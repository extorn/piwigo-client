package delit.piwigoclient.piwigoApi.upload;

import android.app.IntentService;
import android.content.Context;

import androidx.annotation.NonNull;

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
