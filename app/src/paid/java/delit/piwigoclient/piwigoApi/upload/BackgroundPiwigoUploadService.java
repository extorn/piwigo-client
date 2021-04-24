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
import delit.piwigoclient.database.PiwigoUploadsDatabase;
import delit.piwigoclient.database.PriorUploadRepository;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.upload.action.BackgroundUploadNotificationManager;
import delit.piwigoclient.piwigoApi.upload.actor.BackgroundJobLoadActor;
import delit.piwigoclient.piwigoApi.upload.actors.ActorListener;
import delit.piwigoclient.piwigoApi.upload.actors.JobLoadActor;
import delit.piwigoclient.piwigoApi.upload.actors.PriorUploadsActor;
import delit.piwigoclient.piwigoApi.upload.actors.UploadNotificationManager;
import delit.piwigoclient.piwigoApi.upload.network.NetworkUtils;
import delit.piwigoclient.piwigoApi.upload.power.PowerConnectionReceiver;
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
public class BackgroundPiwigoUploadService extends BasePiwigoUploadService<BackgroundPiwigoUploadService> implements BasePiwigoUploadService.JobUploadListener {

    private static final String TAG =  "PwgCli:BgUpldSvc";
    private static final String ACTION_BACKGROUND_UPLOAD_FILES = "delit.piwigoclient.action.ACTION_BACKGROUND_UPLOAD_FILES";
    private static final String ACTION_WAKE = "delit.piwigoclient.backgroundUpload.action.ACTION_WAKE";
    private static final String ACTION_PAUSE =  "delit.piwigoclient.backgroundUpload.action.ACTION_PAUSE";
    private static final String ACTION_STOP = "delit.piwigoclient.backgroundUpload.action.ACTION_STOP";
    private static final int JOB_ID = 10;

    private static volatile boolean terminateUploadServiceThreadAsap = false;
    private static boolean starting;
    private long pauseThreadUntilSysTime;

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
        return new BackgroundUploadNotificationManager(this);
    }

    public static void sendActionKillService(Context context) {
        Intent intent = new Intent(ACTION_STOP);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    @Override
    protected UploadActionsBroadcastReceiver<BackgroundPiwigoUploadService> buildActionBroadcastReceiver() {
        // adds a few extra commands (pause resume - used for WIFI / non wifi)
        return new BackgroundActionsBroadcastReceiver(this);
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

    public static void sendActionResume(@NonNull Context context) {
        Intent intent = new Intent(ACTION_WAKE);
        intent.setPackage(context.getPackageName());
        context.getApplicationContext().sendBroadcast(intent);
    }

    /**
     * Call from e.g. a notification to pause the upload.
     * @param context an active context.
     */
    public static void sendActionPauseUpload(@NonNull Context context) {
        Intent intent = new Intent(ACTION_PAUSE);
        intent.setPackage(context.getPackageName());
        context.getApplicationContext().sendBroadcast(intent);
    }

    /**
     * This will cancel any upload currently running. Note that the upload job can be resumed
     * at a later point. The reason the job wants cancelling is that it will tidy state and then save it.
     */
    private void cancelAnyRunningUploadJob() {
        UploadJob uploadJob = new BackgroundJobLoadActor(this, new BackgroundJobConfigurationErrorListener(this)).getActiveBackgroundJob();
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

        UploadServiceNetworkListener uploadEventListener = new UploadServiceNetworkListener(context, autoUploadJobsConfig);

        networkManager.registerBroadcastReceiver(context, uploadEventListener);

        PowerConnectionReceiver powerStatusReceiver = new PowerConnectionReceiver(uploadEventListener);
        powerStatusReceiver.registerBroadcastReceiver(context);

        Map<Uri, UriWatcher> runningObservers = new HashMap<>();

        try {

            BackgroundPiwigoFileUploadResponseListener<?,?> jobListener = new BackgroundPiwigoFileUploadResponseListener<>(context);

            while (!terminateUploadServiceThreadAsap) {

                EventBus.getDefault().post(new BackgroundUploadThreadCheckingForTasksEvent());
                if(networkManager.isOkayToUpload()) {
                    pollFoldersForJobsAndUploadAnyMatchingFilesFoundNow(context, autoUploadJobsConfig, runningObservers, uploadEventListener, jobListener);
                }
                sendServiceToSleep();
            }
        } finally {
            // unable to remove from keyset iterator hence the temporary list.
            removeAllContentObservers(runningObservers);
            powerStatusReceiver.unregisterBroadcastReceiver(context);
            networkManager.unregisterBroadcastReceiver(context);
            EventBus.getDefault().post(new BackgroundUploadThreadTerminatedEvent());
        }
    }

    private void pollFoldersForJobsAndUploadAnyMatchingFilesFoundNow(Context context, AutoUploadJobsConfig jobs, Map<Uri, UriWatcher> runningObservers, UploadServiceNetworkListener uploadEventListener, BackgroundPiwigoFileUploadResponseListener<?,?> jobListener) {
        getUploadNotificationManager().updateNotificationText(R.string.notification_text_background_upload_polling, true);
        UploadJob unfinishedJob;
        BackgroundJobLoadActor jobLoadActor = getJobLoadActor();
        do {
            // if there's an old incomplete job, try and finish that first.
            unfinishedJob = jobLoadActor.getActiveBackgroundJob();
            if (unfinishedJob != null) {

                AutoUploadJobConfig jobConfig = new AutoUploadJobConfig(unfinishedJob.getJobConfigId());
                if(!uploadEventListener.isPermitUploadOfJob(jobConfig)) {
                    unfinishedJob = null; // allows to break out of loop and try any remaining job configs
                } else {
                    processUnfinishedJob(jobConfig, unfinishedJob, jobLoadActor);
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
                    if(!uploadEventListener.isPermitUploadOfJob(jobConfig) && !terminateUploadServiceThreadAsap && jobConfig.isJobEnabled(context) && jobConfig.isJobValid(context)) {
                        BackgroundJobLoadActor jobLoaderActor = getJobLoadActor();
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

    private void processUnfinishedJob(AutoUploadJobConfig jobConfig, UploadJob unfinishedJob, JobLoadActor jobLoadActor) {
        ConnectionPreferences.ProfilePreferences jobConnProfilePrefs = unfinishedJob.getConnectionPrefs();
        boolean jobIsValid = jobConnProfilePrefs != null && jobConnProfilePrefs.isValid(getPrefs(), getApplicationContext());
        if (!jobIsValid) {
            jobConfig.setJobValid(this, false);
        }
        if (!unfinishedJob.isStatusFinished() && jobIsValid) {
            if (0 == unfinishedJob.getActionableFilesCount()) {
                // ALL Files not uploaded are in error state. Cancel them all so the server will be cleaned of any partial uploads
                unfinishedJob.cancelAllFailedUploads();
                jobLoadActor.saveStateToDisk(unfinishedJob);
            }

            runJob(jobLoadActor, unfinishedJob, this, true);
            if (unfinishedJob.hasJobCompletedAllActionsSuccessfully()) {
                jobLoadActor.removeJob(unfinishedJob, false);
            }
        } else {
            // no longer valid (connection doesn't exist any longer).
            jobLoadActor.removeJob(unfinishedJob, true);
        }
    }

    @Override
    protected BackgroundJobLoadActor getJobLoadActor() {
        return new BackgroundJobLoadActor(this, new BackgroundJobConfigurationErrorListener(this));
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
    protected void updateListOfPreviouslyUploadedFiles(UploadJob uploadJob, ActorListener actorListener) {
        PriorUploadRepository priorUploadRepository = PriorUploadRepository.getInstance(PiwigoUploadsDatabase.getInstance(getApplication()));
        new PriorUploadsActor(this, uploadJob, actorListener).updatePriorUploadsList(priorUploadRepository);
    }

    @Override
    public void onJobReadyToUpload(Context c, UploadJob thisUploadJob, ActorListener actorListener) {
        PriorUploadRepository priorUploadRepository = PriorUploadRepository.getInstance(PiwigoUploadsDatabase.getInstance(getApplication()));
        new PriorUploadsActor(c, thisUploadJob, actorListener).filterPriorUploadsByUri(priorUploadRepository);
        // technically this is called after the job has already started, but the user doesn't need to know that.
        if(thisUploadJob.getActionableFilesCount() > 0) {
            EventBus.getDefault().post(new BackgroundUploadStartedEvent(thisUploadJob, thisUploadJob.isHasRunBefore()));
        }
    }

    private void wakeIfPaused() {
        synchronized(this) {
            pauseThreadUntilSysTime = System.currentTimeMillis() - 1;
            notifyAll();
        }
    }

    public static class BackgroundActionsBroadcastReceiver extends UploadActionsBroadcastReceiver<BackgroundPiwigoUploadService> {
        public BackgroundActionsBroadcastReceiver(BackgroundPiwigoUploadService service) {
            super(service, ACTION_STOP);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_WAKE.equals(intent.getAction())) {
                getService().wakeIfPaused();
            } else if (ACTION_PAUSE.equals(intent.getAction())) {
                getService().cancelAnyRunningUploadJob();
            }
            super.onReceive(context, intent);
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
