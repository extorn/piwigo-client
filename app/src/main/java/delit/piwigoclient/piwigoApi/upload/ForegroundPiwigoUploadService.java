package delit.piwigoclient.piwigoApi.upload;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;

import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 */
public class ForegroundPiwigoUploadService extends BasePiwigoUploadService {

    public static final String INTENT_ARG_JOB_ID = "jobId";
    private static final String TAG = "PwgCli:FgUpldSvc";
    private static final String ACTION_UPLOAD_FILES = "delit.piwigoclient.action.ACTION_UPLOAD_FILES";
    private static final int FOREGROUND_UPLOAD_NOTIFICATION_ID = 3;
    private static final int JOB_ID = 20;

    public ForegroundPiwigoUploadService() {
        super(TAG);
    }

    public static long startActionRunOrReRunUploadJob(Context context, UploadJob uploadJob) {

        Intent intent = new Intent(context, ForegroundPiwigoUploadService.class);
        intent.setAction(ACTION_UPLOAD_FILES);
        intent.putExtra(INTENT_ARG_JOB_ID, uploadJob.getJobId());
        enqueueWork(context, ForegroundPiwigoUploadService.class, JOB_ID, intent);
        uploadJob.setSubmitted(true);
        return uploadJob.getJobId();
    }

    @Override
    protected void onHandleWork(Intent intent) {

        try {
            doBeforeWork(intent);
            doWork(intent);
        } finally {
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
