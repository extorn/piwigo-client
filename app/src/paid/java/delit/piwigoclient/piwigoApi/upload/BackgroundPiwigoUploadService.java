package delit.piwigoclient.piwigoApi.upload;

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.FileObserver;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import delit.libs.util.CustomFileFilter;
import delit.libs.util.IOUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
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
    private static final int JOB_ID = 10;
    private CustomFileFilter fileFilter = new CustomFileFilter();
    private static volatile boolean terminateUploadServiceThreadAsap = false;
    private BroadcastEventsListener networkStatusChangeListener;
    private static BackgroundPiwigoUploadService instance;
    private static boolean starting;
    private static UploadJob runningUploadJob = null;
    private long pauseThreadUntilSysTime;
    private static final int BACKGROUND_UPLOAD_NOTIFICATION_ID = 2;

    public BackgroundPiwigoUploadService() {
        super(TAG);
        instance = this;
        starting = false;
    }

    public synchronized static void startService(Context context) {
        if(starting || instance != null) {
            // don't start if already started or starting.
            return;
        }
        terminateUploadServiceThreadAsap = false;
        starting = true;
        Intent intent = new Intent(context, BackgroundPiwigoUploadService.class);
        intent.setAction(ACTION_BACKGROUND_UPLOAD_FILES);
        enqueueWork(context, BackgroundPiwigoUploadService.class, JOB_ID, intent);
    }

    public static boolean isStarted() {
        return instance != null;
    }

    public synchronized static void wakeServiceIfSleeping() {
        if(instance != null) {
            instance.wakeIfWaiting();
        }
    }

    public synchronized static void killService() {
        if(instance != null) {
            terminateUploadServiceThreadAsap = true;
            if(runningUploadJob != null) {
                runningUploadJob.cancelUploadAsap();
            }
            instance.wakeIfWaiting();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    @Override
    protected int getNotificationId() {
        return BACKGROUND_UPLOAD_NOTIFICATION_ID;
    }

    @Override
    protected String getNotificationTitle() {
        return getString(R.string.notification_title_background_upload_service);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        // don't call the default (locks device from sleeping)
        try {
            doBeforeWork(intent);
            doWork(intent);
        } finally {
            stopForeground(true);
        }
    }

    @Override
    protected void updateNotificationProgressText(int uploadProgress) {
        updateNotificationText(getString(R.string.notification_text_background_upload_running), uploadProgress);
    }

    @Override
    protected void doWork(Intent intent) {
        Context context = getApplicationContext();

        EventBus.getDefault().post(new BackgroundUploadThreadStartedEvent());

        AutoUploadJobsConfig jobs = new AutoUploadJobsConfig(context);

        registerBroadcastReceiver(jobs, context);

        Map<File, FileObserver> runningObservers = new HashMap<>();

        try {

            long pollDurationMillis = 1000 * 60 * 60 * 3; // check once every 3 hours at latest (poll file system for changes though).

            BackgroundPiwigoFileUploadResponseListener jobListener = new BackgroundPiwigoFileUploadResponseListener(context);


            while (!terminateUploadServiceThreadAsap) {

                EventBus.getDefault().post(new BackgroundUploadThreadCheckingForTasksEvent());

                boolean canUpload;
                if (jobs.isUploadOnWirelessOnly(context)) {
                    canUpload = isConnectedToWireless(context);
                } else {
                    canUpload = hasNetworkConnection(context);
                }

                if(canUpload) {
                    updateNotificationText(getString(R.string.notification_text_background_upload_polling), false);
                    UploadJob unfinishedJob;
                    do {
                        // if there's an old incomplete job, try and finish that first.
                        unfinishedJob = getActiveBackgroundJob(context);
                        if (unfinishedJob != null) {
                            ConnectionPreferences.ProfilePreferences jobConnProfilePrefs = unfinishedJob.getConnectionPrefs();
                            boolean jobIsValid = jobConnProfilePrefs != null && jobConnProfilePrefs.isValid(getPrefs(), getApplicationContext());
                            if(!jobIsValid) {
                                new AutoUploadJobConfig(unfinishedJob.getJobConfigId()).setJobValid(this, false);
                            }
                            if (!unfinishedJob.isFinished() && jobIsValid) {
                                AutoUploadJobConfig jobConfig = jobs.getAutoUploadJobConfig(unfinishedJob.getJobConfigId(), context);
                                runJob(unfinishedJob, this, true);
                                if (jobConfig != null) {
                                    runPostJobCleanup(unfinishedJob, jobConfig.isDeleteFilesAfterUpload(context));
                                }
                                if (unfinishedJob.hasJobCompletedAllActionsSuccessfully()) {
                                    removeJob(unfinishedJob);
                                }
                            } else {
                                // no longer valid (connection doesn't exist any longer).
                                deleteStateFromDisk(getApplicationContext(), unfinishedJob, true);
                                removeJob(unfinishedJob);
                            }
                        }
                    } while (unfinishedJob != null && !terminateUploadServiceThreadAsap);

                    if (!terminateUploadServiceThreadAsap && (unfinishedJob == null || !unfinishedJob.isCancelUploadAsap())) {

                        if (jobs.hasUploadJobs(context)) {
                            // remove all existing watchers (in case user has altered the monitored folders)
                            List<AutoUploadJobConfig> jobConfigList = jobs.getAutoUploadJobs(context);
                            List<File> keys = new ArrayList<>(runningObservers.keySet());
                            for(File key : keys) {
                                FileObserver observer = runningObservers.remove(key);
                                if (observer != null) {
                                    observer.stopWatching();
                                }
                            }
                            for (AutoUploadJobConfig jobConfig : jobConfigList) {
                                File monitoringFolder = jobConfig.getLocalFolderToMonitor(context);
                                if (monitoringFolder != null && !runningObservers.containsKey(monitoringFolder)) {
                                    FileObserver monitorProcess = new CustomFileObserver(this, monitoringFolder);
                                    monitorProcess.startWatching();
                                    runningObservers.put(monitoringFolder, monitorProcess);
                                }
                                if (!terminateUploadServiceThreadAsap && jobConfig.isJobEnabled(context) && jobConfig.isJobValid(context)) {
                                    UploadJob uploadJob = getUploadJob(context, jobConfig, jobListener);
                                    if (uploadJob != null) {
                                        runJob(uploadJob, this, false);
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
                synchronized (this) {
                    pauseThreadUntilSysTime = System.currentTimeMillis() + pollDurationMillis;
                    Date wakeAt = new Date(pauseThreadUntilSysTime);
                    while (System.currentTimeMillis() < pauseThreadUntilSysTime && !terminateUploadServiceThreadAsap) {
                        try {
                            updateNotificationText(getString(R.string.notification_text_background_upload_sleeping, wakeAt), false);
                            wait(pollDurationMillis);
                        } catch (InterruptedException e) {
                            Log.d(TAG, "Ignoring interrupt");
                        }
                    }
                }
            }
        } finally {
            // unable to remove from keyset iterator hence the temporary list.
            List<File> observersToRemove = new ArrayList<>(runningObservers.keySet());
            for(File key : observersToRemove) {
                FileObserver observer = runningObservers.remove(key);
                if (observer != null) {
                    observer.stopWatching();
                }
            }
            observersToRemove.clear();

            unregisterBroadcastReceiver(context);
            EventBus.getDefault().post(new BackgroundUploadThreadTerminatedEvent());
        }
    }

    @Override
    protected void runJob(UploadJob thisUploadJob, JobUploadListener listener, boolean deleteJobConfigFileOnSuccess) {
        try {
            synchronized (BackgroundPiwigoUploadService.class) {
                runningUploadJob = thisUploadJob;
            }
            updateNotificationText(getString(R.string.notification_text_background_upload_running), runningUploadJob.getUploadProgress());
            boolean connectionDetailsValid = thisUploadJob.getConnectionPrefs().isValid(this);
            if(connectionDetailsValid) {
                super.runJob(thisUploadJob, listener, deleteJobConfigFileOnSuccess);
            } else {
                // update the job validity status
                AutoUploadJobConfig config = new AutoUploadJobConfig(thisUploadJob.getJobConfigId());
                config.setJobValid(this, false);
            }
        } finally {
            synchronized (BackgroundPiwigoUploadService.class) {
                runningUploadJob = null;
            }
            EventBus.getDefault().post(new BackgroundUploadStoppedEvent(thisUploadJob));
        }
    }

    @Override
    protected void runPostJobCleanup(UploadJob uploadJob, boolean deleteUploadedFiles) {
        AutoUploadJobConfig jobConfig = new AutoUploadJobConfig(uploadJob.getJobConfigId());
        super.runPostJobCleanup(uploadJob, jobConfig.isDeleteFilesAfterUpload(getApplicationContext()));
    }

    @Override
    protected void updateListOfPreviouslyUploadedFiles(UploadJob uploadJob, HashMap<File, String> uploadedFileChecksums) {
        AutoUploadJobConfig jobConfig = new AutoUploadJobConfig(uploadJob.getJobConfigId());
        AutoUploadJobConfig.PriorUploads priorUploads = jobConfig.getFilesPreviouslyUploaded(getApplicationContext());
        priorUploads.getFilesToHashMap().putAll(uploadedFileChecksums);
        jobConfig.saveFilesPreviouslyUploaded(getApplicationContext(), priorUploads);
    }

    private void unregisterBroadcastReceiver(Context context) {
        if(networkStatusChangeListener != null) {
            context.unregisterReceiver(networkStatusChangeListener);
        }
    }

    private void registerBroadcastReceiver(AutoUploadJobsConfig jobs, Context context) {

        networkStatusChangeListener = new BroadcastEventsListener(jobs, this);
        context.registerReceiver(networkStatusChangeListener, networkStatusChangeListener.getIntentFilter());
    }

    @Override
    public void onJobReadyToUpload(Context c, UploadJob thisUploadJob) {
        AutoUploadJobConfig jobConfig = new AutoUploadJobConfig(thisUploadJob.getJobConfigId());
        AutoUploadJobConfig.PriorUploads priorUploads = jobConfig.getFilesPreviouslyUploaded(c);
        thisUploadJob.filterPreviouslyUploadedFiles(priorUploads.getFilesToHashMap());
        // technically this is called after the job has already started, but the user doesn't need to know that.
        if(thisUploadJob.getFilesForUpload().size() > 0) {
            EventBus.getDefault().post(new BackgroundUploadStartedEvent(thisUploadJob, thisUploadJob.hasBeenRunBefore()));
        }
    }

    private static class BroadcastEventsListener extends BroadcastReceiver {

        private final AutoUploadJobsConfig jobs;
        private BackgroundPiwigoUploadService service;

        public BroadcastEventsListener(AutoUploadJobsConfig jobs, BackgroundPiwigoUploadService service) {
            this.service = service;
            this.jobs = jobs;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if("android.intent.action.USER_PRESENT".equals(intent.getAction())) {
                handleDeviceUnlocked(context);
            } else if("android.net.conn.CONNECTIVITY_CHANGE".equals(intent.getAction())) {
                handleNetworkStatusChanged(context);
            }
        }

        private void handleDeviceUnlocked(Context context) {
            wakeServiceIfSleeping();
        }

        private void handleNetworkStatusChanged(Context context) {
            final ConnectivityManager connMgr = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);

            android.net.NetworkInfo network = null;
            if(connMgr != null) {
                if (jobs.isUploadOnWirelessOnly(context)) {
                    network = connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                    if (BuildConfig.DEBUG) {
                        // just allow testing in the emulator.
                        network = connMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
                    }
                } else {
                    network = connMgr.getActiveNetworkInfo();
                }
            }

            if (network != null && network.isAvailable() && network.isConnected()) {
                // wake immediately.
                service.wakeIfWaiting();
            } else {
                service.pauseAnyRunningUpload();
            }
        }

        public IntentFilter getIntentFilter() {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
            intentFilter.addAction("android.intent.action.USER_PRESENT");
            return intentFilter;
        }
    }

    private void wakeIfWaiting() {
        synchronized(this) {
            pauseThreadUntilSysTime = System.currentTimeMillis();
            notifyAll();
        }
    }

    private void pauseAnyRunningUpload() {
        UploadJob uploadJob = getActiveBackgroundJob(getApplicationContext());
        if(uploadJob != null && !uploadJob.isFinished()) {
            uploadJob.cancelUploadAsap();
        }
    }

    private boolean hasNetworkConnection(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();
        return isConnected;
    }

    private boolean isConnectedToWireless(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isWiFi = activeNetwork != null && activeNetwork.isConnected() && activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;
//        if(BuildConfig.DEBUG) {
//            // just allow testing in the emulator.
//            isWiFi = activeNetwork != null && activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE;
//        }
        return isWiFi;
    }

    private UploadJob getUploadJob(Context context, AutoUploadJobConfig jobConfig, BackgroundPiwigoFileUploadResponseListener jobListener) {
        File f = jobConfig.getLocalFolderToMonitor(context);
        if(!f.exists()) {
            postNewResponse(jobConfig.getJobId(), new PiwigoResponseBufferingHandler.CustomErrorResponse(jobConfig.getJobId(),"Local folder no longer exists. Ignoring job"));
            return null;
        }
        boolean compressVideos = jobConfig.isCompressVideosBeforeUpload(context);
        File[] matchingFiles = f.listFiles(fileFilter.withFileExtIn(jobConfig.getFileExtsToUpload(context)).withMaxSizeMb(jobConfig.getMaxUploadSize(context), compressVideos));
        if (matchingFiles.length == 0) {
            return null;
        }
        matchingFiles = IOUtils.getFilesNotBeingWritten(matchingFiles, 1000);
        if(matchingFiles.length == 0) {
            return null;
        }
        ArrayList<File> filesToUpload = new ArrayList<>(Arrays.asList(matchingFiles));

        CategoryItemStub category = jobConfig.getUploadToAlbum(context);
        UploadJob uploadJob = createUploadJob(jobConfig.getConnectionPrefs(context, getPrefs()), filesToUpload, category,
                jobConfig.getUploadedFilePrivacyLevel(context), jobListener.getHandlerId());
        uploadJob.setToRunInBackground();
        uploadJob.setJobConfigId(jobConfig.getJobId());

        uploadJob.setVideoCompressionParams(jobConfig.getVideoCompressionParams(context));
        uploadJob.setImageCompressionParams(jobConfig.getImageCompressionParams(context));
        return uploadJob;
    }

    @Override
    protected void postNewResponse(long jobId, PiwigoResponseBufferingHandler.Response response) {
        //TODO do something useful with the messages... build a log file?

//        PiwigoResponseBufferingHandler.getDefault().preRegisterResponseHandlerForNewMessage(jobId, response.getMessageId());
//        PiwigoResponseBufferingHandler.getDefault().processResponse(response);
    }


    private class CustomFileObserver extends FileObserver {
        private final BackgroundPiwigoUploadService uploadService;

        CustomFileObserver(BackgroundPiwigoUploadService uploadService, File f) {
            super(f.getAbsolutePath(), FileObserver.CREATE ^ FileObserver.MOVED_TO);
            this.uploadService = uploadService;
        }

        @Override
        public void onEvent(int event, @Nullable String path) {
            uploadService.wakeIfWaiting();
        }
    }
}
