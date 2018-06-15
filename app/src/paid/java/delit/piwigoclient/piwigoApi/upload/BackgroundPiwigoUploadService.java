package delit.piwigoclient.piwigoApi.upload;

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.PowerManager;
import android.util.Log;

import com.google.android.gms.common.util.IOUtils;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.ui.events.BackgroundUploadStartedEvent;
import delit.piwigoclient.ui.events.BackgroundUploadStoppedEvent;
import delit.piwigoclient.ui.preferences.AutoUploadJobConfig;
import delit.piwigoclient.ui.preferences.AutoUploadJobsConfig;
import delit.piwigoclient.util.CustomFileFilter;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 */
public class BackgroundPiwigoUploadService extends BasePiwigoUploadService {

    private static final String TAG = "BkgrdUploadService";
    private static final String ACTION_BACKGROUND_UPLOAD_FILES = "delit.piwigoclient.action.ACTION_BACKGROUND_UPLOAD_FILES";
    private CustomFileFilter fileFilter = new CustomFileFilter();
    private static volatile boolean exit = false;
    private NetworkStatusChangeListener networkStatusChangeListener;
    private static BackgroundPiwigoUploadService instance;
    private static boolean starting;
    private static UploadJob runningUploadJob = null;

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
        Intent intent = new Intent(context, BackgroundPiwigoUploadService.class);
        intent.setAction(ACTION_BACKGROUND_UPLOAD_FILES);
        intent.putExtra(INTENT_ARG_KEEP_DEVICE_AWAKE, keepDeviceAwake);
        ComponentName name = context.startService(intent);
    }

    public static boolean isStarted() {
        return instance != null;
    }

    public synchronized static void killService() {
        if(instance != null) {
            exit = true;
            instance.notify();
            if(runningUploadJob != null) {
                runningUploadJob.cancelUploadAsap();
            }
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
        doWork(intent);
    }

    @Override
    protected void doWork(Intent intent) {
        Context context = getApplicationContext();

        registerBroadcastReceiver(context);

        try {

            long pollDurationMillis = 1000 * 60 * 15; // every 15 minutes

            AutoUploadJobsConfig jobs = new AutoUploadJobsConfig(context);
            BackgroundPiwigoFileUploadResponseListener jobListener = new BackgroundPiwigoFileUploadResponseListener(context);
            while (!exit) {

                boolean canUpload;
                if (jobs.isUploadOnWirelessOnly(context)) {
                    canUpload = isConnectedToWireless(context);
                } else {
                    canUpload = hasNetworkConnection(context);
                }

                if(canUpload) {
                    // if there's an old incomplete job, try and finish that first.
                    UploadJob unfinishedJob = getActiveBackgroundJob(context);
                    runJob(unfinishedJob);

                    if (jobs.getUploadJobsCount(context) > 0) {
                        List<AutoUploadJobConfig> jobConfigList = jobs.getAutoUploadJobs(context);
                        PowerManager.WakeLock wl = getWakeLock(intent);
                        try {
                            for (AutoUploadJobConfig jobConfig : jobConfigList) {

                                if (jobConfig.isJobEnabled(context) && jobConfig.isJobValid(context)) {
                                    UploadJob uploadJob = getUploadJob(context, jobConfig, jobListener);
                                    if (uploadJob != null) {
                                            EventBus.getDefault().post(new BackgroundUploadStartedEvent(uploadJob));
                                            runJob(uploadJob);
                                        EventBus.getDefault().post(new BackgroundUploadStoppedEvent(uploadJob));
                                    }
                                }
                            }
                        } finally {
                            releaseWakeLock(wl);
                        }
                    }
                }
                synchronized (this) {
                    long endTime = System.currentTimeMillis() + pollDurationMillis;
                    while (System.currentTimeMillis() < endTime && !exit) {
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
        }
    }

    private void unregisterBroadcastReceiver(Context context) {
        if(networkStatusChangeListener != null) {
            context.unregisterReceiver(networkStatusChangeListener);
        }
    }

    private void registerBroadcastReceiver(Context context) {

        networkStatusChangeListener = new NetworkStatusChangeListener(this);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        context.registerReceiver(networkStatusChangeListener, intentFilter);
    }

    private static class NetworkStatusChangeListener extends BroadcastReceiver {

        private BackgroundPiwigoUploadService service;

        public NetworkStatusChangeListener(BackgroundPiwigoUploadService service) {
            this.service = service;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final ConnectivityManager connMgr = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);

            final android.net.NetworkInfo wifi = connMgr
                    .getNetworkInfo(ConnectivityManager.TYPE_WIFI);

            if (wifi.isAvailable() && wifi.isConnected()) {
                // wake immediately.
                service.wakeIfWaiting();
            } else {
                service.pauseAnyRunningUpload();
            }
        }
    }

    private synchronized void wakeIfWaiting() {
        notifyAll();
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
        return uploadJob;
    }

    @Override
    protected void postNewResponse(long jobId, PiwigoResponseBufferingHandler.Response response) {
        //TODO do something useful with the messages... build a log file?

//        PiwigoResponseBufferingHandler.getDefault().preRegisterResponseHandlerForNewMessage(jobId, response.getMessageId());
//        PiwigoResponseBufferingHandler.getDefault().processResponse(response);
    }



}
