package delit.piwigoclient.piwigoApi.upload;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import com.crashlytics.android.Crashlytics;

import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 */
public class ForegroundPiwigoUploadService extends BasePiwigoUploadService {

    public static final String INTENT_ARG_JOB_ID = "jobId";
    private static final String TAG = "UserUploadService";
    private static final String ACTION_UPLOAD_FILES = "delit.piwigoclient.action.ACTION_UPLOAD_FILES";
    private static final int FOREGROUND_UPLOAD_NOTIFICATION_ID = 3;

    public ForegroundPiwigoUploadService() {
        super(TAG);
    }

    public static long startActionRunOrReRunUploadJob(Context context, UploadJob uploadJob, boolean keepDeviceAwake) {

        Intent intent = new Intent(context, ForegroundPiwigoUploadService.class);
        intent.setAction(ACTION_UPLOAD_FILES);
        intent.putExtra(INTENT_ARG_JOB_ID, uploadJob.getJobId());
        intent.putExtra(INTENT_ARG_KEEP_DEVICE_AWAKE, keepDeviceAwake);
        ComponentName name = context.startService(intent);
        if(name == null) {
            Crashlytics.log(Log.ERROR, TAG, "Unable to start background service. Service does not exist");
        }
        uploadJob.setSubmitted(true);
        return uploadJob.getJobId();
    }

    @SuppressLint("WakelockTimeout")
    @Override
    protected void onHandleIntent(Intent intent) {

        PowerManager.WakeLock wl = getWakeLock(intent);
        try {
            doBeforeWork(intent);
            doWork(intent);
        } finally {
            releaseWakeLock(wl);
            stopForeground(true);
        }
    }

    @Override
    protected int getNotificationId() {
        return FOREGROUND_UPLOAD_NOTIFICATION_ID;
    }

    @Override
    protected String getNotificationTitle() {
        return getString(R.string.notification_title_foreground_upload_service);
    }

    @Override
    protected void doWork(Intent intent) {
        long jobId = intent.getLongExtra(INTENT_ARG_JOB_ID, -1);
        runJob(jobId);
    }

    @Override
    protected void postNewResponse(long jobId, PiwigoResponseBufferingHandler.Response response) {
        PiwigoResponseBufferingHandler.getDefault().preRegisterResponseHandlerForNewMessage(jobId, response.getMessageId());
        PiwigoResponseBufferingHandler.getDefault().processResponse(response);
    }


}
