package delit.piwigoclient.piwigoApi.upload;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import org.greenrobot.eventbus.EventBus;

import java.util.HashMap;

import delit.libs.core.util.Logging;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.database.PiwigoUploadsDatabase;
import delit.piwigoclient.database.PriorUploadRepository;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.upload.action.ForegroundUploadNotificationManager;
import delit.piwigoclient.piwigoApi.upload.actors.ActorListener;
import delit.piwigoclient.piwigoApi.upload.actors.ForegroundJobLoadActor;
import delit.piwigoclient.piwigoApi.upload.actors.PriorUploadsActor;
import delit.piwigoclient.piwigoApi.upload.actors.UploadNotificationManager;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 */
public class ForegroundPiwigoUploadService extends BaseForegroundPiwigoUploadService<ForegroundPiwigoUploadService>{

    public ForegroundPiwigoUploadService() {
        super();
    }

    /**
     *
     * @param context an active context
     * @param uploadJob the job to run
     * @return jobId of the started job (passed in as param)
     */
    public static long startActionRunOrReRunUploadJob(@NonNull Context context, @NonNull UploadJob uploadJob) {
        return startActionRunOrReRunUploadJob(context, uploadJob, ForegroundPiwigoUploadService.class);
    }
}
