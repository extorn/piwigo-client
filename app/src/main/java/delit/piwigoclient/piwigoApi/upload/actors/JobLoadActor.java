package delit.piwigoclient.piwigoApi.upload.actors;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import delit.libs.core.util.Logging;
import delit.libs.util.IOUtils;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.piwigoApi.upload.FileUploadDetails;
import delit.piwigoclient.piwigoApi.upload.UploadJob;

import static delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler.getNextMessageId;

public class JobLoadActor extends LocalUploadActor {

    protected static final List<UploadJob> activeUploadJobs = Collections.synchronizedList(new ArrayList<>(1));
    private final static String TAG = "JobLoadActor";

    public JobLoadActor(Context context) {
        super(context);
    }

    public SharedPreferences getPrefs() {
        return PreferenceManager.getDefaultSharedPreferences(getContext());
    }

    public @NonNull UploadJob createUploadJob(ConnectionPreferences.ProfilePreferences connectionPrefs, Map<Uri, Long> filesForUploadAndBytes, CategoryItemStub category, byte uploadedFilePrivacyLevel, long responseHandlerId, boolean isDeleteFilesAfterUpload) {
        long jobId = getNextMessageId();
        UploadJob uploadJob = new UploadJob(connectionPrefs, jobId, responseHandlerId, filesForUploadAndBytes, category, uploadedFilePrivacyLevel, isDeleteFilesAfterUpload);
        synchronized (activeUploadJobs) {
            activeUploadJobs.add(uploadJob);
        }
        return uploadJob;
    }

    public @Nullable UploadJob pushJobConfigurationToFiles(@NonNull UploadJob uploadJob) {
        for(FileUploadDetails fud : uploadJob.getFilesForUpload()) {
            if(uploadJob.isDeleteFilesAfterUpload()) {
                fud.setDeleteAfterUpload(true);
            }
            if(uploadJob.isPlayableMedia(getContext(), fud.getFileUri())) {
                fud.setCompressionNeeded(uploadJob.isCompressPlayableMediaBeforeUpload());
            } else {
                fud.setCompressionNeeded(uploadJob.isCompressPhotosBeforeUpload());
            }
        }
        return uploadJob;
    }

    public static void removeJob(UploadJob job) {
        synchronized (activeUploadJobs) {
            activeUploadJobs.remove(job);
        }
    }

    /**
     * @param isBackgroundJob
     * @param jobId
     * @return Job file if it exists - It does NOT ever create one if missing.
     * @throws IllegalStateException if the job file does not exist.
     */
    public @NonNull DocumentFile getJobStateFile(boolean isBackgroundJob, long jobId) throws IllegalStateException {
        return getJobStateFile(isBackgroundJob, jobId, false);
    }

    protected  @NonNull DocumentFile getJobStateFile(boolean isBackgroundJob, long jobId, boolean createIfMissing) {
        DocumentFile extCacheFolder = DocumentFile.fromFile(Objects.requireNonNull(getContext().getExternalCacheDir()));
        DocumentFile jobsFolder = extCacheFolder.findFile("uploadJobs");
        if (jobsFolder == null) {
            jobsFolder = extCacheFolder.createDirectory("uploadJobs");
        }
        String filename;
        if (isBackgroundJob) {
            filename = jobId + ".state";
        } else {
            filename = "uploadJob.state";
        }
        DocumentFile file = jobsFolder.findFile(filename);
        if (file == null) {
            if (createIfMissing) {
                file = Objects.requireNonNull(jobsFolder.createFile("", filename));
            } else {
                throw new IllegalStateException("unable to find job state file, but not allowed to create it when missing");
            }
        }
        return file;
    }

    protected void deleteAllCompressedAndTemporaryFilesThatExist(@Nullable UploadJob uploadJob) {
        if(uploadJob == null) {
            Logging.log(Log.WARN,TAG, "Unable to delete compressed and temporary files for job that isn't loaded");
            return;
        }
        //FIXME store info about tmp file and deletion needed in the individual detail

        // tmp files are always placed in this folder.
        DocumentFile sharedFiles = IOUtils.getSharedFilesFolder(getContext());

        for (FileUploadDetails fileDetail : uploadJob.getFilesForUploadDetails()) {
            if (fileDetail.hasCompressedFile()) {
                DocumentFile compressedFile = IOUtils.getSingleDocFile(getContext(), fileDetail.getCompressedFileUri());
                if (compressedFile != null && compressedFile.exists()) {
                    if (!compressedFile.delete()) {
                        Logging.log(Log.ERROR, TAG, "Unable to delete compressed file when attempting to delete job state from disk.");
                    }
                }
            }
            try {
                DocumentFile docFile = IOUtils.getTreeLinkedDocFile(getContext(), sharedFiles.getUri(), fileDetail.getFileUri());
                if (docFile != null && docFile.exists()) {
                    if (!docFile.delete()) {
                        Logging.log(Log.ERROR, TAG, "Unable to delete tmp file when attempting to delete job state from disk.");
                    }
                }
            } catch (IllegalStateException e) {
                // ignore. The file is not a tmp folder resident file
            }
        }
    }

    public void saveStateToDisk(UploadJob job) {
        DocumentFile docFile = getJobStateFile(job.isRunInBackground(), job.getJobId(), true);
        IOUtils.saveParcelableToDocumentFile(getContext(), docFile, job);
    }

    private void deleteJobStateFile(DocumentFile f) {
        if (f != null && f.exists()) {
            if (!f.delete()) {
                Log.d(TAG, "Error deleting job state from disk");
            }
        }
    }

    public void deleteStateFromDisk(UploadJob uploadJob, boolean deleteJobConfigFile) {
        if (uploadJob == null) {
            return; // out of sync! Job no longer exists presumably.
        }
        deleteAllCompressedAndTemporaryFilesThatExist(uploadJob);
        //FIXME do I need to delete successfully uploaded files now?
        if (deleteJobConfigFile) {
            DocumentFile stateFile = uploadJob.getLoadedFromFile();
            if (stateFile == null) {
                try {
                    stateFile = getJobStateFile(uploadJob.isRunInBackground(), uploadJob.getJobId());
                } catch (IllegalStateException e) {
                    // to be expected. Ignore.
                }
            }
            deleteJobStateFile(stateFile);
        }
    }

}
