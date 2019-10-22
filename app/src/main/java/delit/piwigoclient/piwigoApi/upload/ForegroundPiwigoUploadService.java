package delit.piwigoclient.piwigoApi.upload;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.util.HashMap;

import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.ui.UploadActivity;

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
    public static final String ACTION_CANCEL_JOB = "cancelForegroundJob";

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
    protected NotificationCompat.Builder buildNotification(String text) {
        NotificationCompat.Builder builder = super.buildNotification(text);
        Intent contentIntent = new Intent(this, UploadActivity.class);
        contentIntent.putExtra(ForegroundPiwigoUploadService.ACTION_CANCEL_JOB, true);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            builder.addAction(new NotificationCompat.Action(R.drawable.ic_cancel_black, getString(R.string.button_cancel), pendingIntent));
        } else {
            builder.addAction(new NotificationCompat.Action(R.drawable.ic_cancel_black, getString(R.string.button_cancel), pendingIntent));
        }
        return builder;
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
    protected void updateListOfPreviouslyUploadedFiles(UploadJob uploadJob, HashMap<File, String> uploadedFileChecksums) {
        //TODO add the files checksums to a list that can then be used by the file selection for upload fragment perhaps to show those files that have been uploaded subtly.
    }

    @Override
    protected void postNewResponse(long jobId, PiwigoResponseBufferingHandler.Response response) {
        PiwigoResponseBufferingHandler.getDefault().preRegisterResponseHandlerForNewMessage(jobId, response.getMessageId());
        PiwigoResponseBufferingHandler.getDefault().processResponse(response);
    }


}
