package delit.piwigoclient.piwigoApi.upload;

import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.JobIntentService;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.iptc.IptcDirectory;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import cz.msebera.android.httpclient.HttpStatus;
import delit.libs.core.util.Logging;
import delit.libs.util.CollectionUtils;
import delit.libs.util.IOUtils;
import delit.libs.util.Md5SumUtils;
import delit.libs.util.ObjectUtils;
import delit.libs.util.progress.TaskProgressTracker;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.business.UploadPreferences;
import delit.piwigoclient.model.UploadFileChunk;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.PiwigoGalleryDetails;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.PiwigoUtils;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.StaticCategoryItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumCreateResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetPermissionsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumsAdminResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumRemovePermissionsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.BaseImageGetInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.CommunityGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.CommunityNotifyUploadCompleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageFindExistingImagesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageGetInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImagesListOrphansResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.LoginResponseHandler;
import delit.piwigoclient.piwigoApi.upload.actors.FileChunkProducerThread;
import delit.piwigoclient.piwigoApi.upload.actors.FileChunkUploaderThread;
import delit.piwigoclient.piwigoApi.upload.actors.FileUploadCancelMonitorThread;
import delit.piwigoclient.piwigoApi.upload.actors.ImageCompressor;
import delit.piwigoclient.piwigoApi.upload.actors.StatelessErrorRecordingServerCaller;
import delit.piwigoclient.piwigoApi.upload.actors.UploadFileCompressionListener;
import delit.piwigoclient.piwigoApi.upload.actors.VideoCompressor;
import delit.piwigoclient.piwigoApi.upload.handlers.ImageCheckFilesResponseHandler;
import delit.piwigoclient.piwigoApi.upload.handlers.UploadAlbumCreateResponseHandler;
import delit.piwigoclient.piwigoApi.upload.messages.FileUploadCancelledResponse;
import delit.piwigoclient.piwigoApi.upload.messages.MessageForUserResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoCleanupPostUploadFailedResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoPrepareUploadFailedResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoStartUploadFileResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadFileAddToAlbumFailedResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadFileChunkFailedResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadFileFilesExistAlreadyResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadFileJobCompleteResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadFileLocalErrorResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadProgressUpdateResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadUnexpectedLocalErrorResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoVideoCompressionProgressUpdateResponse;
import delit.piwigoclient.ui.events.CancelFileUploadEvent;

import static delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler.getNextMessageId;
import static org.greenrobot.eventbus.ThreadMode.ASYNC;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 */
public abstract class BasePiwigoUploadService extends JobIntentService {

    private static final String TAG = "BaseUpldSvc";
    private static final List<UploadJob> activeUploadJobs = Collections.synchronizedList(new ArrayList<>(1));
    private static final SecureRandom random = new SecureRandom();
    private UploadJob runningUploadJob = null;
    private final String tag;
    private SharedPreferences prefs;
    private ActionsBroadcastReceiver actionsBroadcastReceiver;
    private StatelessErrorRecordingServerCaller serverCaller;

    public BasePiwigoUploadService(String tag) {
        super(/*tag*/);
        this.tag = tag;
    }

    public static @NonNull
    UploadJob createUploadJob(ConnectionPreferences.ProfilePreferences connectionPrefs, Map<Uri, Long> filesForUploadAndBytes, CategoryItemStub category, byte uploadedFilePrivacyLevel, long responseHandlerId, boolean isDeleteFilesAfterUpload) {
        long jobId = getNextMessageId();
        UploadJob uploadJob = new UploadJob(connectionPrefs, jobId, responseHandlerId, filesForUploadAndBytes, category, uploadedFilePrivacyLevel, isDeleteFilesAfterUpload);
        synchronized (activeUploadJobs) {
            activeUploadJobs.add(uploadJob);
        }
        return uploadJob;
    }

    public static UploadJob getFirstActiveForegroundJob(@NonNull Context context) {
        synchronized (activeUploadJobs) {
            if (activeUploadJobs.size() == 0) {
                return loadForegroundJobStateFromDisk(context);
            }
            for (UploadJob job : activeUploadJobs) {
                if (!job.isRunInBackground()) {
                    return job;
                }
            }
        }
        return loadForegroundJobStateFromDisk(context);
    }

    public static void removeJob(UploadJob job) {
        synchronized (activeUploadJobs) {
            activeUploadJobs.remove(job);
        }
    }

    public static UploadJob getActiveBackgroundJob(Context context) {
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

    public static int getUploadJobsCount(@NonNull Context context) {
        synchronized (activeUploadJobs) {
            if (activeUploadJobs.size() == 0) {
                loadForegroundJobStateFromDisk(context);
                activeUploadJobs.addAll(loadBackgroundJobsStateFromDisk(context));
            }
            return activeUploadJobs.size();
        }
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

    public static UploadJob getActiveBackgroundJobByJobConfigId(Context context, long jobConfigId) {
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

    protected void actionKillService() {
        if (runningUploadJob != null) {
            runningUploadJob.cancelUploadAsap();
        }
    }

    public static UploadJob getActiveForegroundJob(@NonNull Context context, long jobId) {
        synchronized (activeUploadJobs) {
            for (UploadJob uploadJob : activeUploadJobs) {
                if (uploadJob.getJobId() == jobId) {
                    return uploadJob;
                }
            }
        }
        UploadJob job = loadForegroundJobStateFromDisk(context);
        if (job != null && job.getJobId() != jobId) {
            // Is this an error caused by corruption. Delete the old job. We can't use it anyway.
            Logging.log(Log.WARN, TAG, "Job exists on disk, but it doesn't match that expected by the app - deleting");
            deleteStateFromDisk(context, job, true);
            job = null;
        }
        if (job != null) {
            synchronized (activeUploadJobs) {
                activeUploadJobs.add(job);
            }
        }
        return job;
    }

    private static List<UploadJob> loadBackgroundJobsStateFromDisk(Context c) {
        File jobsFolder = new File(c.getExternalCacheDir(), "uploadJobs");
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
                job = IOUtils.readParcelableFromDocumentFile(c.getContentResolver(), jobFile, UploadJob.class);
            } catch (Exception e) {
                Logging.log(Log.WARN, TAG, "Unable to reload job from saved state (will be deleted) - has parcelable changed?");
            }
            if (job != null) {
                job.setLoadedFromFile(jobFile);
                AbstractPiwigoDirectResponseHandler.blockMessageId(job.getJobId());
                jobs.add(job);
            } else {
                if (!jobFile.delete()) {
                    onFileDeleteFailed(TAG, jobFile, "job file");
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

    private static @Nullable
    UploadJob loadForegroundJobStateFromDisk(@NonNull Context c) {

        UploadJob loadedJobState = null;

        try {
            DocumentFile sourceFile = getJobStateFile(c, false, -1);
            if (sourceFile.exists()) {
                try {
                    loadedJobState = IOUtils.readParcelableFromDocumentFile(c.getContentResolver(), sourceFile, UploadJob.class);
                } catch (Exception e) {
                    Logging.log(Log.WARN, TAG, "Unable to reload job from saved state (will be deleted) - has parcelable changed?");
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

    public static void deleteStateFromDisk(Context c, UploadJob thisUploadJob, boolean deleteJobConfigFile) {
        if (thisUploadJob == null) {
            return; // out of sync! Job no longer exists presumably.
        }
        for (Uri file : thisUploadJob.getFilesWithStatus(UploadJob.COMPRESSED)) {
            Uri compressedVersion = thisUploadJob.getCompressedFile(file);
            if (compressedVersion != null) {
                DocumentFile compressedFile = IOUtils.getSingleDocFile(c, compressedVersion);
                if (compressedFile != null && compressedFile.exists()) {
                    if (!compressedFile.delete()) {
                        Logging.log(Log.ERROR, TAG, "Unable to delete compressed file when attempting to delete job state from disk.");
                    }
                }
            }
        }
        if (deleteJobConfigFile) {
            DocumentFile stateFile = thisUploadJob.getLoadedFromFile();
            if (stateFile == null) {
                try {
                    stateFile = getJobStateFile(c, thisUploadJob.isRunInBackground(), thisUploadJob.getJobId());
                } catch (IllegalStateException e) {
                    // to be expected. Ignore.
                }
            }
            deleteJobStateFile(stateFile);
        }
    }

    private static void deleteJobStateFile(DocumentFile f) {
        if (f != null && f.exists()) {
            if (!f.delete()) {
                Log.d(TAG, "Error deleting job state from disk");
            }
        }
    }

    /**
     * @param c
     * @param isBackgroundJob
     * @param jobId
     * @return Job file if it exists - It does NOT ever create one if missing.
     * @throws IllegalStateException if the job file does not exist.
     */
    private static @NonNull
    DocumentFile getJobStateFile(@NonNull Context c, boolean isBackgroundJob, long jobId) throws IllegalStateException {
        return getJobStateFile(c, isBackgroundJob, jobId, false);
    }

    private static @NonNull
    DocumentFile getJobStateFile(@NonNull Context c, boolean isBackgroundJob, long jobId, boolean createIfMissing) {
        DocumentFile extCacheFolder = DocumentFile.fromFile(Objects.requireNonNull(c.getExternalCacheDir()));
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

    public static void onFileDeleteFailed(@NonNull String tag, @NonNull DocumentFile f, @NonNull String fileDesc) {
        if (f.exists()) {
            if (BuildConfig.DEBUG) {
                Log.e(tag, "Unable to delete " + fileDesc + " : " + f.getUri());
            } else {
                Logging.log(Log.WARN, tag, "\"Unable to delete " + fileDesc);
            }
        }
    }

    private void runPostJobCleanup(UploadJob uploadJob) {
        if (uploadJob == null) {
            return; // Do nothing.
        }

        DocumentFile sharedFiles = IOUtils.getSharedFilesFolder(this);
        boolean isDeleteUploadedFiles = uploadJob.isDeleteFilesAfterUpload();
        for (Uri f : uploadJob.getFilesSuccessfullyUploaded()) {
            DocumentFile docFile = null;
            boolean isTmpFile = false;
            if (docFile == null) {
                try {
                    docFile = IOUtils.getTreeLinkedDocFile(this, sharedFiles.getUri(), f);
                    isTmpFile = true;
                } catch (IllegalStateException e) {
                    // ignore.
                }
            }
            if (docFile == null) {
                // this is NOT a temporary folder file.
                docFile = IOUtils.getSingleDocFile(this, f);
            }

            if (docFile != null && docFile.exists()) {
                if (isDeleteUploadedFiles || isTmpFile) {
                    if (!docFile.delete()) {
                        onFileDeleteFailed(tag, docFile, "uploaded file");
                    }
                }
            }
        }

        // record all files uploaded to prevent repeated upload (do this always in case delete fails for a file!
        HashMap<Uri, String> uploadedFileChecksums = uploadJob.getUploadedFilesLocalFileChecksums(this);
        updateListOfPreviouslyUploadedFiles(uploadJob, uploadedFileChecksums);
    }

    protected NotificationCompat.Builder getNotificationBuilder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannelIfNeeded();
        }
        return new NotificationCompat.Builder(this, getDefaultNotificationChannelId());
    }

    abstract protected int getNotificationId();

    abstract protected String getNotificationTitle();

    protected void updateNotificationText(String text, int progress) {
//        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder notificationBuilder = buildNotification(text);
        notificationBuilder.setProgress(100, progress, false);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(getNotificationId(), notificationBuilder.build());
    }

    protected void updateNotificationText(String text, boolean showIndeterminateProgress) {
//        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder notificationBuilder = buildNotification(text);
        if (showIndeterminateProgress) {
            notificationBuilder.setProgress(0, 0, true);
        }
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(getNotificationId(), notificationBuilder.build());
    }

    protected NotificationCompat.Builder buildNotification(String text) {
        NotificationCompat.Builder notificationBuilder = getNotificationBuilder();
//        notificationBuilder.setContentIntent(PendingIntent.getActivity(this, 0, new Intent(), 0));
        notificationBuilder.setContentTitle(getNotificationTitle())
                .setContentText(text);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // this is not a vector graphic
            notificationBuilder.setSmallIcon(R.drawable.ic_file_upload_black);
            notificationBuilder.setCategory("service");
        } else {
            notificationBuilder.setSmallIcon(R.drawable.ic_file_upload_black_24dp);
            notificationBuilder.setCategory(NotificationCompat.CATEGORY_SERVICE);
        }
        notificationBuilder.setAutoCancel(true);
//        .setTicker(getText(R.string.ticker_text))
        return notificationBuilder;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createNotificationChannelIfNeeded() {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        NotificationChannel channel = notificationManager.getNotificationChannel(getDefaultNotificationChannelId());
        int importance = NotificationManager.IMPORTANCE_LOW; // no noise for low.
        if (channel == null || channel.getImportance() != importance) {
            String name = getString(R.string.app_name);
            channel = new NotificationChannel(getDefaultNotificationChannelId(), name, importance);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private String getDefaultNotificationChannelId() {
        return getString(R.string.app_name) + "_UploadService";
    }

    protected void doBeforeWork(@NonNull Intent intent) {
        NotificationCompat.Builder notificationBuilder = buildNotification(getString(R.string.notification_message_upload_service));
        notificationBuilder.setProgress(0, 0, true);
        startForeground(getNotificationId(), notificationBuilder.build());
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
    }

    public SharedPreferences getPrefs() {
        return prefs;
    }

    protected abstract void doWork(@NonNull Intent intent);

    protected abstract void updateListOfPreviouslyUploadedFiles(UploadJob uploadJob, HashMap<Uri, String> uploadedFileChecksums);

    private void recordAndPostNewResponse(UploadJob thisUploadJob, PiwigoResponseBufferingHandler.Response response) {
        if (!(response instanceof PiwigoUploadProgressUpdateResponse
                || response instanceof PiwigoVideoCompressionProgressUpdateResponse
                || response instanceof PiwigoStartUploadFileResponse
                || response instanceof PiwigoUploadFileFilesExistAlreadyResponse
                || response instanceof PiwigoUploadFileJobCompleteResponse)) {
            if (response instanceof PiwigoPrepareUploadFailedResponse) {
                PiwigoResponseBufferingHandler.Response error = ((PiwigoPrepareUploadFailedResponse) response).getError();
                String errorMsg = null;
                if (error instanceof PiwigoResponseBufferingHandler.CustomErrorResponse) {
                    errorMsg = ((PiwigoResponseBufferingHandler.CustomErrorResponse) error).getErrorMessage();
                }
                if (errorMsg != null) {
                    thisUploadJob.recordError("PiwigoPrepareUpload:Failed : " + errorMsg);
                } else {
                    thisUploadJob.recordError("PiwigoPrepareUpload:Failed");
                }

            }
            if (response instanceof PiwigoCleanupPostUploadFailedResponse) {
                thisUploadJob.recordError("PiwigoCleanupPostUpload:Failed");
            }
            if (response instanceof PiwigoUploadFileAddToAlbumFailedResponse) {
                thisUploadJob.recordErrorLinkedToFile(((PiwigoUploadFileAddToAlbumFailedResponse) response).getFileForUpload(), "PiwigoUploadFileAddToAlbum:Failed : " + ((PiwigoUploadFileAddToAlbumFailedResponse) response).getFileForUpload().getPath());
            }
            if (response instanceof PiwigoUploadFileChunkFailedResponse) {
                thisUploadJob.recordErrorLinkedToFile(((PiwigoUploadFileChunkFailedResponse) response).getFileForUpload(), "PiwigoUploadFileChunk:Failed : " + ((PiwigoUploadFileChunkFailedResponse) response).getFileForUpload().toString());
            }
            if (response instanceof PiwigoUploadFileLocalErrorResponse) {
                String error = ((PiwigoUploadFileLocalErrorResponse) response).getError().getMessage();
                thisUploadJob.recordErrorLinkedToFile(((PiwigoUploadFileLocalErrorResponse) response).getFileForUpload(), "PiwigoUploadFileLocalError: " + error);
            } else if (response instanceof PiwigoUploadUnexpectedLocalErrorResponse) {
                // need else as this is extended by the previous exception
                String error = ((PiwigoUploadUnexpectedLocalErrorResponse) response).getError().getMessage();
                thisUploadJob.recordError("PiwigoUploadUnexpectedLocalError: " + error);
            }
        }
        postNewResponse(thisUploadJob.getJobId(), response);
    }


    protected void setRunningUploadJob(UploadJob thisUploadJob) {
        runningUploadJob = thisUploadJob;
    }

    protected void clearRunningUploadJob() {
        runningUploadJob = null;
    }


    public abstract void postNewResponse(long jobId, PiwigoResponseBufferingHandler.Response response);

    protected final void runJob(long jobId) {
        try {
            EventBus.getDefault().register(this);
            serverCaller = new StatelessErrorRecordingServerCaller(this);
            UploadJob thisUploadJob = getActiveForegroundJob(this, jobId);
            runJob(thisUploadJob, null, true);
        } finally {
            EventBus.getDefault().unregister(this);
        }
    }

    protected void runJob(@NonNull UploadJob thisUploadJob, JobUploadListener listener, boolean deleteJobConfigFileOnSuccess) {

        try {
            setRunningUploadJob(thisUploadJob);

            if (thisUploadJob == null) {
                Logging.log(Log.WARN, tag, "Upload job could not be located immediately after creating it - weird!");
                return;
            }
            TaskProgressTracker overallJobProgressTracker = thisUploadJob.getProgressTrackerForJob(this);
            try {
                overallJobProgressTracker.setWorkDone(0);
            } catch(IllegalStateException e) {
                // try this as an option until I can reproduce the error.
                thisUploadJob.resetProgressTrackers();
                Logging.log(Log.ERROR, TAG, "UploadJobProgressTrackerIllegalStateErr");
                Logging.recordException(e);
                Logging.logAnalyticEvent(this, "UploadJobProgressTrackerIllegalStateErr");
            }


            int maxChunkUploadAutoRetries = UploadPreferences.getUploadChunkMaxRetries(this, prefs);

            thisUploadJob.setRunning(true);
            thisUploadJob.setSubmitted(false);

            try {
                PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(thisUploadJob.getConnectionPrefs());
                if (sessionDetails == null) {
                    LoginResponseHandler handler = new LoginResponseHandler();
                    serverCaller.invokeWithRetries(thisUploadJob, handler, 2);
                    if (handler.isSuccess()) {
                        sessionDetails = PiwigoSessionDetails.getInstance(thisUploadJob.getConnectionPrefs());
                    } else {
                        recordAndPostNewResponse(thisUploadJob, new PiwigoPrepareUploadFailedResponse(getNextMessageId(), handler.getResponse()));
                        throw new JobUnableToContinueException();
                    }
                    if (sessionDetails == null) {
                        Bundle b = new Bundle();
                        b.putString("location", "upload - get login");
                        Logging.logAnalyticEvent(this, "SessionNull", b);
                        recordAndPostNewResponse(thisUploadJob, new PiwigoPrepareUploadFailedResponse(getNextMessageId(), handler.getResponse()));
                        throw new JobUnableToContinueException();
                    }
                }
                ArrayList<CategoryItemStub> availableAlbumsOnServer = retrieveListOfAlbumsOnServer(thisUploadJob, sessionDetails);
                if (availableAlbumsOnServer == null) {
                    //try again. This is really important.
                    availableAlbumsOnServer = retrieveListOfAlbumsOnServer(thisUploadJob, sessionDetails);
                }
                if (availableAlbumsOnServer == null) {
                    // This is fatal really. It is necessary for a resilient upload. Stop the upload.
                    throw new JobUnableToContinueException();
                }

                if (thisUploadJob.getTemporaryUploadAlbum() > 0) {
                    // check it still exists (ensure one is created again if not) - could have been deleted by a user manually.
                    boolean tempAlbumExists = PiwigoUtils.containsItemWithId(availableAlbumsOnServer, thisUploadJob.getTemporaryUploadAlbum());
                    if (!tempAlbumExists) {
                        // allow a new one to be created and tracked
                        thisUploadJob.setTemporaryUploadAlbum(-1);
                    }
                }

                saveStateToDisk(thisUploadJob);


                Map<Uri, Md5SumUtils.Md5SumException> failures = thisUploadJob.calculateChecksums(this);
                if (!failures.isEmpty()) {
                    for (Map.Entry<Uri, Md5SumUtils.Md5SumException> entry : failures.entrySet()) {
                        // mark the upload as cancelled
                        thisUploadJob.setErrorFlag(entry.getKey());
                        // notify listeners of the error
                        recordAndPostNewResponse(thisUploadJob, new PiwigoUploadFileLocalErrorResponse(getNextMessageId(), entry.getKey(), true, entry.getValue()));
                    }
                }

                if (thisUploadJob.isRunInBackground() && listener != null) {
                    listener.onJobReadyToUpload(this, thisUploadJob);
                }

                // is name or md5sum used for uniqueness on this server?
                boolean nameUnique = isUseFilenamesOverMd5ChecksumForUniqueness(thisUploadJob);
                Collection<String> uniqueIdsList;
                if (nameUnique) {
                    uniqueIdsList = thisUploadJob.getFileToFilenamesMap(this).values();
                } else {
                    uniqueIdsList = thisUploadJob.getFileChecksums().values();
                }


                if (thisUploadJob.hasFilesForUpload()) {
                    // remove any files that already exist on the server from the upload.
                    ImageFindExistingImagesResponseHandler imageFindExistingHandler = new ImageFindExistingImagesResponseHandler(uniqueIdsList, nameUnique);
                    serverCaller.invokeWithRetries(thisUploadJob, imageFindExistingHandler, 2);
                    if (imageFindExistingHandler.isSuccess()) {
                        ArrayList<Long> orphans;
                        if (PiwigoSessionDetails.isAdminUser(thisUploadJob.getConnectionPrefs())) {
                            orphans = getOrphanImagesOnServer(thisUploadJob);
                            if (orphans == null) {
                                // there has been an error which is reported within the getOrphanImagesOnServer method.
                                throw new JobUnableToContinueException();
                            }
                        } else {
                            orphans = new ArrayList<>();
                        }
                        if (imageFindExistingHandler.getResponse() instanceof ImageFindExistingImagesResponseHandler.PiwigoFindExistingImagesResponse) {
                            processFindPreexistingImagesResponse(thisUploadJob, (ImageFindExistingImagesResponseHandler.PiwigoFindExistingImagesResponse) imageFindExistingHandler.getResponse(), orphans);
                        } else {
                            // this is bizarre - a failure was recorded as a success!
                            // notify the listener of the final error we received from the server
                            recordAndPostNewResponse(thisUploadJob, new PiwigoPrepareUploadFailedResponse(getNextMessageId(), imageFindExistingHandler.getResponse()));
                        }

                    }
                    if (!imageFindExistingHandler.isSuccess()) {
                        // notify the listener of the final error we received from the server
                        recordAndPostNewResponse(thisUploadJob, new PiwigoPrepareUploadFailedResponse(getNextMessageId(), imageFindExistingHandler.getResponse()));
                        throw new JobUnableToContinueException();
                    }

                    boolean useTempFolder = !PiwigoSessionDetails.isUseCommunityPlugin(thisUploadJob.getConnectionPrefs());

                    if(!thisUploadJob.getFilesNotYetUploaded().isEmpty()) {
                        // create a secure folder to upload to if required
                        if (useTempFolder && !createTemporaryUploadAlbum(thisUploadJob)) {
                            throw new JobUnableToContinueException();
                        }
                    }
                }

                overallJobProgressTracker.incrementWorkDone(UploadJob.WORK_DIVISION_POST_CHECKED_FOR_EXISTING_FILES);

                saveStateToDisk(thisUploadJob);

                if (!thisUploadJob.isCancelUploadAsap()) {
                    if (thisUploadJob.hasFilesForUpload()) {
                        TaskProgressTracker overallDataCompressAndUploadTracker = thisUploadJob.getTaskProgressTrackerForOverallCompressionAndUploadOfData();
                        try {
                            doUploadFilesInJob(maxChunkUploadAutoRetries, thisUploadJob, availableAlbumsOnServer);
                        } finally {
                            try {
                                overallDataCompressAndUploadTracker.markComplete();
                            } catch (IllegalStateException e) {
                                // Will occur if the upload chunks task didn't complete.
                                recordAndPostNewResponse(thisUploadJob, new PiwigoUploadUnexpectedLocalErrorResponse(thisUploadJob.getJobId(), new Exception("Upload of all chunks for file did not complete successfully")));
                                Logging.recordException(e);
                            }
                        }
                    }
                }

                if (!thisUploadJob.isCancelUploadAsap()) {
                    sessionDetails = PiwigoSessionDetails.getInstance(thisUploadJob.getConnectionPrefs());
                    if (sessionDetails.isUseCommunityPlugin() && sessionDetails.isCommunityApiAvailable()) {
                        Set<Long> ids = thisUploadJob.getIdsOfResourcesForFilesSuccessfullyUploaded();
                        CommunityNotifyUploadCompleteResponseHandler hndlr = new CommunityNotifyUploadCompleteResponseHandler(ids, thisUploadJob.getUploadToCategory());
                        if (sessionDetails.isMethodAvailable(hndlr.getPiwigoMethod())) {
                            serverCaller.invokeWithRetries(thisUploadJob, hndlr, 2);
                        }
                    }
                }

                overallJobProgressTracker.incrementWorkDone(UploadJob.WORK_DIVISION_POST_UPLOAD_CALLS);

                if (!thisUploadJob.isCancelUploadAsap()) {
                    ArrayList<Uri> filesToUpload = thisUploadJob.getFilesNotYetUploaded();
                    ArrayList<Uri> filesNoLongerAvailable = thisUploadJob.getListOfFilesNoLongerUnavailable(this, filesToUpload);
                    boolean noFilesToUpload = filesToUpload.isEmpty() || filesToUpload.size() == filesNoLongerAvailable.size();
                    if (noFilesToUpload && thisUploadJob.getTemporaryUploadAlbum() > 0) {
                        boolean success = deleteTemporaryUploadAlbum(thisUploadJob);
                        if (!success) {
                            throw new JobUnableToContinueException();
                        }
                    }
                }

                overallJobProgressTracker.incrementWorkDone(UploadJob.WORK_DIVISION_DELETE_TEMP_FOLDER);

                thisUploadJob.setFinished();
            } catch(JobUnableToContinueException e) {
                Logging.log(Log.DEBUG, TAG, "Stopping job. Unable to continue. Check recorded errors for reason");
            } catch (RuntimeException e) {
                recordAndPostNewResponse(thisUploadJob, new PiwigoUploadUnexpectedLocalErrorResponse(getNextMessageId(), e));
                Logging.log(Log.ERROR, TAG, "An unexpected Runtime error stopped upload job");
                Logging.recordException(e);
            } finally {
                thisUploadJob.setRunning(false);
                thisUploadJob.clearCancelUploadAsapFlag();

                updateNotificationProgressText(thisUploadJob.getOverallUploadProgressInt());

                if (!thisUploadJob.hasJobCompletedAllActionsSuccessfully()) {
                    saveStateToDisk(thisUploadJob);
                } else {
                    deleteStateFromDisk(this, thisUploadJob, deleteJobConfigFileOnSuccess);
                }
            }

            try {
                runPostJobCleanup(thisUploadJob);
            } catch (RuntimeException e) {
                recordAndPostNewResponse(thisUploadJob, new PiwigoUploadUnexpectedLocalErrorResponse(getNextMessageId(), e));
                Logging.log(Log.ERROR, TAG, "An unexpected Runtime error stopped upload job");
                Logging.recordException(e);
            } finally {
                if (thisUploadJob.hasJobCompletedAllActionsSuccessfully()) {
                    overallJobProgressTracker.markComplete();
                }
                recordAndPostNewResponse(thisUploadJob, new PiwigoUploadFileJobCompleteResponse(getNextMessageId(), thisUploadJob));
                PiwigoResponseBufferingHandler.getDefault().deRegisterResponseHandler(thisUploadJob.getJobId());
                AbstractPiwigoDirectResponseHandler.unblockMessageId(thisUploadJob.getJobId());
            }
        } finally {
            clearRunningUploadJob();
        }
    }

    private boolean isUseFilenamesOverMd5ChecksumForUniqueness(UploadJob thisUploadJob) {
        String uniqueResourceKey = thisUploadJob.getConnectionPrefs().getPiwigoUniqueResourceKey(prefs, this);
        return "name".equals(uniqueResourceKey);
    }


    private ArrayList<CategoryItemStub> retrieveListOfAlbumsOnServer(UploadJob thisUploadJob, PiwigoSessionDetails sessionDetails) {
        if (sessionDetails.isAdminUser()) {
            AlbumGetSubAlbumsAdminResponseHandler handler = new AlbumGetSubAlbumsAdminResponseHandler();
            serverCaller.invokeWithRetries(thisUploadJob, handler, 2);
            if (handler.isSuccess()) {
                AlbumGetSubAlbumsAdminResponseHandler.PiwigoGetSubAlbumsAdminResponse rsp = (AlbumGetSubAlbumsAdminResponseHandler.PiwigoGetSubAlbumsAdminResponse) handler.getResponse();
                return rsp.getAdminList().flattenTree();
            } else {
                recordAndPostNewResponse(thisUploadJob, new PiwigoPrepareUploadFailedResponse(getNextMessageId(), handler.getResponse()));
            }
        } else if (sessionDetails.isUseCommunityPlugin() && sessionDetails.isCommunityApiAvailable()) {
            final boolean recursive = true;
            CommunityGetSubAlbumNamesResponseHandler handler = new CommunityGetSubAlbumNamesResponseHandler(StaticCategoryItem.ROOT_ALBUM.getId()/*currentGallery.id*/, recursive);
            serverCaller.invokeWithRetries(thisUploadJob, handler, 2);
            if (handler.isSuccess()) {
                CommunityGetSubAlbumNamesResponseHandler.PiwigoCommunityGetSubAlbumNamesResponse rsp = (CommunityGetSubAlbumNamesResponseHandler.PiwigoCommunityGetSubAlbumNamesResponse) handler.getResponse();
                return rsp.getAlbumNames();
            } else {
                recordAndPostNewResponse(thisUploadJob, new PiwigoPrepareUploadFailedResponse(getNextMessageId(), handler.getResponse()));
            }
        } else {
            AlbumGetSubAlbumNamesResponseHandler handler = new AlbumGetSubAlbumNamesResponseHandler(StaticCategoryItem.ROOT_ALBUM.getId()/*currentGallery.id*/, true);
            serverCaller.invokeWithRetries(thisUploadJob, handler, 2);
            if (handler.isSuccess()) {
                AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse rsp = (AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) handler.getResponse();
                return rsp.getAlbumNames();
            } else {
                recordAndPostNewResponse(thisUploadJob, new PiwigoPrepareUploadFailedResponse(getNextMessageId(), handler.getResponse()));
            }
        }
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        actionsBroadcastReceiver = buildActionBroadcastReceiver();
        registerReceiver(actionsBroadcastReceiver, actionsBroadcastReceiver.getFilter());
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(actionsBroadcastReceiver);
        super.onDestroy();
    }

    public void saveStateToDisk(@NonNull UploadJob thisUploadJob) {
        saveStateToDisk(this, thisUploadJob);
    }

    public static void saveStateToDisk(@NonNull Context context, @NonNull UploadJob thisUploadJob) {
        IOUtils.saveParcelableToDocumentFile(context, getJobStateFile(context, thisUploadJob.isRunInBackground(), thisUploadJob.getJobId(), true), thisUploadJob);
    }

    protected abstract ActionsBroadcastReceiver buildActionBroadcastReceiver();

    private boolean deleteTemporaryUploadAlbum(@NonNull UploadJob thisUploadJob) {
        ArrayList<Uri> filesToUpload = thisUploadJob.getFilesNotYetUploaded();
        ArrayList<Uri> filesNoLongerAvailable = thisUploadJob.getListOfFilesNoLongerUnavailable(this, filesToUpload);
        boolean noFilesToUpload = filesToUpload.isEmpty() || filesToUpload.size() == filesNoLongerAvailable.size();
        if (noFilesToUpload && thisUploadJob.getTemporaryUploadAlbum() < 0) {
            throw new IllegalStateException("Cannot delete upload album when job is still incomplete");
        }
        // all files were uploaded successfully.
        //delete temporary hidden album
        AlbumDeleteResponseHandler albumDelHandler = new AlbumDeleteResponseHandler(thisUploadJob.getTemporaryUploadAlbum(), true);
        serverCaller.invokeWithRetries(thisUploadJob, albumDelHandler, 2);
        if (!albumDelHandler.isSuccess()) {
            // notify the listener of the final error we received from the server
            recordAndPostNewResponse(thisUploadJob, new PiwigoCleanupPostUploadFailedResponse(getNextMessageId(), albumDelHandler.getResponse()));
        } else {
            thisUploadJob.setTemporaryUploadAlbum(-1);
        }
        return albumDelHandler.isSuccess();
    }

    private boolean createTemporaryUploadAlbum(UploadJob thisUploadJob) {

        long uploadAlbumId = thisUploadJob.getTemporaryUploadAlbum();

        if (uploadAlbumId < 0) {
            // create temporary hidden album
            UploadAlbumCreateResponseHandler albumGenHandler = new UploadAlbumCreateResponseHandler(thisUploadJob.getUploadToCategoryName(), thisUploadJob.getUploadToCategory());
            serverCaller.invokeWithRetries(thisUploadJob, albumGenHandler, 2);
            if (albumGenHandler.isSuccess()) {
                PiwigoGalleryDetails albumDetails = ((AlbumCreateResponseHandler.PiwigoAlbumCreatedResponse) albumGenHandler.getResponse()).getAlbumDetails();
                PiwigoSessionDetails currentUserDetails = PiwigoSessionDetails.getInstance(thisUploadJob.getConnectionPrefs());
                CategoryItem catItem = new CategoryItem(albumDetails.asCategoryItemStub());
                AlbumGetPermissionsResponseHandler permissionsResponseHandler = new AlbumGetPermissionsResponseHandler(catItem);
                serverCaller.invokeWithRetries(thisUploadJob, permissionsResponseHandler, 2);
                if(permissionsResponseHandler.isSuccess()) {
                    HashSet<Long> removeGroups = CollectionUtils.getSetFromArray(catItem.getGroups());
                    HashSet<Long> removeUsers = CollectionUtils.getSetFromArray(catItem.getUsers());
                    removeUsers.retainAll(Collections.singleton(currentUserDetails.getUserId()));
                    AlbumRemovePermissionsResponseHandler removePermissionsResponseHandler = new AlbumRemovePermissionsResponseHandler(albumDetails, removeGroups, removeUsers);
                    serverCaller.invokeWithRetries(thisUploadJob, removePermissionsResponseHandler, 2);
                    if(!removePermissionsResponseHandler.isSuccess()) {
                        recordAndPostNewResponse(thisUploadJob, new PiwigoPrepareUploadFailedResponse(getNextMessageId(), removePermissionsResponseHandler.getResponse()));
                    }
                } else {
                    recordAndPostNewResponse(thisUploadJob, new PiwigoPrepareUploadFailedResponse(getNextMessageId(), permissionsResponseHandler.getResponse()));
                }

                uploadAlbumId = albumDetails.getGalleryId();
            }
            if (!albumGenHandler.isSuccess() || uploadAlbumId < 0) {
                // notify the listener of the final error we received from the server
                recordAndPostNewResponse(thisUploadJob, new PiwigoPrepareUploadFailedResponse(getNextMessageId(), albumGenHandler.getResponse()));
                return false;
            } else {
                thisUploadJob.setTemporaryUploadAlbum(uploadAlbumId);
            }
        }
        return true;
    }

    private void processFindPreexistingImagesResponse(UploadJob thisUploadJob, ImageFindExistingImagesResponseHandler.PiwigoFindExistingImagesResponse response, List<Long> orphans) {
        HashMap<String, Long> preexistingItemsMap = response.getExistingImages();
        ArrayList<Uri> filesExistingOnServerAlready = new ArrayList<>();
        HashMap<Uri, Long> resourcesToRetrieve = new HashMap<>();

        // is name or md5sum used for uniqueness on this server?
        boolean nameUnique = "name".equals(prefs.getString(getString(R.string.preference_gallery_unique_id_key), getResources().getString(R.string.preference_gallery_unique_id_default)));
        Map<Uri, String> uniqueIdsSet;
        if (nameUnique) {
            uniqueIdsSet = thisUploadJob.getFileToFilenamesMap(this);
        } else {
            uniqueIdsSet = thisUploadJob.getFileChecksums();
        }

        for (Map.Entry<Uri, String> fileCheckSumEntry : uniqueIdsSet.entrySet()) {
            String uploadedFileUid = fileCheckSumEntry.getValue(); // usually MD5Sum (less chance of collision).

            if (preexistingItemsMap.containsKey(uploadedFileUid)) {
                Uri fileFoundOnServer = fileCheckSumEntry.getKey();
                Long serverResourceId = preexistingItemsMap.get(uploadedFileUid);

                // theoretically we needn't retrieve the item again if we already have it (not null), but it may have been changed by other means...
//                ResourceItem resourceItem = thisUploadJob.getUploadedFileResource(fileFoundOnServer);

                if (thisUploadJob.isFileUploadComplete(fileFoundOnServer)) {
                    resourcesToRetrieve.put(fileFoundOnServer, serverResourceId);
                }
                if (thisUploadJob.needsVerification(fileFoundOnServer) || thisUploadJob.isUploadingData(fileFoundOnServer)) {
                    thisUploadJob.markFileAsVerified(fileFoundOnServer);
                } else if (thisUploadJob.needsConfiguration(fileFoundOnServer)) {
                    resourcesToRetrieve.put(fileFoundOnServer, serverResourceId);
                } else {
                    // mark this file as needing configuration (probably uploaded by ?someone else? or a different upload mechanism anyway)
                    thisUploadJob.markFileAsVerified(fileFoundOnServer);
                    filesExistingOnServerAlready.add(fileFoundOnServer);
                    resourcesToRetrieve.put(fileFoundOnServer, serverResourceId);
                }
            }
        }

        if (filesExistingOnServerAlready.size() > 0) {
//            thisUploadJob.getFilesForUpload().removeAll(filesExistingOnServerAlready);
            recordAndPostNewResponse(thisUploadJob, new PiwigoUploadFileFilesExistAlreadyResponse(getNextMessageId(), filesExistingOnServerAlready));
        }

        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(thisUploadJob.getConnectionPrefs());


        for (Map.Entry<Uri, Long> entry : resourcesToRetrieve.entrySet()) {

            long imageId = entry.getValue();
            if (orphans.contains(imageId)) {
                ResourceItem item = new ResourceItem(imageId, null, null, null, null, null);
                item.setFileChecksum(uniqueIdsSet.get(entry.getKey()));
                item.setLinkedAlbums(new HashSet<>(1));
                thisUploadJob.addFileUploaded(entry.getKey(), item);
            } else {
                ImageGetInfoResponseHandler<ResourceItem> getImageInfoHandler = new ImageGetInfoResponseHandler<>(new ResourceItem(imageId, null, null, null, null, null));
                int allowedAttempts = 2;
                boolean success = false;
                while (!success && allowedAttempts > 0) {
                    allowedAttempts--;
                    // this is blocking
                    getImageInfoHandler.invokeAndWait(this, thisUploadJob.getConnectionPrefs());
                    if (getImageInfoHandler.isSuccess()) {
                        success = true;
                        BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse<?> rsp = (BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse<?>) getImageInfoHandler.getResponse();
                        thisUploadJob.addFileUploaded(entry.getKey(), rsp.getResource());
                    } else if (sessionDetails.isUseCommunityPlugin() && getImageInfoHandler.getResponse() instanceof PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) {
                        PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse rsp = (PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) getImageInfoHandler.getResponse();
                        if (rsp.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                            success = true; // image is on the server, but not yet approved.
                            thisUploadJob.addFileUploaded(entry.getKey(), null);
                            recordAndPostNewResponse(thisUploadJob, new PiwigoUploadProgressUpdateResponse(getNextMessageId(), entry.getKey(), thisUploadJob.getUploadProgress(entry.getKey())));
                        } else {
                            StatelessErrorRecordingServerCaller.logServerCallError(this, getImageInfoHandler, thisUploadJob.getConnectionPrefs());
                        }
                    }
                }
            }
        }
    }

    private ArrayList<Long> getOrphanImagesOnServer(UploadJob thisUploadJob) {

        ImagesListOrphansResponseHandler orphanListHandler = new ImagesListOrphansResponseHandler(0, 100);
        ArrayList<Long> orphans;

        if (orphanListHandler.isMethodAvailable(this, thisUploadJob.getConnectionPrefs())) {
            orphans = null;
            serverCaller.invokeWithRetries(thisUploadJob, orphanListHandler, 2);
            if (orphanListHandler.isSuccess()) {
                ImagesListOrphansResponseHandler.PiwigoGetOrphansResponse resp = (ImagesListOrphansResponseHandler.PiwigoGetOrphansResponse) orphanListHandler.getResponse();
                if (resp.getTotalCount() > resp.getResources().size()) {
                    recordAndPostNewResponse(thisUploadJob, new PiwigoPrepareUploadFailedResponse(getNextMessageId(), new PiwigoResponseBufferingHandler.CustomErrorResponse(thisUploadJob.getJobId(), getString(R.string.upload_error_too_many_orphaned_files_exist_on_server))));
                    return null;
                } else {
                    orphans = resp.getResources();
                }
            } else {
                recordAndPostNewResponse(thisUploadJob, new PiwigoPrepareUploadFailedResponse(getNextMessageId(), new PiwigoResponseBufferingHandler.CustomErrorResponse(thisUploadJob.getJobId(), getString(R.string.upload_error_orphaned_file_retrieval_failed))));
            }
        } else {
            orphans = new ArrayList<>(0);
            thisUploadJob.recordError(getString(R.string.upload_error_orphaned_file_retrieval_unavailable));
        }

        return orphans;
    }

    private void notifyListenersOfCustomErrorUploadingFile(UploadJob thisUploadJob, Uri fileBeingUploaded, boolean itemUploadCancelled, String errorMessage) {
        long jobId = thisUploadJob.getJobId();
        PiwigoResponseBufferingHandler.CustomErrorResponse errorResponse = new PiwigoResponseBufferingHandler.CustomErrorResponse(jobId, errorMessage);
        PiwigoUploadFileAddToAlbumFailedResponse r1 = new PiwigoUploadFileAddToAlbumFailedResponse(getNextMessageId(), fileBeingUploaded, itemUploadCancelled, errorResponse);
        postNewResponse(jobId, r1);
    }

    private void doUploadFilesInJob(int maxChunkUploadAutoRetries, UploadJob thisUploadJob, ArrayList<CategoryItemStub> availableAlbumsOnServer) {

        long jobId = thisUploadJob.getJobId();

        Set<Long> allServerAlbumIds = PiwigoUtils.toSetOfIds(availableAlbumsOnServer);

        for (Uri fileForUploadUri : thisUploadJob.getFilesForUpload()) {
            boolean isHaveUploadedCompressedFile = false;

            if(!thisUploadJob.isFileUploadStillWanted(fileForUploadUri)) {
                doHandleUserCancelledUpload(thisUploadJob, fileForUploadUri);
            }

            if (thisUploadJob.isLocalFileNeededForUpload(fileForUploadUri)) {
                try {
                    if (!IOUtils.exists(this, fileForUploadUri)) {

                        thisUploadJob.setErrorFlag(fileForUploadUri);
                        // notify the listener of the final error we received from the server
                        String filename = IOUtils.getFilename(this, fileForUploadUri);
                        String errorMsg = getString(R.string.alert_error_upload_file_no_longer_available_message_pattern, filename, fileForUploadUri.getPath());
                        notifyListenersOfCustomErrorUploadingFile(thisUploadJob, fileForUploadUri, true, errorMsg);
                    }
                } catch (SecurityException e) {
                    thisUploadJob.setErrorFlag(fileForUploadUri);
                    recordAndPostNewResponse(thisUploadJob, new PiwigoUploadFileLocalErrorResponse(getNextMessageId(), fileForUploadUri, true, e));
                }
            }



            if (thisUploadJob.needsUpload(fileForUploadUri)) {
                doAnyCompressionNeeded(thisUploadJob, fileForUploadUri);
            }

            if (thisUploadJob.isCancelUploadAsap()) {
                return;
            }

            if (thisUploadJob.needsUpload(fileForUploadUri)) {
                boolean uploadingCompressedFile = thisUploadJob.isUploadingData(fileForUploadUri) && thisUploadJob.getCompressedFile(fileForUploadUri) != null;
                if (thisUploadJob.isFileCompressed(fileForUploadUri) || uploadingCompressedFile) {
                    doUploadFileData(thisUploadJob, fileForUploadUri, thisUploadJob.getCompressedFile(fileForUploadUri), maxChunkUploadAutoRetries);
                    isHaveUploadedCompressedFile = thisUploadJob.needsVerification(fileForUploadUri); // if the file uploaded all chunks
                } else {
                    doUploadFileData(thisUploadJob, fileForUploadUri, fileForUploadUri, maxChunkUploadAutoRetries);
                }

            }

            if (thisUploadJob.isCancelUploadAsap()) {
                return;
            }

            saveStateToDisk(thisUploadJob);

            if (thisUploadJob.needsVerification(fileForUploadUri)) {
                doVerificationOfUploadedFileData(thisUploadJob, fileForUploadUri);
                isHaveUploadedCompressedFile &= thisUploadJob.isUploadedFileVerified(fileForUploadUri);
            }

            if (thisUploadJob.isCancelUploadAsap()) {
                return;
            }

            saveStateToDisk(thisUploadJob);

            if (isHaveUploadedCompressedFile && thisUploadJob.isUploadVerified(fileForUploadUri)) {
                // delete the temporarily created compressed file.
                Uri compressedVideoFileUri = thisUploadJob.getCompressedFile(fileForUploadUri);

                if (!IOUtils.delete(this, compressedVideoFileUri)) {
                    DocumentFile compressedVideoFile = Objects.requireNonNull(IOUtils.getSingleDocFile(this, compressedVideoFileUri));
                    onFileDeleteFailed(tag, compressedVideoFile, "compressed video - post upload");
                }
            }

            if (thisUploadJob.needsConfiguration(fileForUploadUri)) {
                doConfigurationOfUploadedFileDetails(thisUploadJob, jobId, fileForUploadUri, allServerAlbumIds);
            }

            saveStateToDisk(thisUploadJob);

            if (thisUploadJob.isCancelUploadAsap()) {
                return;
            }

            // Once added to album its too late the cancel the upload.
//            if (!thisUploadJob.isFileUploadStillWanted(fileForUpload)) {
//                thisUploadJob.markFileAsNeedsDelete(fileForUpload);
//            }

            if (thisUploadJob.needsDelete(fileForUploadUri)) {
                if (doDeleteUploadedResourceFromServer(thisUploadJob, thisUploadJob.getUploadedFileResource(fileForUploadUri))) {
                    thisUploadJob.markFileAsDeleted(fileForUploadUri);
                    // notify the listener that upload has been cancelled for this file
                    postNewResponse(jobId, new FileUploadCancelledResponse(getNextMessageId(), fileForUploadUri));
                } else {
                    //TODO notify user the uploaded file couldn't be deleted - needs manual intervention to remove it. Will be handled on Retry?
                }
            }

            if (thisUploadJob.needsDeleteAndThenReUpload(fileForUploadUri)) {
                if (doDeleteUploadedResourceFromServer(thisUploadJob, thisUploadJob.getUploadedFileResource(fileForUploadUri))) {
                    thisUploadJob.clearUploadProgress(fileForUploadUri);
                    //TODO notify user that the file bytes were deleted and upload must be started over
                } else {
                    //TODO notify user the uploaded file couldn't be deleted - needs manual intervention to remove it. Will be handled on Retry?
                }
            }

            saveStateToDisk(thisUploadJob);

            updateNotificationProgressText(thisUploadJob.getOverallUploadProgressInt());
        }
    }

    protected void doHandleUserCancelledUpload(UploadJob thisUploadJob, Uri fileForUploadUri) {
        if(null != thisUploadJob.getChunksAlreadyUploadedData(fileForUploadUri)) {
            thisUploadJob.markFileAsNeedsDelete(fileForUploadUri);
        }
    }

    private void doAnyCompressionNeeded(UploadJob thisUploadJob, Uri fileForUploadUri) {
        DocumentFile compressedFile;
        if (thisUploadJob.isPlayableMedia(this, fileForUploadUri)) {
            // it is compression wanted, and it this particular video compressible.
            if (thisUploadJob.isCompressPlayableMediaBeforeUpload() && thisUploadJob.canCompressVideoFile(this, fileForUploadUri)) {
                //Check if we've already compressed it
                compressedFile = getCompressedVersionOfFileToUpload(thisUploadJob, fileForUploadUri);
                // compressedFile could have been deleted (and thus be null)
                if (compressedFile == null && thisUploadJob.isUploadProcessNotYetStarted(fileForUploadUri)) {
                    // need to compress this file
                    final UploadFileCompressionListener listener = new UploadFileCompressionListener(this, thisUploadJob);
                    VideoCompressor videoCompressor = new VideoCompressor(this);
                    videoCompressor.compressVideo(thisUploadJob, fileForUploadUri, listener);
                }
            }
        } else if (thisUploadJob.isPhoto(this, fileForUploadUri)) {
            if (thisUploadJob.isCompressPhotosBeforeUpload()) {
                compressedFile = getCompressedVersionOfFileToUpload(thisUploadJob, fileForUploadUri);
                // compressedFile could have been deleted (and thus be null)
                if (compressedFile == null && thisUploadJob.isUploadProcessNotYetStarted(fileForUploadUri)) {
                    // need to compress this file
                    ImageCompressor imageCompressor = new ImageCompressor(this);
                    imageCompressor.compressImage(thisUploadJob, fileForUploadUri, new ImageCompressor.ImageCompressorListener() {
                        @Override
                        public void onError(long jobId, PiwigoUploadFileLocalErrorResponse piwigoUploadFileLocalErrorResponse) {
                            postNewResponse(jobId, piwigoUploadFileLocalErrorResponse);
                        }

                        public void onCompressionSuccess(DocumentFile compressedFile) {
                            updateJobWithCompressedFile(thisUploadJob, fileForUploadUri, compressedFile);
                        }
                    });
                }
            }
        }
    }

    public void updateJobWithCompressedFile(UploadJob uploadJob, Uri fileForUploadUri, DocumentFile compressedFile) {
        // we use this checksum to check the file was uploaded successfully
        try {
            uploadJob.addFileChecksum(this, fileForUploadUri, compressedFile.getUri());
            uploadJob.markFileAsCompressed(fileForUploadUri);
            saveStateToDisk(uploadJob);
        } catch (Md5SumUtils.Md5SumException e) {
            // theoretically this will never occur.
            Bundle b = new Bundle();
            b.putString("error", "error calculating md5sum for compressed file.");
            Logging.logAnalyticEvent(this, "md5sum", b);
            uploadJob.setErrorFlag(fileForUploadUri);
            recordAndPostNewResponse(uploadJob, new PiwigoUploadFileLocalErrorResponse(getNextMessageId(), compressedFile.getUri(), true, e));
        }
    }

    protected DocumentFile getCompressedVersionOfFileToUpload(UploadJob uploadJob, Uri fileForUpload) {
        Uri compressedFileUri = uploadJob.getCompressedFile(fileForUpload);
        return IOUtils.getSingleDocFile(this, compressedFileUri);
    }

    private void uploadFileInChunks(UploadJob thisUploadJob, Uri uploadItemKey, Uri fileForUpload, String uploadName, int maxChunkUploadAutoRetries) throws IOException {

        // Have at most one chunk queued ready for upload
        BlockingQueue<UploadFileChunk> chunkQueue = new ArrayBlockingQueue<>(2);
        FileChunkProducerThread chunkProducer = new FileChunkProducerThread(thisUploadJob, uploadItemKey, fileForUpload, uploadName, isUseFilenamesOverMd5ChecksumForUniqueness(thisUploadJob), getMaxChunkUploadSizeBytes());
        FileChunkUploaderThread chunkConsumer = new FileChunkUploaderThread(thisUploadJob, maxChunkUploadAutoRetries, new MyChunkUploadListener(chunkProducer));

        // start the upload
        FileUploadCancelMonitorThread watchThread = new FileUploadCancelMonitorThread(thisUploadJob, uploadItemKey) {
            @Override
            public void onFileUploadCancelled(Uri f) {
                Logging.log(Log.DEBUG, TAG, "FileUploadCancel Thread - Cancelling file upload");
                doHandleUserCancelledUpload(thisUploadJob, f);
                chunkProducer.stopAsap();
                chunkConsumer.stopAsap();
            }
        };

        watchThread.setDaemon(true);
        watchThread.start();
        chunkProducer.startProducing(this, chunkQueue);
        chunkConsumer.startConsuming(this, chunkQueue);

        // wait until complete or the
        do {
            try {
                // 250millis is a reasonable delay to discover the producer died for some unknown reason
                synchronized (chunkConsumer) {
                    chunkConsumer.wait(250); // wait for 250millis then wait again.
                }
            } catch (InterruptedException e) {
                if (thisUploadJob.isCancelUploadAsap()) {
                    chunkProducer.stopAsap();
                    chunkConsumer.stopAsap();
                    watchThread.markDone();
                }
            }
            if(chunkConsumer.isUploadComplete()) {
                watchThread.markDone();
            }
            if(chunkProducer.isFinished()){
                if(!chunkProducer.isCompletedSuccessfully()) {
                    chunkConsumer.stopAsap();
                    watchThread.markDone();
                }
            }
        } while (!thisUploadJob.isCancelUploadAsap() && !chunkConsumer.isUploadComplete() && !chunkConsumer.isFinished());
        if(!chunkProducer.isFinished()) {
            chunkProducer.stopAsap();
        }
        if(!chunkConsumer.isFinished()) {
            chunkConsumer.stopAsap();
        }
    }

    public void updateNotificationProgressText(int uploadProgress) {
        updateNotificationText(getString(R.string.notification_message_upload_service), uploadProgress);
    }

    private void doConfigurationOfUploadedFileDetails(UploadJob thisUploadJob, long jobId, Uri fileForUpload, Set<Long> allServerAlbumIds) {
        if (thisUploadJob.isFileUploadStillWanted(fileForUpload)) {

            ResourceItem uploadedResource = thisUploadJob.getUploadedFileResource(fileForUpload);
            if (uploadedResource != null) {
                PiwigoResponseBufferingHandler.BaseResponse response = updateImageInfoAndPermissions(thisUploadJob, fileForUpload, uploadedResource, allServerAlbumIds);

                if (response instanceof PiwigoResponseBufferingHandler.ErrorResponse) {

                    // notify the listener of the final error we received from the server
                    postNewResponse(jobId, new PiwigoUploadFileAddToAlbumFailedResponse(getNextMessageId(), fileForUpload, false, response));

                } else {
                    thisUploadJob.markFileAsConfigured(fileForUpload);
                    recordAndPostNewResponse(thisUploadJob, new PiwigoUploadProgressUpdateResponse(getNextMessageId(), fileForUpload, thisUploadJob.getUploadProgress(fileForUpload)));
                }
            }
        } else {
            thisUploadJob.markFileAsNeedsDelete(fileForUpload);
        }
    }

    private void doVerificationOfUploadedFileData(UploadJob thisUploadJob, Uri fileForUpload) {
        if (thisUploadJob.isFileUploadStillWanted(fileForUpload)) {
//            TaskProgressTracker verificationTracker = thisUploadJob.getTaskProgressTrackerForSingleFileVerification();
            try {

                Boolean verifiedUploadedFile = verifyFileNotCorrupted(thisUploadJob, fileForUpload, thisUploadJob.getUploadedFileResource(fileForUpload));
                if (verifiedUploadedFile == null) {
                    // notify the listener of the final error we received from the server
                    String errorMsg = getString(R.string.error_upload_file_verification_failed, fileForUpload.getPath());
                    notifyListenersOfCustomErrorUploadingFile(thisUploadJob, fileForUpload, false, errorMsg);
                    // this file isn't on the server at all, so just clear the upload progress to allow retry.
                    thisUploadJob.clearUploadProgress(fileForUpload);
                } else if (verifiedUploadedFile) {
                    //TODO send AJAX request to generate all derivatives. Need Custom method in piwigo client plugin. - method will be sent id of image and the server will invoke a get on all derivative urls (obtained using a ws call to pwg.getDerivatives).

                    thisUploadJob.markFileAsVerified(fileForUpload);
                    recordAndPostNewResponse(thisUploadJob, new PiwigoUploadProgressUpdateResponse(getNextMessageId(), fileForUpload, thisUploadJob.getUploadProgress(fileForUpload)));
                    deleteCompressedVersionIfExists(thisUploadJob, fileForUpload);
                } else {
                    // the file verification failed - this file is corrupt (needs delete but then re-upload).
                    thisUploadJob.deleteChunksAlreadyUploadedData(fileForUpload);
                    thisUploadJob.markFileAsCorrupt(fileForUpload);
                    thisUploadJob.clearUploadProgress(fileForUpload);
                    // reduce the overall progress again.
                    thisUploadJob.getTaskProgressTrackerForOverallCompressionAndUploadOfData().rollbackWorkDone(thisUploadJob.getUploadUploadProgressTicksForFile(fileForUpload));
                }
            } finally {
//                verificationTracker.markComplete();
            }
        } else {
            thisUploadJob.markFileAsNeedsDelete(fileForUpload);
        }
    }

    @Subscribe(threadMode = ASYNC)
    public void onEvent(CancelFileUploadEvent event) {
        if(runningUploadJob.getJobId() == event.getJobId()) {
            synchronized (runningUploadJob) {
                runningUploadJob.notifyAll();
            }
        }
    }

    private void deleteCompressedVersionIfExists(UploadJob thisUploadJob, Uri fileForUpload) {
        DocumentFile compressedFile = getCompressedVersionOfFileToUpload(thisUploadJob, fileForUpload);
        if (compressedFile != null && compressedFile.exists()) {
            if (!compressedFile.delete()) {
                onFileDeleteFailed(TAG, compressedFile, "compressed file after upload of file (or upload cancelled)");
            }
        }
    }

    private void doUploadFileData(UploadJob thisUploadJob, Uri uploadJobKey, Uri fileForUploadUri, int maxChunkUploadAutoRetries) {
        long jobId = thisUploadJob.getJobId();

        if (!thisUploadJob.isCancelUploadAsap() && thisUploadJob.isFileUploadStillWanted(uploadJobKey)) {

            postNewResponse(jobId, new PiwigoStartUploadFileResponse(getNextMessageId(), uploadJobKey));

            try {
                String ext = IOUtils.getFileExt(this, fileForUploadUri);

                if (ext.length() == 0) {
                    thisUploadJob.setErrorFlag(uploadJobKey);
                    // notify the listener of the final error we received from the server
                    String errorMsg = getString(R.string.error_upload_file_ext_missing_pattern, fileForUploadUri.getPath());
                    notifyListenersOfCustomErrorUploadingFile(thisUploadJob, uploadJobKey, true, errorMsg);
                }

                if (thisUploadJob.isFileUploadStillWanted(uploadJobKey)) {
                    String tempUploadName = "PiwigoClient_Upload_" + random.nextLong() + '.' + ext;

                    uploadFileInChunks(thisUploadJob, uploadJobKey, fileForUploadUri, tempUploadName, maxChunkUploadAutoRetries);

                    ResourceItem uploadedResource = thisUploadJob.getUploadedFileResource(uploadJobKey);
                    if (uploadedResource != null) {
                        // this should ALWAYS be the case!
                        fillBlankResourceItem(thisUploadJob, uploadedResource, uploadJobKey, fileForUploadUri);

                    } else if (!thisUploadJob.isFileUploadStillWanted(uploadJobKey)) {
                        //This block gets entered when the upload is cancelled for a file
                    } else {
                        // notify the listener of the final error we received from the server
                        String errorMsg = getString(R.string.error_upload_file_chunk_upload_failed_after_retries, maxChunkUploadAutoRetries);
                        notifyListenersOfCustomErrorUploadingFile(thisUploadJob, uploadJobKey, false, errorMsg);
                    }
                }
            } catch (IOException e) {
                Logging.recordException(e);
                thisUploadJob.setErrorFlag(uploadJobKey);
                postNewResponse(jobId, new PiwigoUploadFileLocalErrorResponse(getNextMessageId(), uploadJobKey, true, e));
            }
        }
    }

    private void fillBlankResourceItem(UploadJob thisUploadJob, ResourceItem uploadedResource, Uri fileForUploadItemKey, Uri fileForUploadUri) {
        uploadedResource.setName(IOUtils.getFilename(this, fileForUploadUri));
        uploadedResource.setParentageChain(thisUploadJob.getUploadToCategoryParentage(), thisUploadJob.getUploadToCategory());
        uploadedResource.setPrivacyLevel(thisUploadJob.getPrivacyLevelWanted());
        uploadedResource.setFileChecksum(thisUploadJob.getFileChecksum(fileForUploadItemKey));


        long lastModifiedTime = IOUtils.getLastModifiedTime(this, fileForUploadItemKey);
        if (lastModifiedTime > 0) {
            Date lastModDate = new Date(lastModifiedTime);
            uploadedResource.setCreationDate(lastModDate);
        }
        setUploadedImageDetailsFromExifData(fileForUploadUri, uploadedResource);
    }

    private void setUploadedImageDetailsFromExifData(Uri fileForUpload, ResourceItem uploadedResource) {

        try (InputStream is = getContentResolver().openInputStream(fileForUpload)) {
            if (is == null) {
                throw new IOException("Error ");
            }

            try (BufferedInputStream bis = new BufferedInputStream(is)) {

                Metadata metadata = ImageMetadataReader.readMetadata(bis);
                Directory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
                setCreationDateAndFilenameFromExifMetadata(uploadedResource, directory);
                directory = metadata.getFirstDirectoryOfType(IptcDirectory.class);
                setCreationDateAndFilenameFromIptcMetadata(uploadedResource, directory);
            } catch (ImageProcessingException e) {
                Logging.recordException(e);
                Logging.log(Log.ERROR, tag, "Error parsing EXIF data : sinking");
            }
        } catch (FileNotFoundException e) {
            Logging.log(Log.WARN, tag, "File Not found - Unable to parse EXIF data : sinking");
        } catch (IOException e) {
            Logging.recordException(e);
            // ignore for now
            Logging.log(Log.ERROR, tag, "Error parsing EXIF data : sinking");
        }
    }

    private boolean setCreationDateAndFilenameFromIptcMetadata(@NonNull ResourceItem uploadedResource, @Nullable Directory directory) {
        if (directory == null) {
            return false;
        }
        Date creationDate = directory.getDate(IptcDirectory.TAG_DATE_CREATED);
        if (creationDate != null) {
            uploadedResource.setCreationDate(creationDate);
        }
        String imageDescription = directory.getString(IptcDirectory.TAG_CAPTION);
        if (imageDescription != null) {
            uploadedResource.setName(imageDescription);
        }
        return true;
    }

    private boolean setCreationDateAndFilenameFromExifMetadata(@NonNull ResourceItem uploadedResource, @Nullable Directory directory) {
        if (directory == null) {
            return false;
        }
        Date creationDate = directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
        if (creationDate != null) {
            uploadedResource.setCreationDate(creationDate);
        }
        String imageDescription = directory.getString(ExifSubIFDDirectory.TAG_IMAGE_DESCRIPTION);
        if (imageDescription != null) {
            uploadedResource.setName(imageDescription);
        }
        return true;
    }

    private boolean doDeleteUploadedResourceFromServer(UploadJob uploadJob, ResourceItem uploadedResource) {

        if (uploadedResource == null) {
            Logging.log(Log.WARN, TAG, "cannot delete uploaded resource from server, as we are missing a reference to it (presumably upload has not been started)!");
            return true;
        }
        if (PiwigoSessionDetails.isAdminUser(uploadJob.getConnectionPrefs())) {
            ImageDeleteResponseHandler<ResourceItem> imageDeleteHandler = new ImageDeleteResponseHandler<>(uploadedResource);
            serverCaller.invokeWithRetries(uploadJob, imageDeleteHandler, 2);
            return imageDeleteHandler.isSuccess();
        } else {
            // community plugin... can't delete files... have to pretend we did...
            return true;
        }
    }

    private Boolean verifyFileNotCorrupted(UploadJob uploadJob, Uri fileForUpload, ResourceItem uploadedResource) {
        if (uploadedResource.getFileChecksum() == null) {
            return Boolean.FALSE; // cannot verify it as don't have a local checksum for some reason. Will have to assume it is corrupt.
        }
        ImageCheckFilesResponseHandler<ResourceItem> imageFileCheckHandler = new ImageCheckFilesResponseHandler<>(uploadedResource);
        serverCaller.invokeWithRetries(uploadJob, fileForUpload, imageFileCheckHandler, 2);
        Boolean val = imageFileCheckHandler.isSuccess() ? imageFileCheckHandler.isFileMatch() : null;
        if (Boolean.FALSE.equals(val)) {

            ResourceItem uploadedResourceDummy = new ResourceItem(uploadedResource.getId(), uploadedResource.getName(), null, null, null, null);
            ImageGetInfoResponseHandler<ResourceItem> imageDetailsHandler = new ImageGetInfoResponseHandler<>(uploadedResourceDummy);
            serverCaller.invokeWithRetries(uploadJob, imageDetailsHandler, 2);
            if (imageDetailsHandler.isSuccess()) {
                BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse<?> rsp = (ImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse) imageDetailsHandler.getResponse();
                val = ObjectUtils.areEqual(uploadedResource.getFileChecksum(), rsp.getResource().getFileChecksum());
                if (val) {
                    String msgStr = String.format(getString(R.string.message_piwigo_server_inconsistent_results), imageFileCheckHandler.getPiwigoMethod(), imageDetailsHandler.getPiwigoMethod());
                    MessageForUserResponse msg = new MessageForUserResponse(uploadJob.getJobId(), msgStr);
                    postNewResponse(uploadJob.getJobId(), msg);
                }
            }
        }
        return val;
    }

    private PiwigoResponseBufferingHandler.BaseResponse updateImageInfoAndPermissions(UploadJob thisUploadJob, Uri fileForUpload, ResourceItem uploadedResource, Set<Long> allServerAlbumIds) {
        uploadedResource.getLinkedAlbums().add(thisUploadJob.getUploadToCategory());
        if (thisUploadJob.getTemporaryUploadAlbum() > 0) {
            uploadedResource.getLinkedAlbums().remove(thisUploadJob.getTemporaryUploadAlbum());
        }
        // Don't update the tags because we aren't altering this aspect of the the image during upload and it (could) cause problems
        ImageUpdateInfoResponseHandler<ResourceItem> imageInfoUpdateHandler = new ImageUpdateInfoResponseHandler<>(uploadedResource, false);

        imageInfoUpdateHandler.setFilename(IOUtils.getFilename(this, fileForUpload));
        serverCaller.invokeWithRetries(thisUploadJob, fileForUpload, imageInfoUpdateHandler, 2);
        if (!imageInfoUpdateHandler.isSuccess()) {
            Iterator<Long> iter = uploadedResource.getLinkedAlbums().iterator();
            boolean ghostAlbumRemovedFromImage = false;
            while (iter.hasNext()) {
                Long albumId = iter.next();
                if (!allServerAlbumIds.contains(albumId)) {
                    ghostAlbumRemovedFromImage = true;
                    iter.remove();
                }
            }
            if (ghostAlbumRemovedFromImage) {
                // retry.
                serverCaller.invokeWithRetries(thisUploadJob, fileForUpload, imageInfoUpdateHandler, 2);
            }
        }
        return imageInfoUpdateHandler.getResponse();
    }

    private int getMaxChunkUploadSizeBytes() {
        int wantedUploadSizeInKbB = UploadPreferences.getMaxUploadChunkSizeKb(this, prefs);
        return 1024 * wantedUploadSizeInKbB; // 512Kb chunk size
    }

    public interface JobUploadListener {
        void onJobReadyToUpload(Context c, UploadJob thisUploadJob);
    }

    protected class ActionsBroadcastReceiver extends BroadcastReceiver {

        private final String stopAction;

        public ActionsBroadcastReceiver(@NonNull String stopAction) {
            this.stopAction = stopAction;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (stopAction.equals(intent.getAction())) {
                actionKillService();
            }
        }

        public IntentFilter getFilter() {
            IntentFilter filter = new IntentFilter();
            filter.addAction(stopAction);
            return filter;
        }
    }

    private class MyChunkUploadListener implements FileChunkUploaderThread.ChunkUploadListener {

        private final FileChunkProducerThread chunkProducer;
        private Map<Uri, TaskProgressTracker> chunkProgressTrackers;

        public MyChunkUploadListener(FileChunkProducerThread chunkProducer) {
            this.chunkProducer = chunkProducer;
            chunkProgressTrackers = new HashMap<>();
        }

        @Override
        public void onChunkUploadFailed(UploadJob thisUploadJob, UploadFileChunk chunk, PiwigoResponseBufferingHandler.BaseResponse piwigoServerResponse) {
            try {
                recordAndPostNewResponse(thisUploadJob, new PiwigoUploadFileChunkFailedResponse(getNextMessageId(), chunk.getUploadJobItemKey(), piwigoServerResponse));
                if (!thisUploadJob.isFileUploadStillWanted(chunk.getUploadJobItemKey())) {
                    // notify the listener that upload has been cancelled for this file (at user's request)
                    recordAndPostNewResponse(thisUploadJob, new FileUploadCancelledResponse(getNextMessageId(), chunk.getUploadJobItemKey()));
                }
            } finally {
                chunkProducer.returnChunkToPool(chunk);
                //TODO maybe stop uploading the rest of the chunks.
                //chunkProducer.stopAsap();
            }
        }

        @Override
        public void onUploadCancelled() {
            chunkProgressTrackers.clear();
        }

        @Override
        public boolean onChunkUploadSuccess(UploadJob thisUploadJob, UploadFileChunk chunk) {
            TaskProgressTracker chunkProgressTracker = getChunkProgressTracker(thisUploadJob, chunk.getUploadJobItemKey());

            try {
                String fileChecksum = thisUploadJob.getFileChecksum(chunk.getUploadJobItemKey());
                if (isUseFilenamesOverMd5ChecksumForUniqueness(thisUploadJob)) {
                    fileChecksum = IOUtils.getFilename(getApplicationContext(), chunk.getFileBeingUploaded());
                }
                if (chunkProgressTracker == null) {
                    // this file upload is just starting. The overall progress has no record of this file upload byte transfer progress
                    chunkProgressTracker = thisUploadJob.getTaskProgressTrackerForSingleFileChunkParsing(chunk.getUploadJobItemKey(), chunk.getFileSizeBytes(), 0);
                    storeChunkProgressTracker(chunk.getUploadJobItemKey(), chunkProgressTracker);
                    thisUploadJob.markFileAsUploading(chunk.getUploadJobItemKey());
                    thisUploadJob.markFileAsPartiallyUploaded(chunk.getUploadJobItemKey(), chunk.getFilenameOnServer(), fileChecksum, chunk.getFileSizeBytes(), chunk.getChunkSizeBytes(), chunk.getChunkId(), chunk.getMaxChunkSize());
                } else {
                    thisUploadJob.markFileAsPartiallyUploaded(chunk.getUploadJobItemKey(), fileChecksum, chunk.getChunkSizeBytes(), chunk.getChunkId());
                }
                chunkProgressTracker.incrementWorkDone(chunk.getChunkSizeBytes());

                recordAndPostNewResponse(thisUploadJob, new PiwigoUploadProgressUpdateResponse(getNextMessageId(), chunk.getUploadJobItemKey(), thisUploadJob.getUploadProgress(chunk.getUploadJobItemKey())));
                saveStateToDisk(thisUploadJob);
            } finally {
                chunkProducer.returnChunkToPool(chunk);
            }
            return chunkProgressTracker.isComplete();
        }

        private void storeChunkProgressTracker(Uri uploadJobItemKey, TaskProgressTracker chunkProgressTracker) {
            chunkProgressTrackers.put(uploadJobItemKey, chunkProgressTracker);
        }

        private TaskProgressTracker getChunkProgressTracker(UploadJob thisUploadJob, Uri uploadJobItemKey) {
            TaskProgressTracker tracker = chunkProgressTrackers.get(uploadJobItemKey);
            if (tracker == null) {
                UploadJob.PartialUploadData uploadData = thisUploadJob.getChunksAlreadyUploadedData(uploadJobItemKey);
                if (uploadData != null) {
                    tracker = thisUploadJob.getTaskProgressTrackerForSingleFileChunkParsing(uploadJobItemKey, uploadData.getTotalBytesToUpload(), uploadData.getBytesUploaded());
                    storeChunkProgressTracker(uploadJobItemKey, tracker);
                }
            }
            return tracker;
        }
    }
}
