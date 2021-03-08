package delit.piwigoclient.piwigoApi.upload.actors;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import delit.libs.core.util.Logging;
import delit.libs.util.IOUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler;
import delit.piwigoclient.piwigoApi.upload.UploadJob;

public class ForegroundJobLoadActor extends JobLoadActor {
    private static final String TAG = "ForegroundJobLoadActor";

    public ForegroundJobLoadActor(@NonNull Context context) {
        super(context);
    }

    public UploadJob getFirstActiveForegroundJob() {
        synchronized (activeUploadJobs) {
            if (activeUploadJobs.size() == 0) {
                UploadJob job = loadForegroundJobStateFromDisk();
                if(job != null && (job.isStatusSubmitted() || job.isStatusRunningNow())) {
                    // this could occur if the user forcibly stopped the app
                    Logging.log(Log.WARN, TAG, "Loading foreground job from file with illogical status - marking stopped");
                    job.setStatusStopped();
                    new JobLoadActor(getContext()).saveStateToDisk(job);
                }
                return job;
            }
            for (UploadJob job : activeUploadJobs) {
                if (!job.isRunInBackground()) {
                    return job;
                }
            }
        }
        return loadForegroundJobStateFromDisk();
    }

    public @Nullable UploadJob loadForegroundJobStateFromDisk() {

        UploadJob loadedJobState = null;

        try {
            DocumentFile sourceFile = getJobStateFile(false, -1);
            if (sourceFile.exists()) {
                try {
                    loadedJobState = IOUtils.readParcelableFromDocumentFile(getContext().getContentResolver(), sourceFile, UploadJob.class);
                    loadedJobState.setLoadedFromFile(sourceFile);
                } catch (Exception e) {
                    if(BuildConfig.DEBUG) {
                        Logging.log(Log.WARN, TAG, "Unable to reload job from saved state (will be deleted) - has parcelable changed?");
                        //TODO warn the user perhaps? Is this "too much information"?
                    }
                }
            }
            if (loadedJobState != null) {
                AbstractPiwigoDirectResponseHandler.blockMessageId(loadedJobState.getJobId());
            }
        } catch (IllegalStateException e) {
            // job file does not exist.
        }
        return loadedJobState;
    }

    public UploadJob getActiveForegroundJob(long jobId) {
        synchronized (activeUploadJobs) {
            for (UploadJob uploadJob : activeUploadJobs) {
                if (uploadJob.getJobId() == jobId) {
                    return uploadJob;
                }
            }
        }
        UploadJob job = loadForegroundJobStateFromDisk();
        if (job != null && job.getJobId() != jobId) {
            // Is this an error caused by corruption. Delete the old job. We can't use it anyway.
            Logging.log(Log.WARN, TAG, "Job exists on disk, but it doesn't match that expected by the app - deleting");
            deleteStateFromDisk(job,true);
            job = null;
        }
        if (job != null) {
            synchronized (activeUploadJobs) {
                activeUploadJobs.add(job);
            }
        }
        return job;
    }


}
