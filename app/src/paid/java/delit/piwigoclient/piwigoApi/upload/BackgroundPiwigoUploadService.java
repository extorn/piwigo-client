package delit.piwigoclient.piwigoApi.upload;

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.ui.events.BackgroundUploadStartedEvent;
import delit.piwigoclient.ui.events.BackgroundUploadStoppedEvent;
import delit.piwigoclient.ui.events.BackgroundUploadThreadCheckingForTasksEvent;
import delit.piwigoclient.ui.events.BackgroundUploadThreadStartedEvent;
import delit.piwigoclient.ui.events.BackgroundUploadThreadTerminatedEvent;
import delit.piwigoclient.ui.preferences.AutoUploadJobConfig;
import delit.piwigoclient.ui.preferences.AutoUploadJobsConfig;
import delit.piwigoclient.util.CustomFileFilter;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 */
public class BackgroundPiwigoUploadService extends BasePiwigoUploadService implements BasePiwigoUploadService.JobUploadListener {

    private static final String TAG = "BkgrdUploadService";
    private static final String ACTION_BACKGROUND_UPLOAD_FILES = "delit.piwigoclient.action.ACTION_BACKGROUND_UPLOAD_FILES";
    private CustomFileFilter fileFilter = new CustomFileFilter();
    private static volatile boolean exit = false;
    private BroadcastEventsListener networkStatusChangeListener;
    private static BackgroundPiwigoUploadService instance;
    private static boolean starting;
    private static UploadJob runningUploadJob = null;
    private long pauseThreadUntilSysTime;

    public BackgroundPiwigoUploadService() {
        super(TAG);
        instance = this;
        starting = false;
    }

    public synchronized static void startService(Context context, boolean keepDeviceAwake) {
        if(starting || instance != null) {
            // don't start if already started or starting.
            return;
        }
        exit = false;
        starting = true;
        Intent intent = new Intent(context, BackgroundPiwigoUploadService.class);
        intent.setAction(ACTION_BACKGROUND_UPLOAD_FILES);
        intent.putExtra(INTENT_ARG_KEEP_DEVICE_AWAKE, keepDeviceAwake);
        ComponentName name = context.startService(intent);
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
            exit = true;
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
    protected void onHandleIntent(Intent intent) {
        // don't call the default (locks device from sleeping)
        doBeforeWork(intent);
        doWork(intent);
    }

    @Override
    protected void doWork(Intent intent) {
        Context context = getApplicationContext();

        EventBus.getDefault().post(new BackgroundUploadThreadStartedEvent());

        registerBroadcastReceiver(context);

        try {

            long pollDurationMillis = 1000 * 60 * 15; // every 15 minutes

            AutoUploadJobsConfig jobs = new AutoUploadJobsConfig(context);
            BackgroundPiwigoFileUploadResponseListener jobListener = new BackgroundPiwigoFileUploadResponseListener(context);
            while (!exit) {

                EventBus.getDefault().post(new BackgroundUploadThreadCheckingForTasksEvent());

                boolean canUpload;
                if (jobs.isUploadOnWirelessOnly(context)) {
                    canUpload = isConnectedToWireless(context);
                } else {
                    canUpload = hasNetworkConnection(context);
                }

                if(canUpload) {
                    UploadJob unfinishedJob;
                    do {
                        // if there's an old incomplete job, try and finish that first.
                        unfinishedJob = getActiveBackgroundJob(context);
                        if (unfinishedJob != null) {
                            if (!unfinishedJob.isFinished() && unfinishedJob.getConnectionPrefs().isValid(getPrefs(), getApplicationContext())) {
                                AutoUploadJobConfig jobConfig = jobs.getAutoUploadJobConfig(unfinishedJob.getJobConfigId(), context);
                                runJob(unfinishedJob, this);
                                if (jobConfig != null) {
                                    runPostJobCleanup(jobConfig, unfinishedJob);
                                }
                            } else {
                                // no longer valid (connection doesn't exist any longer).
                                deleteStateFromDisk(getApplicationContext(), unfinishedJob);
                            }
                        }
                    } while(unfinishedJob != null);

                    if (unfinishedJob == null || !unfinishedJob.isCancelUploadAsap()) {

                        if (jobs.hasUploadJobs(context)) {
                            List<AutoUploadJobConfig> jobConfigList = jobs.getAutoUploadJobs(context);
                            PowerManager.WakeLock wl = getWakeLock(intent);
                            try {
                                for (AutoUploadJobConfig jobConfig : jobConfigList) {

                                    if (jobConfig.isJobEnabled(context) && jobConfig.isJobValid(context)) {
                                        UploadJob uploadJob = getUploadJob(context, jobConfig, jobListener);
                                        if (uploadJob != null) {
                                            if (runJobWithCleanup(uploadJob, jobConfig, context)) {
                                                // stop running jobs if we had a cancel request.
                                                break;
                                            }
                                        }

                                    }
                                }
                            } finally {
                                releaseWakeLock(wl);
                            }
                        }
                    }

                }
                synchronized (this) {
                    pauseThreadUntilSysTime = System.currentTimeMillis() + pollDurationMillis;
                    while (System.currentTimeMillis() < pauseThreadUntilSysTime && !exit) {
                        try {
                            wait(pollDurationMillis);
                        } catch (InterruptedException e) {
                            Log.d(TAG, "Ignoring interrupt");
                        }
                    }
                }
            }
        } finally {
            unregisterBroadcastReceiver(context);
            EventBus.getDefault().post(new BackgroundUploadThreadTerminatedEvent());
        }
    }

    /**
     * @param uploadJob
     * @param jobConfig
     * @param context
     * @return true if cancelled early due to termination request.
     */
    private boolean runJobWithCleanup(UploadJob uploadJob, AutoUploadJobConfig jobConfig, Context context) {
        runJob(uploadJob, this);
        runPostJobCleanup(jobConfig, uploadJob);
        if(!uploadJob.isCancelUploadAsap()) {
            BasePiwigoUploadService.deleteStateFromDisk(context, uploadJob);
            BasePiwigoUploadService.removeJob(uploadJob);
            return false;
        }
        return true;
    }

    @Override
    protected void runJob(UploadJob thisUploadJob, JobUploadListener listener) {
        try {
            EventBus.getDefault().post(new BackgroundUploadStartedEvent(thisUploadJob));
            super.runJob(thisUploadJob, listener);
        } finally {
            EventBus.getDefault().post(new BackgroundUploadStoppedEvent(thisUploadJob));
        }
    }

    private void runPostJobCleanup(AutoUploadJobConfig jobConfig, UploadJob uploadJob) {
        if(jobConfig.isDeleteFilesAfterUpload(getApplicationContext())) {
            for (File f : uploadJob.getFilesSuccessfullyUploaded()) {
                if (f.exists()) {
                    if(!f.delete()) {
                        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            try {
                                Files.delete(f.toPath());
                            } catch (IOException e) {
                                if(BuildConfig.DEBUG) {
                                    Log.e(TAG, "Unable to delete uploaded file", e);
                                }
                            }
                        }
                    }
                }
            }
        }
        // record all files uploaded to prevent repeated upload (do this always in case delete fails for a file!
        HashSet<File> filesUploaded = uploadJob.getFilesSuccessfullyUploaded();
        HashMap<File, String> uploadedFileChecksums = new HashMap<>(filesUploaded.size());
        for(File f : filesUploaded) {
            if(f.exists()) {
                uploadedFileChecksums.put(f, uploadJob.getFileChecksum(f));
            }
        }
        jobConfig.saveFilesPreviouslyUploaded(getApplicationContext(), new AutoUploadJobConfig.PriorUploads(jobConfig.getJobId(), uploadedFileChecksums));
    }

    private void unregisterBroadcastReceiver(Context context) {
        if(networkStatusChangeListener != null) {
            context.unregisterReceiver(networkStatusChangeListener);
        }
    }

    private void registerBroadcastReceiver(Context context) {

        networkStatusChangeListener = new BroadcastEventsListener(this);
        context.registerReceiver(networkStatusChangeListener, networkStatusChangeListener.getIntentFilter());
    }

    @Override
    public void onJobReadyToUpload(Context c, UploadJob thisUploadJob) {
        thisUploadJob.getJobId();
        AutoUploadJobConfig jobConfig = new AutoUploadJobConfig(c, thisUploadJob.getJobConfigId());
        AutoUploadJobConfig.PriorUploads priorUploads = jobConfig.getFilesPreviouslyUploaded(c);
        thisUploadJob.filterPreviouslyUploadedFiles(priorUploads.getFilesToHashMap());
    }

    private static class BroadcastEventsListener extends BroadcastReceiver {

        private BackgroundPiwigoUploadService service;

        public BroadcastEventsListener(BackgroundPiwigoUploadService service) {
            this.service = service;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals("android.intent.action.USER_PRESENT")) {
                handleDeviceUnlocked(context);
            } else if(intent.getAction().equals("android.net.conn.CONNECTIVITY_CHANGE")) {
                handleNetworkStatusChanged(context);
            }
        }

        private void handleDeviceUnlocked(Context context) {
            wakeServiceIfSleeping();
        }

        private void handleNetworkStatusChanged(Context context) {
            final ConnectivityManager connMgr = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);

            android.net.NetworkInfo network = connMgr
                    .getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if(BuildConfig.DEBUG) {
                // just allow testing in the emulator.
                network = connMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
            }

            if (network.isAvailable() && network.isConnected()) {
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
        File[] matchingFiles = f.listFiles(fileFilter.withFileExtIn(jobConfig.getFileExtsToUpload(context)).withMaxSizeMb(jobConfig.getMaxUploadSize(context)));
        if(matchingFiles.length == 0) {
            return null;
        }
        ArrayList<File> filesToUpload = new ArrayList(Arrays.asList(matchingFiles));
        //public UploadJob(ConnectionPreferences.ProfilePreferences connectionPrefs, long jobId, long responseHandlerId, ArrayList<File> filesForUpload, CategoryItemStub destinationCategory, int uploadedFilePrivacyLevel, boolean useTempFolder) {
        CategoryItemStub category = jobConfig.getUploadToAlbum(context);
        UploadJob uploadJob = createUploadJob(jobConfig.getConnectionPrefs(context), filesToUpload, category,
                jobConfig.getUploadedFilePrivacyLevel(context), jobListener.getHandlerId());
        uploadJob.setToRunInBackground();
        uploadJob.setJobConfigId(jobConfig.getJobId());
        return uploadJob;
    }

    @Override
    protected void postNewResponse(long jobId, PiwigoResponseBufferingHandler.Response response) {
        //TODO do something useful with the messages... build a log file?

//        PiwigoResponseBufferingHandler.getDefault().preRegisterResponseHandlerForNewMessage(jobId, response.getMessageId());
//        PiwigoResponseBufferingHandler.getDefault().processResponse(response);
    }



}
