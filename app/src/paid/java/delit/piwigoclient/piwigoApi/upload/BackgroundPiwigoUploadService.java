package delit.piwigoclient.piwigoApi.upload;

import android.app.ActivityManager;
import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.MimeTypeFilter;
import androidx.documentfile.provider.DocumentFile;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.greenrobot.eventbus.EventBus;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.util.IOUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.ui.events.BackgroundUploadStartedEvent;
import delit.piwigoclient.ui.events.BackgroundUploadStoppedEvent;
import delit.piwigoclient.ui.events.BackgroundUploadThreadCheckingForTasksEvent;
import delit.piwigoclient.ui.events.BackgroundUploadThreadStartedEvent;
import delit.piwigoclient.ui.events.BackgroundUploadThreadTerminatedEvent;
import delit.piwigoclient.ui.file.DocumentFileFilter;
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
    private static final String ACTION_WAKE = "delit.piwigoclient.backgroundUpload.action.ACTION_WAKE";
    private static final String ACTION_PAUSE =  "delit.piwigoclient.backgroundUpload.action.ACTION_PAUSE";
    private static final String ACTION_STOP = "delit.piwigoclient.backgroundUpload.action.ACTION_STOP";
    private static final int JOB_ID = 10;

    private static volatile boolean terminateUploadServiceThreadAsap = false;
    private BroadcastEventsListener networkStatusChangeListener;
    private ActionsBroadcastReceiver actionsBroadcastReceiver;
    private static boolean starting;
    private long pauseThreadUntilSysTime;
    private static final int BACKGROUND_UPLOAD_NOTIFICATION_ID = 2;
    private boolean hasInternetAccess;
    private boolean isOnUnmeteredNetwork;

    public BackgroundPiwigoUploadService() {
        super(TAG);
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

    public static void sendActionWakeServiceIfSleeping(Context context) {
        Intent intent = new Intent();
        intent.setAction(ACTION_WAKE);
        context.getApplicationContext().sendBroadcast(intent);
    }

    private static void pauseAnyRunningUpload(Context context) {
        Intent intent = new Intent();
        intent.setAction(ACTION_PAUSE);
        context.getApplicationContext().sendBroadcast(intent);
    }

    public static boolean isStarted(Context context) {
        return isMyServiceRunning(context.getApplicationContext(), BackgroundPiwigoUploadService.class);
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

        isOnUnmeteredNetwork = isConnectedToWireless(context);
        hasInternetAccess = hasNetworkConnection(context);

        EventBus.getDefault().post(new BackgroundUploadThreadStartedEvent());

        AutoUploadJobsConfig jobs = new AutoUploadJobsConfig(context);

        registerBroadcastReceiver(context, jobs);

        Map<Uri, CustomContentObserver> runningObservers = new HashMap<>();

        try {

            BackgroundPiwigoFileUploadResponseListener jobListener = new BackgroundPiwigoFileUploadResponseListener(context);

            while (!terminateUploadServiceThreadAsap) {

                EventBus.getDefault().post(new BackgroundUploadThreadCheckingForTasksEvent());

                boolean canUpload = hasInternetAccess && (!jobs.isUploadOnWirelessOnly(context) || isOnUnmeteredNetwork);
                if(canUpload) {
                    pollFoldersForJobsAndUploadAnyMatchingFilesFoundNow(context, jobs, runningObservers, jobListener);
                }
                sendServiceToSleep();
            }
        } finally {
            // unable to remove from keyset iterator hence the temporary list.
            removeAllContentObservers(runningObservers);

            unregisterBroadcastReceiver(context);
            EventBus.getDefault().post(new BackgroundUploadThreadTerminatedEvent());
        }
    }

    private void pollFoldersForJobsAndUploadAnyMatchingFilesFoundNow(Context context, AutoUploadJobsConfig jobs, Map<Uri, CustomContentObserver> runningObservers, BackgroundPiwigoFileUploadResponseListener jobListener) {
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

    private void sendServiceToSleep() {
        long pollDurationMillis = 1000 * 60 * 60 * 3; // check once every 3 hours at latest (poll file system for changes though).
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
            updateNotificationText(getString(R.string.notification_text_background_upload_running), thisUploadJob.getUploadProgress());
            boolean connectionDetailsValid = thisUploadJob.getConnectionPrefs().isValid(this);
            if(connectionDetailsValid) {
                super.runJob(thisUploadJob, listener, deleteJobConfigFileOnSuccess);
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

    private void unregisterBroadcastReceiver(Context context) {
        if(networkStatusChangeListener != null) {
            context.unregisterReceiver(networkStatusChangeListener);
        }
    }

    private void registerBroadcastReceiver(Context context, AutoUploadJobsConfig autoUploadJobsConfig) {

        ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cm.registerDefaultNetworkCallback(new MyNetworkCallback(new MyNetworkListener(context, autoUploadJobsConfig)));
        } else {
            networkStatusChangeListener = new BroadcastEventsListener(new MyNetworkListener(context, autoUploadJobsConfig));
            context.registerReceiver(networkStatusChangeListener, networkStatusChangeListener.getIntentFilter());
        }
    }

    @Override
    public void onJobReadyToUpload(Context c, UploadJob thisUploadJob) {
        AutoUploadJobConfig jobConfig = new AutoUploadJobConfig(thisUploadJob.getJobConfigId());
        AutoUploadJobConfig.PriorUploads priorUploads = jobConfig.getFilesPreviouslyUploaded(c);
        thisUploadJob.filterPreviouslyUploadedFiles(priorUploads.getFileUrisAndHashcodes());
        // technically this is called after the job has already started, but the user doesn't need to know that.
        if(thisUploadJob.getFilesForUpload().size() > 0) {
            EventBus.getDefault().post(new BackgroundUploadStartedEvent(thisUploadJob, thisUploadJob.hasBeenRunBefore()));
        }
    }

    private static class BroadcastEventsListener extends BroadcastReceiver {

        private final MyBroadcastEventListener listener;

        public BroadcastEventsListener(MyBroadcastEventListener listener) {
            this.listener = listener;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if(Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
                handleDeviceUnlocked(context);
            } else if(ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                handleNetworkStatusChanged(intent);
            } else if(WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                handleWifiEvent(context, intent);
            }
        }

        private void handleWifiEvent(Context context, Intent intent) {
            boolean hasWifi = false;
            boolean hasInternet = false;
            NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            if(networkInfo != null && networkInfo.isConnected()) {
                // Wifi is connected
                hasWifi = true;
            }
            final ConnectivityManager connMgr = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            if(connMgr != null) {
                networkInfo = connMgr.getActiveNetworkInfo();
                hasInternet = networkInfo != null && networkInfo.isConnected();
            }

            listener.onNetworkChange(hasInternet, hasWifi);
        }

        private void handleDeviceUnlocked(Context context) {
            listener.onDeviceUnlocked();
        }

        private void handleNetworkStatusChanged(Intent intent) {
            boolean hasWifi = false;
            boolean hasInternet;
            NetworkInfo networkInfo =
                    intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
            if(networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                hasWifi = networkInfo.isConnected();
            }
            hasInternet = networkInfo != null && networkInfo.isConnected();
            listener.onNetworkChange(hasInternet, hasWifi);
        }

        public IntentFilter getIntentFilter() {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
            intentFilter.addAction("android.intent.action.USER_PRESENT");
            return intentFilter;
        }
    }

    private void wakeIfPaused() {
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
        NetworkInfo networkInfo;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = Objects.requireNonNull(cm).getActiveNetwork();
            networkInfo = cm.getNetworkInfo(activeNetwork);
        } else {
            networkInfo = Objects.requireNonNull(cm).getActiveNetworkInfo();
        }
        return networkInfo != null && networkInfo.isConnected();
    }

    private boolean isConnectedToWireless(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = Objects.requireNonNull(cm).getActiveNetwork();
            networkInfo = cm.getNetworkInfo(activeNetwork);
        } else {
            networkInfo = Objects.requireNonNull(cm).getActiveNetworkInfo();
        }
        if (networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
            return networkInfo.isConnected();
        }
        return false;
    }

    private UploadJob getUploadJob(Context context, AutoUploadJobConfig jobConfig, BackgroundPiwigoFileUploadResponseListener jobListener) {
        DocumentFile localFolderToMonitor = jobConfig.getLocalFolderToMonitor(context);
        if(localFolderToMonitor == null || !localFolderToMonitor.exists()) {
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
        }.withFileExtIn(fileExtsToUpload).withMaxSizeBytes(maxFileSizeMb * 1024 * 1024);
        List<DocumentFile> matchingFiles = DocumentFileFilter.filterDocumentFiles(localFolderToMonitor.listFiles(), filter);
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

        private final Uri watchedUri;
        private final Context context;
        private EventProcessor eventProcessor;

        /**
         * Creates a content observer.
         *
         * @param handler The handler to run {@link #onChange} on, or null if none.
         */
        public CustomContentObserver(Handler handler, Context context, Uri watchedUri) {
            super(handler);
            this.context = context;
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
            eventProcessor.execute(context, watchedUri, uri);
        }

        public void startWatching() {
            context.getContentResolver().registerContentObserver(watchedUri, false, this);
        }

        public void stopWatching() {
            context.getContentResolver().unregisterContentObserver(this);
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
                boolean processed;
                do {
                    processed = processEvent(watchedUri, eventSourceUri, lastEventId);
                } while(!processed); // do until processed.
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

            private @NonNull Context getContext() {
                return Objects.requireNonNull(contextRef.get());
            }

            private boolean processEvent(Uri watchedUri, Uri eventSourceUri, int eventId) {
                DocumentFile file = DocumentFile.fromSingleUri(getContext(), eventSourceUri);
                if(file == null) {
                    Logging.log(Log.ERROR, TAG, "Unable to retrieve DocumentFile for uri " + eventSourceUri);
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
                    BackgroundPiwigoUploadService.sendActionWakeServiceIfSleeping(context);
                    return true;
                }
                return false;
            }
        }
    }

    private interface MyBroadcastEventListener {
        void onNetworkChange(boolean internetAccess, boolean unmeteredNet);

        void onDeviceUnlocked();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static class MyNetworkCallback extends ConnectivityManager.NetworkCallback {

        private MyBroadcastEventListener listener;

        public MyNetworkCallback(MyBroadcastEventListener listener) {
            this.listener = listener;
        }

        @Override
        public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities);
            boolean unmeteredNet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
            boolean internetAccess = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            listener.onNetworkChange(internetAccess, unmeteredNet);
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
                pauseAnyRunningUpload();
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

    private class MyNetworkListener implements MyBroadcastEventListener {
        private final Context context;
        private final AutoUploadJobsConfig autoUploadJobsConfig;

        public MyNetworkListener(Context context, AutoUploadJobsConfig autoUploadJobsConfig) {
            this.context = context;
            this.autoUploadJobsConfig = autoUploadJobsConfig;
        }

        @Override
        public void onNetworkChange(boolean internetAccess, boolean unmeteredNet) {
            hasInternetAccess = internetAccess;
            isOnUnmeteredNetwork = unmeteredNet;
            if (hasInternetAccess && (!autoUploadJobsConfig.isUploadOnWirelessOnly(context) || isOnUnmeteredNetwork)) {
//                BackgroundPiwigoUploadService.sendActionWakeServiceIfSleeping(context);
                wakeIfPaused();
            } else {
//                BackgroundPiwigoUploadService.sendActionPauseAnyRunningUpload(context);
                pauseAnyRunningUpload();
            }
        }

        @Override
        public void onDeviceUnlocked() {
//            BackgroundPiwigoUploadService.sendActionWakeServiceIfSleeping(context);
            wakeIfPaused();
        }
    }
}
