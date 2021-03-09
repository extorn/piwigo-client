package delit.piwigoclient.piwigoApi.upload.actor;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.util.IOUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler;
import delit.piwigoclient.piwigoApi.upload.BackgroundJobConfigurationErrorListener;
import delit.piwigoclient.piwigoApi.upload.BackgroundPiwigoFileUploadResponseListener;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.piwigoApi.upload.actors.JobLoadActor;
import delit.piwigoclient.ui.file.DocumentFileFilter;
import delit.piwigoclient.ui.file.SimpleDocumentFileFilter;
import delit.piwigoclient.ui.preferences.AutoUploadJobConfig;

public class BackgroundJobLoadActor extends JobLoadActor {
    private final static String TAG = "BackgroundJobLoadActor";
    private final BackgroundJobConfigurationErrorListener listener;

    public BackgroundJobLoadActor(Context context, BackgroundJobConfigurationErrorListener listener) {
        super(context);
        this.listener = listener;
    }

    private BackgroundJobConfigurationErrorListener getListener() {
        return listener;
    }

    public static @Nullable UploadJob getActiveBackgroundJob(@NonNull Context context) {
        synchronized (activeUploadJobs) {
            for (UploadJob uploadJob : activeUploadJobs) {
                if (uploadJob.isRunInBackground()) {
                    return uploadJob;
                }
            }
            activeUploadJobs.addAll(loadBackgroundJobsStateFromDisk(context));
            for (UploadJob uploadJob : activeUploadJobs) {
                if (uploadJob.isRunInBackground()) {
                    return uploadJob;
                }
            }
        }
        return null;
    }

    public @Nullable UploadJob getUploadJob(@NonNull AutoUploadJobConfig jobConfig, @NonNull BackgroundPiwigoFileUploadResponseListener jobListener) {
        DocumentFile localFolderToMonitor = jobConfig.getLocalFolderToMonitor(getContext());
        if(localFolderToMonitor == null || !localFolderToMonitor.exists()) {
            getListener().postError(jobConfig.getJobId(), getContext().getString(R.string.ignoring_job_local_folder_not_found));
            return null;
        }
        boolean compressVideos = jobConfig.isCompressVideosBeforeUpload(getContext());
        Set<String> fileExtsToUpload = jobConfig.getFileExtsToUpload(getContext());
        int maxFileSizeMb = jobConfig.getMaxUploadSize(getContext());
        if (fileExtsToUpload == null) {
            Bundle b = new Bundle();
            b.putString("message", "No File extensions selected for upload - nothing can be uploaded. Ignoring job");
            Logging.logAnalyticEvent(getContext(),"uploadError", b);
            getListener().postError(jobConfig.getJobId(), getContext().getString(R.string.ignoring_job_no_file_types_selected_for_upload));
            return null;
        }
        SimpleDocumentFileFilter filter = new SimpleDocumentFileFilter() {
            @Override
            protected boolean nonAcceptOverride(DocumentFile f) {
                return compressVideos && IOUtils.isPlayableMedia(f.getType());
            }
        }.withFileExtIn(fileExtsToUpload).withMaxSizeBytes(maxFileSizeMb * 1024 * 1024);
        List<DocumentFile> matchingFiles = DocumentFileFilter.filterDocumentFiles(localFolderToMonitor.listFiles(), filter);
        if (matchingFiles.isEmpty()) {
            return null;
        }
        matchingFiles = IOUtils.getFilesNotBeingWritten(matchingFiles, 5000); // wait 5 seconds before double checking the file size etc (if its in use, it will have altered)
        if(matchingFiles.isEmpty()) {
            return null;
        }
        Map<Uri,Long> filesToUpload = new HashMap<>(matchingFiles.size());
        for (DocumentFile matchingFile : matchingFiles) {
            filesToUpload.put(matchingFile.getUri(), matchingFile.length());
        }

        CategoryItemStub category = jobConfig.getUploadToAlbum(getContext());
        UploadJob uploadJob = createUploadJob(jobConfig.getConnectionPrefs(getContext(), getPrefs()), filesToUpload, category,
                jobConfig.getUploadedFilePrivacyLevel(getContext()), jobListener.getHandlerId(), jobConfig.isDeleteFilesAfterUpload(getContext()));

        uploadJob.setToRunInBackground();
        uploadJob.setJobConfigId(jobConfig.getJobId());
        if (uploadJob.getConnectionPrefs().isOfflineMode(getPrefs(), getContext())) {
            getListener().postError(jobConfig.getJobId(), getContext().getString(R.string.ignoring_job_connection_profile_set_for_offline_access));
            return null;
        }

        uploadJob.setPlayableMediaCompressionParams(jobConfig.getVideoCompressionParams(getContext()));
        uploadJob.setImageCompressionParams(jobConfig.getImageCompressionParams(getContext()));
        pushJobConfigurationToFiles(uploadJob);
        return uploadJob;
    }

    public static List<UploadJob> loadBackgroundJobsStateFromDisk(@NonNull Context context) {
        File jobsFolder = new File(context.getExternalCacheDir(), "uploadJobs");
        if (!jobsFolder.exists()) {
            if (!jobsFolder.mkdir()) {
                Logging.log(Log.ERROR, TAG, "Unable to create folder to store background upload job status data in");
            }
            return new ArrayList<>();
        }

        List<UploadJob> jobs = new ArrayList<>();
        DocumentFile[] jobFiles = DocumentFile.fromFile(jobsFolder).listFiles();
        for (DocumentFile jobFile : jobFiles) {
            UploadJob job = null;
            try {
                job = IOUtils.readParcelableFromDocumentFile(context.getContentResolver(), jobFile, UploadJob.class);
            } catch (Exception e) {
                Logging.log(Log.WARN, TAG, "Unable to reload job from saved state (will be deleted) - has parcelable changed?");
            }
            if (job != null) {
                job.setLoadedFromFile(jobFile);
                AbstractPiwigoDirectResponseHandler.blockMessageId(job.getJobId());
                if(job.isStatusSubmitted() || job.isStatusRunningNow()) {
                    // this could occur if the user forcibly stopped the app
                    Logging.log(Log.WARN, TAG, "Loading background job from file with illogical status - marking stopped");
                    job.setStatusStopped();
                    new JobLoadActor(context).saveStateToDisk(job);
                }
                jobs.add(job);
            } else {
                if (!jobFile.delete()) {
                    IOUtils.onFileDeleteFailed(TAG, jobFile, "job file");
                }
            }
        }
        synchronized (activeUploadJobs) {
            for (UploadJob activeJob : activeUploadJobs) {
                UploadJob loadedJob;
                for (Iterator<UploadJob> iter = jobs.iterator(); iter.hasNext(); ) {
                    loadedJob = iter.next();
                    if (loadedJob.getJobId() == activeJob.getJobId()) {
                        iter.remove();
                    }
                }
            }
        }
        return jobs;
    }

    public static UploadJob getActiveBackgroundJobByJobConfigId(@NonNull Context context, long jobConfigId) {
        synchronized (activeUploadJobs) {
            for (UploadJob uploadJob : activeUploadJobs) {
                if (uploadJob.isRunInBackground() && uploadJob.getJobConfigId() == jobConfigId) {
                    return uploadJob;
                }
            }
            activeUploadJobs.addAll(loadBackgroundJobsStateFromDisk(context));
            for (UploadJob uploadJob : activeUploadJobs) {
                if (uploadJob.isRunInBackground() && uploadJob.getJobConfigId() == jobConfigId) {
                    return uploadJob;
                }
            }
        }
        return null;
    }

    public static UploadJob getActiveBackgroundJobByJobId(Context context, long jobId) {
        synchronized (activeUploadJobs) {
            for (UploadJob uploadJob : activeUploadJobs) {
                if (uploadJob.isRunInBackground() && uploadJob.getJobId() == jobId) {
                    return uploadJob;
                }
            }
            activeUploadJobs.addAll(loadBackgroundJobsStateFromDisk(context));
            for (UploadJob uploadJob : activeUploadJobs) {
                if (uploadJob.isRunInBackground() && uploadJob.getJobId() == jobId) {
                    return uploadJob;
                }
            }
        }
        return null;
    }

}
