package delit.piwigoclient.piwigoApi.upload;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import org.greenrobot.eventbus.EventBus;

import delit.libs.core.util.Logging;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.upload.action.ForegroundUploadNotificationManager;
import delit.piwigoclient.piwigoApi.upload.actors.ActorListener;
import delit.piwigoclient.piwigoApi.upload.actors.ForegroundJobLoadActor;
import delit.piwigoclient.piwigoApi.upload.actors.UploadNotificationManager;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 */
public class BaseForegroundPiwigoUploadService<T extends BaseForegroundPiwigoUploadService<T>> extends BasePiwigoUploadService<T> {

    public static final String INTENT_ARG_JOB_ID = "jobId";
    private static final String TAG = "PwgCli:FgUpldSvc";
    private static final String ACTION_UPLOAD_FILES = "delit.piwigoclient.foregroundUpload.action.ACTION_UPLOAD_FILES";
    private static final int JOB_ID = 20;
    public static final String ACTION_STOP = "delit.piwigoclient.foregroundUpload.action.STOP";

    public BaseForegroundPiwigoUploadService() {
        super();
    }

    protected UploadActionsBroadcastReceiver<T> buildActionBroadcastReceiver() {
        return new ForegroundUploadActionsBroadcastReceiver<>((T)this);
    }

    public static class ForegroundUploadActionsBroadcastReceiver<T extends BaseForegroundPiwigoUploadService<T>> extends UploadActionsBroadcastReceiver<T> {

        public ForegroundUploadActionsBroadcastReceiver(@NonNull T service) {
            super(service, ACTION_STOP);
        }
    }

    /**
     *
     * @param context an active context
     * @param uploadJob the job to run
     * @return jobId of the started job (passed in as param)
     */
    protected static <T extends BaseForegroundPiwigoUploadService<T>> long startActionRunOrReRunUploadJob(@NonNull Context context, @NonNull UploadJob uploadJob, Class<T> serviceClass) {
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent(appContext, serviceClass);
        intent.setAction(ACTION_UPLOAD_FILES);
        intent.putExtra(INTENT_ARG_JOB_ID, uploadJob.getJobId());
        uploadJob.setStatusSubmitted();
        try {
            enqueueWork(appContext, serviceClass, JOB_ID, intent);
        } catch(RuntimeException e) {
            Logging.log(Log.ERROR,TAG, "Unexpected error starting upload service");
            Logging.recordException(e);
            uploadJob.setStatusStopped();
        }
        return uploadJob.getJobId();
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        String oldThreadName = Thread.currentThread().getName();
        Thread.currentThread().setName("Foreground Upload Service");
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
            Thread.currentThread().setName(oldThreadName);
        }
        if(BuildConfig.DEBUG) {
            Log.d(TAG, "Foreground upload service really ended nicely");
        }
    }

    @Override
    protected UploadNotificationManager buildUploadNotificationManager() {
        return new ForegroundUploadNotificationManager(this);
    }

    @Override
    protected void doWork(@NonNull Intent intent) {
        long jobId = intent.getLongExtra(INTENT_ARG_JOB_ID, -1);
        runJob(jobId);
    }


    protected final void runJob(long jobId) {
        try {
            EventBus.getDefault().register(this);
            ForegroundJobLoadActor jobLoadActor = getJobLoadActor();
            UploadJob thisUploadJob = jobLoadActor.getActiveForegroundJob(jobId);
            runJob(jobLoadActor, thisUploadJob, null, true);
        } finally {
            EventBus.getDefault().unregister(this);
        }
    }

    @Override
    protected void updateListOfPreviouslyUploadedFiles(UploadJob uploadJob, ActorListener actorListener) {
    }

    @Override
    protected ForegroundJobLoadActor getJobLoadActor() {
        return new ForegroundJobLoadActor(this);
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
