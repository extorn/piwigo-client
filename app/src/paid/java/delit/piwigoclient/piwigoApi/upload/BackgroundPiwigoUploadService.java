package delit.piwigoclient.piwigoApi.upload;

import android.app.ActivityManager;
import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
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
    private BroadcastEventsListener networkStatusChangeListener;
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
     * @param context
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

        isOnUnmeteredNetwork = isConnectedToWireless(context);
        hasInternetAccess = hasNetworkConnection(context);

        EventBus.getDefault().post(new BackgroundUploadThreadStartedEvent());

        AutoUploadJobsConfig jobs = new AutoUploadJobsConfig(context);

        registerBroadcastReceiver(context, jobs);

        Map<Uri, UriWatcher> runningObservers = new HashMap<>();

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
                        if("file".equals(monitoringFolder.getUri().getScheme())) {
                            observer = new BackgroundUploadInvokingFolderChangeObserver(this, new File(monitoringFolder.getUri().getPath()));
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
        //FIXME this isn't scalable. Needs storing in a database and querying on a file by file basis as needed
        AutoUploadJobConfig.PriorUploads priorUploads = jobConfig.getFilesPreviouslyUploaded(c);
        thisUploadJob.filterPreviouslyUploadedFiles(priorUploads.getFileUrisAndHashcodes());
        // technically this is called after the job has already started, but the user doesn't need to know that.
        if(thisUploadJob.hasProcessableFiles()) {
            EventBus.getDefault().post(new BackgroundUploadStartedEvent(thisUploadJob, thisUploadJob.isHasRunBefore()));
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
                handleDeviceUnlocked();
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

        private void handleDeviceUnlocked() {
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

    private interface MyBroadcastEventListener {
        void onNetworkChange(boolean internetAccess, boolean unmeteredNet);

        void onDeviceUnlocked();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static class MyNetworkCallback extends ConnectivityManager.NetworkCallback {

        private final MyBroadcastEventListener listener;

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
                cancelAnyRunningUploadJob();
            }
        }

        @Override
        public void onDeviceUnlocked() {
//            BackgroundPiwigoUploadService.sendActionWakeServiceIfSleeping(context);
            wakeIfPaused();
        }
    }
}
