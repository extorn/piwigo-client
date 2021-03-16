package delit.piwigoclient.piwigoApi.upload;

import android.app.IntentService;
import android.content.Context;

import androidx.annotation.NonNull;

import delit.piwigoclient.database.PiwigoUploadsDatabase;
import delit.piwigoclient.database.PriorUploadRepository;
import delit.piwigoclient.piwigoApi.upload.actors.ActorListener;
import delit.piwigoclient.piwigoApi.upload.actors.PriorUploadsActor;

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

    @Override
    protected void updateListOfPreviouslyUploadedFiles(UploadJob uploadJob, ActorListener actorListener) {
        PriorUploadRepository priorUploadRepository = PriorUploadRepository.getInstance(PiwigoUploadsDatabase.getInstance(getApplication()));
        new PriorUploadsActor(this, uploadJob, actorListener).updatePriorUploadsList(priorUploadRepository);
    }
}
