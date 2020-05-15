package delit.piwigoclient.piwigoApi.upload;

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.MimeTypeFilter;
import androidx.documentfile.provider.DocumentFile;

import com.crashlytics.android.Crashlytics;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.greenrobot.eventbus.EventBus;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import delit.piwigoclient.ui.file.SimpleDocumentFileFilter;
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

        Map<Uri, CustomContentObserver> runningObservers = new HashMap<>();

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
                            removeAllContentObservers(runningObservers);
                            for (AutoUploadJobConfig jobConfig : jobConfigList) {
                                DocumentFile monitoringFolder = jobConfig.getLocalFolderToMonitor(context);

                                if (monitoringFolder != null && !runningObservers.containsKey(monitoringFolder)) {
                                    // TODO get rid of the event processor thread and use the handler instead to minimise potential thread generation!
                                    CustomContentObserver observer = new CustomContentObserver(null, this, monitoringFolder.getUri());
                                    runningObservers.put(observer.getWatchedUri(), observer);
                                    observer.startWatching();
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
            removeAllContentObservers(runningObservers);

            unregisterBroadcastReceiver(context);
            EventBus.getDefault().post(new BackgroundUploadThreadTerminatedEvent());
        }
    }

    private void removeAllContentObservers(Map<Uri, CustomContentObserver> runningObservers) {
        List<Uri> keys = new ArrayList<>(runningObservers.keySet());
        for(Uri key : keys) {
            CustomContentObserver observer = runningObservers.remove(key);
            if (observer != null) {
                observer.stopWatching();
            }
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
    protected void updateListOfPreviouslyUploadedFiles(UploadJob uploadJob, HashMap<Uri, String> uploadedFileChecksums) {
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
            pauseThreadUntilSysTime = System.currentTimeMillis() - 1;
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
        DocumentFile localFolderToMonitor = jobConfig.getLocalFolderToMonitor(context);
        if(!localFolderToMonitor.exists()) {
            postNewResponse(jobConfig.getJobId(), new PiwigoResponseBufferingHandler.CustomErrorResponse(jobConfig.getJobId(), getString(R.string.ignoring_job_local_folder_not_found)));
            return null;
        }
        boolean compressVideos = jobConfig.isCompressVideosBeforeUpload(context);
        Set<String> fileExtsToUpload = jobConfig.getFileExtsToUpload(context);
        int maxFileSizeMb = jobConfig.getMaxUploadSize(context);
        if (fileExtsToUpload == null) {
            Bundle b = new Bundle();
            b.putString("message", "No File extensions selected for upload - nothing can be uploaded. Ignoring job");
            FirebaseAnalytics.getInstance(context).logEvent("uploadError", b);
            postNewResponse(jobConfig.getJobId(), new PiwigoResponseBufferingHandler.CustomErrorResponse(jobConfig.getJobId(), getString(R.string.ignoring_job_no_file_types_selected_for_upload)));
            return null;
        }
        SimpleDocumentFileFilter filter = new SimpleDocumentFileFilter() {
            @Override
            protected boolean nonAcceptOverride(DocumentFile f) {
                return compressVideos && MimeTypeFilter.matches(f.getType(), "video/*");
            }
        }.withFileExtIn(fileExtsToUpload).withMaxSize(maxFileSizeMb);
        List<DocumentFile> matchingFiles = IOUtils.filterDocumentFiles(localFolderToMonitor.listFiles(), filter);
        if (matchingFiles.isEmpty()) {
            return null;
        }
        matchingFiles = IOUtils.getFilesNotBeingWritten(matchingFiles, 1000);
        if(matchingFiles.isEmpty()) {
            return null;
        }
        ArrayList<Uri> filesToUpload = new ArrayList<>(matchingFiles.size());
        for (DocumentFile matchingFile : matchingFiles) {
            filesToUpload.add(matchingFile.getUri());
        }

        CategoryItemStub category = jobConfig.getUploadToAlbum(context);
        UploadJob uploadJob = createUploadJob(jobConfig.getConnectionPrefs(context, getPrefs()), filesToUpload, category,
                jobConfig.getUploadedFilePrivacyLevel(context), jobListener.getHandlerId(), jobConfig.isDeleteFilesAfterUpload(context));
        uploadJob.withContext(this);
        uploadJob.setToRunInBackground();
        uploadJob.setJobConfigId(jobConfig.getJobId());
        if (uploadJob.getConnectionPrefs().isOfflineMode(getPrefs(), context)) {
            postNewResponse(jobConfig.getJobId(), new PiwigoResponseBufferingHandler.CustomErrorResponse(jobConfig.getJobId(), getString(R.string.ignoring_job_connection_profile_set_for_offline_access)));
            return null;
        }

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

    private static class CustomContentObserver extends ContentObserver {

        private final BackgroundPiwigoUploadService uploadService;
        private final Uri watchedUri;
        private EventProcessor eventProcessor;

        /**
         * Creates a content observer.
         *
         * @param handler The handler to run {@link #onChange} on, or null if none.
         * @param uploadService
         * @param watchedUri
         */
        public CustomContentObserver(Handler handler, BackgroundPiwigoUploadService uploadService, Uri watchedUri) {
            super(handler);
            this.uploadService = uploadService;
            this.watchedUri = watchedUri;
        }

        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if(eventProcessor == null) {
                eventProcessor = new EventProcessor();
            }
            eventProcessor.execute(uploadService, watchedUri, uri);
        }

        public void startWatching() {
            uploadService.getContentResolver().registerContentObserver(watchedUri, false, this);
        }

        public void stopWatching() {
            uploadService.getContentResolver().unregisterContentObserver(this);
        }

        public Uri getWatchedUri() {
            return watchedUri;
        }

        private class EventProcessor extends Thread {

            private WeakReference<Context> contextRef;
            private Uri eventSourceUri;
            private int lastEventId;
            private boolean running;

            @Override
            public void run() {
                while (!processEvent(watchedUri, eventSourceUri, lastEventId)) {
                } // do until processed.
                lastEventId = 0;
            }

            public void execute(Context context, Uri watchedUri, Uri eventSourceUri) {
                contextRef = new WeakReference<>(context);
                lastEventId++;
                if (lastEventId <= 0) {
                    lastEventId = 1;
                }
                this.eventSourceUri = eventSourceUri;
                synchronized (this) {
                    if (!running) {
                        running = true;
                        start();
                    }
                }
            }

            private boolean processEvent(Uri watchedUri, Uri eventSourceUri, int eventId) {
                DocumentFile file = DocumentFile.fromSingleUri(contextRef.get(), eventSourceUri);
                if(file == null) {
                    Crashlytics.log(Log.ERROR, TAG, "Unable to retrieve DocumentFile for uri " + eventSourceUri);
                    return false;
                }
                long len = file.length();
                long lastMod = file.lastModified();
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (eventId == lastEventId && len == file.length() && lastMod == file.lastModified()) {
                    uploadService.wakeIfWaiting();
                    return true;
                }
                return false;
            }
        }
    }
}
