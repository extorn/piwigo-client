package delit.piwigoclient.piwigoApi.upload;

import android.app.ActivityManager;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import delit.libs.core.util.Logging;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.upload.actor.BackgroundJobLoadActor;
import delit.piwigoclient.piwigoApi.upload.actors.ActorListener;
import delit.piwigoclient.piwigoApi.upload.actors.JobLoadActor;
import delit.piwigoclient.piwigoApi.upload.actors.UploadNotificationManager;
import delit.piwigoclient.piwigoApi.upload.network.NetworkUtils;
import delit.piwigoclient.ui.events.BackgroundUploadStartedEvent;
import delit.piwigoclient.ui.events.BackgroundUploadStoppedEvent;
import delit.piwigoclient.ui.events.BackgroundUploadThreadCheckingForTasksEvent;
import delit.piwigoclient.ui.events.BackgroundUploadThreadStartedEvent;
import delit.piwigoclient.ui.events.BackgroundUploadThreadTerminatedEvent;
import delit.piwigoclient.ui.preferences.AutoUploadJobConfig;
import delit.piwigoclient.ui.preferences.AutoUploadJobsConfig;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 */
public class BackgroundPiwigoUploadService extends BasePiwigoUploadService implements BasePiwigoUploadService.JobUploadListener {

    private static final String TAG =  "PwgCli:BgUpldSvc";
    private static final String ACTION_BACKGROUND_UPLOAD_FILES = "delit.piwigoclient.action.ACTION_BACKGROUND_UPLOAD_FILES";
    private static final String ACTION_WAKE = "delit.piwigoclient.backgroundUpload.action.ACTION_WAKE";
    private static final String ACTION_PAUSE =  "delit.piwigoclient.backgroundUpload.action.ACTION_PAUSE";
    private static final String ACTION_STOP = "delit.piwigoclient.backgroundUpload.action.ACTION_STOP";
    private static final int JOB_ID = 10;

    private static volatile boolean terminateUploadServiceThreadAsap = false;
    private static boolean starting;
    private long pauseThreadUntilSysTime;
    private static final int BACKGROUND_UPLOAD_NOTIFICATION_ID = 2;

    public BackgroundPiwigoUploadService() {
        super();
        starting = false;
    }

    private static boolean isMyServiceRunning(Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : Objects.requireNonNull(manager).getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void actionKillService() {
        terminateUploadServiceThreadAsap = true;
        super.actionKillService();
        wakeIfPaused();
    }

    @Override
    protected UploadNotificationManager buildUploadNotificationManager() {
        return new UploadNotificationManager(this) {
            @Override
            public int getNotificationId() {
                return BACKGROUND_UPLOAD_NOTIFICATION_ID;
            }

            @Override
            protected String getNotificationTitle() {
                return getString(R.string.notification_title_background_upload_service);
            }
        };
    }

    public static void sendActionKillService(Context context) {
        Intent intent = new Intent();
        intent.setAction(ACTION_STOP);
        context.sendBroadcast(intent);
    }

    @Override
    protected ActionsBroadcastReceiver buildActionBroadcastReceiver() {
        // adds a few extra commands (pause resume - used for WIFI / non wifi)
        return new BackgroundActionsBroadcastReceiver();
    }

    @Override
    protected ActorListener buildUploadActorListener(UploadJob uploadJob, UploadNotificationManager notificationManager) {
        return new ActorListener(uploadJob, notificationManager) {
            @Override
            public void postNewResponse(long jobId, PiwigoResponseBufferingHandler.Response response) {
                //TODO do something useful with the messages... build a log file?

//                  PiwigoResponseBufferingHandler.getDefault().preRegisterResponseHandlerForNewMessage(jobId, response.getMessageId());
//                  PiwigoResponseBufferingHandler.getDefault().processResponse(response);
            }

            @Override
            public void updateNotificationProgressText(int uploadProgress) {
                getUploadNotificationManager().updateNotificationText(R.string.notification_text_background_upload_running, uploadProgress);
            }
        };
    }

    public synchronized static void startService(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        if(starting || isStarted(appContext)) {
            return;
        }
        terminateUploadServiceThreadAsap = false;
        starting = true;
        Intent intent = new Intent(appContext, BackgroundPiwigoUploadService.class);
        intent.setAction(ACTION_BACKGROUND_UPLOAD_FILES);
        enqueueWork(appContext, BackgroundPiwigoUploadService.class, JOB_ID, intent);
    }

    public static void resumeUploadService(@NonNull Context context) {
        Intent intent = new Intent();
        intent.setAction(ACTION_WAKE);
        context.getApplicationContext().sendBroadcast(intent);
    }

    /**
     * Call from e.g. a notification to pause the upload.
     * @param context an active context.
     */
    public static void pauseUploadService(@NonNull Context context) {
        Intent intent = new Intent();
        intent.setAction(ACTION_PAUSE);
        context.getApplicationContext().sendBroadcast(intent);
    }

    /**
     * This will cancel any upload currently running. Note that the upload job can be resumed
     * at a later point. The reason the job wants cancelling is that it will tidy state and then save it.
     */
    private void cancelAnyRunningUploadJob() {
        UploadJob uploadJob = BackgroundJobLoadActor.getActiveBackgroundJob(this);
        if(uploadJob != null && !uploadJob.isStatusFinished()) {
            uploadJob.cancelUploadAsap();
        }
    }

    public static boolean isStarted(@NonNull Context context) {
        return isMyServiceRunning(context.getApplicationContext(), BackgroundPiwigoUploadService.class);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        // don't call the default (locks device from sleeping)
        try {
            doBeforeWork(intent);
            doWork(intent);
        } catch(RuntimeException e) {
            Bundle b = new Bundle();
            b.putSerializable("error", e);
            b.putString("service", TAG);
            Logging.logAnalyticEvent(this,"UploadServiceCrash", b);
            Logging.recordException(e);
        } finally {
            stopForeground(true);
        }
    }

    @Override
    protected void doWork(@NonNull Intent intent) {

        Context context = getApplicationContext();

        NetworkUtils networkManager = new NetworkUtils();

        EventBus.getDefault().post(new BackgroundUploadThreadStartedEvent());

        AutoUploadJobsConfig autoUploadJobsConfig = new AutoUploadJobsConfig(context);

        networkManager.registerBroadcastReceiver(context, new UploadServiceNetworkListener(context, autoUploadJobsConfig));

        Map<Uri, UriWatcher> runningObservers = new HashMap<>();

        try {

            BackgroundPiwigoFileUploadResponseListener jobListener = new BackgroundPiwigoFileUploadResponseListener(context);

            while (!terminateUploadServiceThreadAsap) {

                EventBus.getDefault().post(new BackgroundUploadThreadCheckingForTasksEvent());
                boolean canUpload = networkManager.isOkayToUpload();
                if(canUpload) {
                    pollFoldersForJobsAndUploadAnyMatchingFilesFoundNow(context, autoUploadJobsConfig, runningObservers, jobListener);
                }
                sendServiceToSleep();
            }
        } finally {
            // unable to remove from keyset iterator hence the temporary list.
            removeAllContentObservers(runningObservers);

            networkManager.unregisterBroadcastReceiver(context);
            EventBus.getDefault().post(new BackgroundUploadThreadTerminatedEvent());
        }
    }

    private void pollFoldersForJobsAndUploadAnyMatchingFilesFoundNow(Context context, AutoUploadJobsConfig jobs, Map<Uri, UriWatcher> runningObservers, BackgroundPiwigoFileUploadResponseListener jobListener) {
        getUploadNotificationManager().updateNotificationText(R.string.notification_text_background_upload_polling, true);
        UploadJob unfinishedJob;

        do {
            // if there's an old incomplete job, try and finish that first.
            unfinishedJob = BackgroundJobLoadActor.getActiveBackgroundJob(context);
            if (unfinishedJob != null) {
                ConnectionPreferences.ProfilePreferences jobConnProfilePrefs = unfinishedJob.getConnectionPrefs();
                boolean jobIsValid = jobConnProfilePrefs != null && jobConnProfilePrefs.isValid(getPrefs(), getApplicationContext());
                if(!jobIsValid) {
                    new AutoUploadJobConfig(unfinishedJob.getJobConfigId()).setJobValid(this, false);
                }
                if (!unfinishedJob.isStatusFinished() && jobIsValid) {
                    if(!unfinishedJob.hasProcessableFiles()) {
                        // ALL Files are in error state. Cancel them all so the server will be cleaned of any partial uploads
                        unfinishedJob.cancelAllFailedUploads();
                        new JobLoadActor(this).saveStateToDisk(unfinishedJob);
                    }
                    BackgroundJobLoadActor jobLoadActor = new BackgroundJobLoadActor(this, new BackgroundJobConfigurationErrorListener(this));
                    runJob(jobLoadActor, unfinishedJob, this, true);
                    if (unfinishedJob.hasJobCompletedAllActionsSuccessfully()) {
                        JobLoadActor.removeJob(unfinishedJob);
                    }
                } else {
                    // no longer valid (connection doesn't exist any longer).
                    new JobLoadActor(this).deleteStateFromDisk(unfinishedJob, true);
                    JobLoadActor.removeJob(unfinishedJob);
                }
            }
        } while (unfinishedJob != null && !terminateUploadServiceThreadAsap);

        if (!terminateUploadServiceThreadAsap && (unfinishedJob == null || !unfinishedJob.isCancelUploadAsap())) {

            if (jobs.hasUploadJobs(context)) {
                // remove all existing watchers (in case user has altered the monitored folders)
                List<AutoUploadJobConfig> jobConfigList = jobs.getAutoUploadJobs(context);
                removeAllContentObservers(runningObservers);
                for (AutoUploadJobConfig jobConfig : jobConfigList) {
                    DocumentFile monitoringFolder = jobConfig.getLocalFolderToMonitor(context);

                    if (monitoringFolder != null && !runningObservers.containsKey(monitoringFolder.getUri())) {
                        // TODO get rid of the event processor thread and use the handler instead to minimise potential thread generation!
                        UriWatcher observer;
                        Uri monitoredUri  = monitoringFolder.getUri();
                        if("file".equals(monitoredUri.getScheme())) {
                            observer = new BackgroundUploadInvokingFolderChangeObserver(this, new File(Objects.requireNonNull(monitoredUri.getPath())));
                        } else {
                            observer = new BackgroundUploadInvokingUriContentObserver(null, this, monitoringFolder.getUri());
                        }

                        runningObservers.put(observer.getWatchedUri(), observer);
                        observer.startWatching();
                    }
                    if (!terminateUploadServiceThreadAsap && jobConfig.isJobEnabled(context) && jobConfig.isJobValid(context)) {
                        BackgroundJobLoadActor jobLoaderActor = new BackgroundJobLoadActor(this, new BackgroundJobConfigurationErrorListener(this));
                        UploadJob uploadJob = jobLoaderActor.getUploadJob(jobConfig, jobListener);
                        if (uploadJob != null) {
                            runJob(jobLoaderActor, uploadJob, this, false);
                            if (!uploadJob.isCancelUploadAsap()) {
                                // stop running jobs if we had a cancel request.
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private void sendServiceToSleep() {
        long pollDurationMillis = 1000 * 60 * 60 * 3; // check once every 3 hours at latest (poll file system for changes though).
        synchronized (this) {
            pauseThreadUntilSysTime = System.currentTimeMillis() + pollDurationMillis;
            Date wakeAt = new Date(pauseThreadUntilSysTime);
            while (System.currentTimeMillis() < pauseThreadUntilSysTime && !terminateUploadServiceThreadAsap) {
                try {
                    getUploadNotificationManager().updateNotificationText(getString(R.string.notification_text_background_upload_sleeping, wakeAt), false);
                    wait(pollDurationMillis);
                } catch (InterruptedException e) {
                    Log.d(TAG, "Ignoring interrupt");
                }
            }
        }
    }

    private void removeAllContentObservers(Map<Uri, UriWatcher> runningObservers) {
        List<Uri> keys = new ArrayList<>(runningObservers.keySet());
        for(Uri key : keys) {
            UriWatcher observer = runningObservers.remove(key);
            if (observer != null) {
                observer.stopWatching();
            }
        }
    }

    @Override
    protected void runJob(@NonNull JobLoadActor jobLoaderActor, @NonNull UploadJob thisUploadJob, JobUploadListener listener, boolean deleteJobConfigFileOnSuccess) {
        try {
            getUploadNotificationManager().updateNotificationText(R.string.notification_text_background_upload_running, thisUploadJob.getOverallUploadProgressInt());
            boolean connectionDetailsValid = thisUploadJob.getConnectionPrefs().isValid(this);
            if(connectionDetailsValid) {
                super.runJob(jobLoaderActor, thisUploadJob, listener, deleteJobConfigFileOnSuccess);
            } else {
                // update the job validity status
                AutoUploadJobConfig config = new AutoUploadJobConfig(thisUploadJob.getJobConfigId());
                config.setJobValid(this, false);
            }
        } finally {
            EventBus.getDefault().post(new BackgroundUploadStoppedEvent(thisUploadJob));
        }
    }

    @Override
    protected void updateListOfPreviouslyUploadedFiles(UploadJob uploadJob, HashMap<Uri, String> uploadedFileChecksums) {
        AutoUploadJobConfig jobConfig = new AutoUploadJobConfig(uploadJob.getJobConfigId());
        AutoUploadJobConfig.PriorUploads priorUploads = jobConfig.getFilesPreviouslyUploaded(getApplicationContext());
        priorUploads.putAll(uploadedFileChecksums);
        jobConfig.saveFilesPreviouslyUploaded(getApplicationContext(), priorUploads);
    }

    @Override
    public void onJobReadyToUpload(Context c, UploadJob thisUploadJob) {
        AutoUploadJobConfig jobConfig = new AutoUploadJobConfig(thisUploadJob.getJobConfigId());
        //FIXME this isn't scalable. Needs storing in a database and querying on a file by file basis as needed
        AutoUploadJobConfig.PriorUploads priorUploads = jobConfig.getFilesPreviouslyUploaded(c);
        thisUploadJob.filterPreviouslyUploadedFiles(priorUploads.getFileUrisAndHashcodes());
        // technically this is called after the job has already started, but the user doesn't need to know that.
        if(thisUploadJob.hasProcessableFiles()) {
            EventBus.getDefault().post(new BackgroundUploadStartedEvent(thisUploadJob, thisUploadJob.isHasRunBefore()));
        }
    }

    private void wakeIfPaused() {
        synchronized(this) {
            pauseThreadUntilSysTime = System.currentTimeMillis() - 1;
            notifyAll();
        }
    }

    private class BackgroundActionsBroadcastReceiver extends ActionsBroadcastReceiver {
        public BackgroundActionsBroadcastReceiver() {
            super(ACTION_STOP);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_WAKE.equals(intent.getAction())) {
                wakeIfPaused();
            } else if (ACTION_PAUSE.equals(intent.getAction())) {
                cancelAnyRunningUploadJob();
            } else {
                super.onReceive(context, intent);
            }
        }

        @Override
        public IntentFilter getFilter() {
            IntentFilter filter = super.getFilter();
            filter.addAction(ACTION_WAKE);
            filter.addAction(ACTION_PAUSE);
            return filter;
        }
    }

}
