package delit.piwigoclient.piwigoApi.upload;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import org.greenrobot.eventbus.EventBus;

import java.util.HashMap;

import delit.libs.core.util.Logging;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.upload.actors.ActorListener;
import delit.piwigoclient.piwigoApi.upload.actors.ForegroundJobLoadActor;
import delit.piwigoclient.piwigoApi.upload.actors.UploadNotificationManager;
import delit.piwigoclient.ui.UploadActivity;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 */
public class ForegroundPiwigoUploadService extends BasePiwigoUploadService {

    public static final String INTENT_ARG_JOB_ID = "jobId";
    private static final String TAG = "PwgCli:FgUpldSvc";
    private static final String ACTION_UPLOAD_FILES = "delit.piwigoclient.foregroundUpload.action.ACTION_UPLOAD_FILES";
    private static final int FOREGROUND_UPLOAD_NOTIFICATION_ID = 3;
    private static final int JOB_ID = 20;
    private static final String ACTION_STOP = "delit.piwigoclient.foregroundUpload.action.STOP";

    public ForegroundPiwigoUploadService() {
        super();
    }

    protected ActionsBroadcastReceiver buildActionBroadcastReceiver() {
        return new ActionsBroadcastReceiver(ACTION_STOP);
    }

    /**
     *
     * @param context an active context
     * @param uploadJob the job to run
     * @return jobId of the started job (passed in as param)
     */
    public static long startActionRunOrReRunUploadJob(@NonNull Context context, @NonNull UploadJob uploadJob) {
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent(appContext, ForegroundPiwigoUploadService.class);
        intent.setAction(ACTION_UPLOAD_FILES);
        intent.putExtra(INTENT_ARG_JOB_ID, uploadJob.getJobId());
        uploadJob.setStatusSubmitted();
        try {
            enqueueWork(appContext, ForegroundPiwigoUploadService.class, JOB_ID, intent);
        } catch(RuntimeException e) {
            Logging.log(Log.ERROR,TAG, "Unexpected error starting upload service");
            Logging.recordException(e);
            uploadJob.setStatusStopped();
        }
        return uploadJob.getJobId();
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {

        try {
            if(BuildConfig.DEBUG) {
                Log.d(TAG, "Foreground upload service looking for work");
            }
            doBeforeWork(intent);
            doWork(intent);
            if(BuildConfig.DEBUG) {
                Log.d(TAG, "Foreground upload service about to end nicely");
            }
        } catch(RuntimeException e) {
            Bundle b = new Bundle();
            b.putSerializable("error", e);
            b.putString("service", TAG);
            Logging.logAnalyticEvent(this,"UploadServiceCrash", b);
            Logging.recordException(e);
        } finally {
            if(BuildConfig.DEBUG) {
                Log.d(TAG, "Foreground upload service ending");
            }
            stopForeground(true);
//            EventBus.getDefault().post(new ForegroundUploadFinishedEvent());
        }
        if(BuildConfig.DEBUG) {
            Log.d(TAG, "Foreground upload service really ended nicely");
        }
    }

    @Override
    protected UploadNotificationManager buildUploadNotificationManager() {
        return new UploadNotificationManager(this) {
            @Override
            public int getNotificationId() {
                return FOREGROUND_UPLOAD_NOTIFICATION_ID;
            }

            @Override
            protected String getNotificationTitle() {
                return getString(R.string.notification_title_foreground_upload_service);
            }

            @Override
            protected NotificationCompat.Builder buildNotification(String text) {
                NotificationCompat.Builder builder = super.buildNotification(text);
                Intent contentIntent = new Intent(getContext(), UploadActivity.class);
                Intent cancelIntent = new Intent();
                cancelIntent.setAction(ForegroundPiwigoUploadService.ACTION_STOP);
                PendingIntent pendingIntent = PendingIntent.getActivity(getContext(), 0, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                // add a cancel button to cancel the upload if clicked
                builder.addAction(new NotificationCompat.Action(R.drawable.ic_cancel_black, getString(R.string.button_cancel), pendingIntent));
                // open the upload activity if notification clicked
                builder.setContentIntent(PendingIntent.getActivity(getContext(), 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT));
                return builder;
            }
        };
    }

    @Override
    protected void doWork(@NonNull Intent intent) {
        long jobId = intent.getLongExtra(INTENT_ARG_JOB_ID, -1);
        runJob(jobId);
    }


    protected final void runJob(long jobId) {
        try {
            EventBus.getDefault().register(this);
            ForegroundJobLoadActor jobLoadActor = new ForegroundJobLoadActor(this);
            UploadJob thisUploadJob = jobLoadActor.getActiveForegroundJob(jobId);
            runJob(jobLoadActor, thisUploadJob, null, true);
        } finally {
            EventBus.getDefault().unregister(this);
        }
    }

    @Override
    protected void updateListOfPreviouslyUploadedFiles(UploadJob uploadJob, HashMap<Uri, String> uploadedFileChecksums) {
        //TODO add the files checksums to a list that can then be used by the file selection for upload fragment perhaps to show those files that have been uploaded subtly.
    }

    @Override
    protected ActorListener buildUploadActorListener(UploadJob uploadJob, UploadNotificationManager notificationManager) {
        return new ActorListener(uploadJob, notificationManager) {
            @Override
            public void postNewResponse(long jobId, PiwigoResponseBufferingHandler.Response response) {
                PiwigoResponseBufferingHandler.getDefault().preRegisterResponseHandlerForNewMessage(jobId, response.getMessageId());
                PiwigoResponseBufferingHandler.getDefault().processResponse(response);
            }
        };
    }
}
