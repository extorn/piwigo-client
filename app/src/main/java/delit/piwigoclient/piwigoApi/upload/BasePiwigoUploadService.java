package delit.piwigoclient.piwigoApi.upload;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.RequiresApi;
import androidx.core.app.JobIntentService;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.exifinterface.media.ExifInterface;

import com.crashlytics.android.Crashlytics;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.annotations.NotNull;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.iptc.IptcDirectory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
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
import java.util.Set;

import cz.msebera.android.httpclient.HttpStatus;
import delit.libs.util.IOUtils;
import delit.libs.util.ObjectUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AlbumViewPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.business.UploadPreferences;
import delit.piwigoclient.business.video.compression.ExoPlayerCompression;
import delit.piwigoclient.model.UploadFileChunk;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoWsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumCreateResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumsAdminResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.BaseImageGetInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.CommunityGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.CommunityNotifyUploadCompleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageFindExistingImagesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageGetInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImagesListOrphansResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.LoginResponseHandler;
import delit.piwigoclient.piwigoApi.upload.handlers.ImageCheckFilesResponseHandler;
import delit.piwigoclient.piwigoApi.upload.handlers.NewImageUploadFileChunkResponseHandler;
import delit.piwigoclient.piwigoApi.upload.handlers.UploadAlbumCreateResponseHandler;
import id.zelory.compressor.Compressor;

import static delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler.getNextMessageId;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 */
public abstract class BasePiwigoUploadService extends JobIntentService {

    private static final String TAG = "BaseUpldSvc";
    private static final List<UploadJob> activeUploadJobs = Collections.synchronizedList(new ArrayList<UploadJob>(1));
    private static final SecureRandom random = new SecureRandom();
    private final String tag;
    private SharedPreferences prefs;

    public BasePiwigoUploadService(String tag) {
        this.tag = tag;
    }

    public static @NotNull
    UploadJob createUploadJob(ConnectionPreferences.ProfilePreferences connectionPrefs, List<File> filesForUpload, CategoryItemStub category, byte uploadedFilePrivacyLevel, long responseHandlerId) {
        long jobId = getNextMessageId();
        UploadJob uploadJob = new UploadJob(connectionPrefs, jobId, responseHandlerId, filesForUpload, category, uploadedFilePrivacyLevel);
        synchronized (activeUploadJobs) {
            activeUploadJobs.add(uploadJob);
        }
        return uploadJob;
    }

    public static UploadJob getFirstActiveForegroundJob(Context context) {
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

    public static int getUploadJobsCount(Context context) {
        synchronized (activeUploadJobs) {
            if (activeUploadJobs.size() == 0) {
                loadForegroundJobStateFromDisk(context);
                activeUploadJobs.addAll(loadBackgroundJobsStateFromDisk(context));
            }
            return activeUploadJobs.size();
        }
    }

    public static UploadJob getActiveBackgroundJob(Context context, long jobId) {
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

    public static UploadJob getActiveForegroundJob(Context context, long jobId) {
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
            Crashlytics.log(Log.WARN, TAG, "Job exists on disk, but it doesn't match that expected by the app - deleting");
            deleteStateFromDisk(context, job);
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
                Crashlytics.log(Log.ERROR, TAG, "Unable to create folder to store background upload job status data in");
            }
            return new ArrayList<>();
        }

        List<UploadJob> jobs = new ArrayList<>();
        File[] jobFiles = jobsFolder.listFiles();
        for (File jobFile : jobFiles) {
            UploadJob job = IOUtils.readObjectFromFile(jobFile);
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

    private static UploadJob loadForegroundJobStateFromDisk(Context c) {

        UploadJob loadedJobState = null;

        File sourceFile = getJobStateFile(c, false, -1);
        if (sourceFile.exists()) {
            loadedJobState = IOUtils.readObjectFromFile(sourceFile);
        }
        if (loadedJobState != null) {
            AbstractPiwigoDirectResponseHandler.blockMessageId(loadedJobState.getJobId());
        }
        return loadedJobState;
    }

    public static void deleteStateFromDisk(Context c, UploadJob thisUploadJob) {
        if (thisUploadJob == null) {
            return; // out of sync! Job no longer exists presumably.
        }
        for (File file : thisUploadJob.getFilesWithStatus(UploadJob.COMPRESSED)) {
            File compressedVersion = thisUploadJob.getCompressedFile(file);
            if (compressedVersion != null && compressedVersion.exists()) {
                if (!compressedVersion.delete()) {
                    Crashlytics.log(Log.ERROR, TAG, "Unable to delete compressed file when attempting to delete job state from disk.");
                }
            }
        }

        File stateFile = thisUploadJob.getLoadedFromFile();
        if (stateFile == null) {
            stateFile = getJobStateFile(c, thisUploadJob.isRunInBackground(), thisUploadJob.getJobId());
        }
        deleteJobStateFile(stateFile);
    }

    private static void deleteJobStateFile(File f) {
        if (f.exists()) {
            if (!f.delete()) {
                Log.d(TAG, "Error deleting job state from disk");
            }
        }
    }

    private static File getJobStateFile(Context c, boolean isBackgroundJob, long jobId) {
        if (isBackgroundJob) {
            File jobsFolder = new File(c.getExternalCacheDir(), "uploadJobs");
            return new File(jobsFolder, jobId + ".state");
        } else {
            return new File(c.getExternalCacheDir(), "uploadJob.state");
        }
    }

    private static void onFileDeleteFailed(String tag, File f, String fileDesc) {
        if (f.exists()) {
            if (BuildConfig.DEBUG) {
                Log.e(tag, "Unable to delete " + fileDesc + " : " + f.getAbsolutePath());
            } else {
                Crashlytics.log(Log.WARN, tag, "\"Unable to delete " + fileDesc);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    Files.delete(f.toPath());
                } catch (IOException e) {
                    Crashlytics.logException(e);
                    if (BuildConfig.DEBUG) {
                        Log.e(tag, "Really unable to delete " + fileDesc + " : " + f.getAbsolutePath(), e);
                    }
                }
            }
        }
    }

    protected void runPostJobCleanup(UploadJob uploadJob, boolean deleteUploadedFiles) {
        if (uploadJob == null) {
            return; // Do nothing.
        }
        File tmp_upload_folder = new File(getExternalCacheDir(), "piwigo-upload");

        for (File f : uploadJob.getFilesSuccessfullyUploaded()) {
            if (f.exists()) {
                if (deleteUploadedFiles || f.getParentFile().equals(tmp_upload_folder)) {
                    if (!f.getAbsoluteFile().delete()) {
                        onFileDeleteFailed(tag, f, "uploaded file");
                    }
                }
            }
        }

        // record all files uploaded to prevent repeated upload (do this always in case delete fails for a file!
        HashMap<File, String> uploadedFileChecksums = uploadJob.getUploadedFilesLocalFileChecksums();
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

    //TODO add determinate progress...
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
            notificationBuilder.setCategory(Notification.CATEGORY_SERVICE);
        }
        notificationBuilder.setAutoCancel(true);
//        .setTicker(getText(R.string.ticker_text))
        return notificationBuilder;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createNotificationChannelIfNeeded() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = notificationManager.getNotificationChannel(getDefaultNotificationChannelId());
        if (channel == null) {
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            String name = getString(R.string.app_name);
            channel = new NotificationChannel(getDefaultNotificationChannelId(), name, importance);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private String getDefaultNotificationChannelId() {
        return getString(R.string.app_name) + "_UploadService";
    }

    protected void doBeforeWork(Intent intent) {
        NotificationCompat.Builder notificationBuilder = buildNotification(getString(R.string.notification_message_upload_service));
        notificationBuilder.setProgress(0, 0, true);
        startForeground(getNotificationId(), notificationBuilder.build());
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
    }

    public SharedPreferences getPrefs() {
        return prefs;
    }

    protected abstract void doWork(Intent intent);

    protected abstract void updateListOfPreviouslyUploadedFiles(UploadJob uploadJob, HashMap<File, String> uploadedFileChecksums);

    protected abstract void postNewResponse(long jobId, PiwigoResponseBufferingHandler.Response response);

    protected void runJob(long jobId) {
        UploadJob thisUploadJob = getActiveForegroundJob(this, jobId);
        runJob(thisUploadJob, null);
        runPostJobCleanup(thisUploadJob, false);
    }

    protected void runJob(UploadJob thisUploadJob, JobUploadListener listener) {

        int maxChunkUploadAutoRetries = UploadPreferences.getUploadChunkMaxRetries(this, prefs);

        if (thisUploadJob == null) {
            if (BuildConfig.DEBUG) {
                Log.e(tag, "Upload job could not be located immediately after creating it - weird!");
            } else {
                Crashlytics.log(Log.WARN, tag, "Upload job could not be located immediately after creating it - weird!");
            }
            return;
        }
        thisUploadJob.setRunning(true);
        thisUploadJob.setSubmitted(false);

        try {
            PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(thisUploadJob.getConnectionPrefs());
            if (sessionDetails == null) {
                LoginResponseHandler handler = new LoginResponseHandler();
                invokeWithRetries(thisUploadJob, handler, 2);
                if (handler.isSuccess()) {
                    sessionDetails = PiwigoSessionDetails.getInstance(thisUploadJob.getConnectionPrefs());
                } else {
                    postNewResponse(thisUploadJob.getJobId(), new PiwigoPrepareUploadFailedResponse(getNextMessageId(), handler.getResponse()));
                    return;
                }
                if (sessionDetails == null) {
                    Bundle b = new Bundle();
                    b.putString("location", "upload - get login");
                    FirebaseAnalytics.getInstance(this).logEvent("SessionNull", b);
                    postNewResponse(thisUploadJob.getJobId(), new PiwigoPrepareUploadFailedResponse(getNextMessageId(), handler.getResponse()));
                    return;
                }
            }
            ArrayList<CategoryItemStub> availableAlbumsOnServer = retrieveListOfAlbumsOnServer(thisUploadJob, sessionDetails);
            if (availableAlbumsOnServer == null) {
                //try again. This is really important.
                availableAlbumsOnServer = retrieveListOfAlbumsOnServer(thisUploadJob, sessionDetails);
            }
            if (availableAlbumsOnServer == null) {
                // This is fatal really. It is necessary for a resilient upload. Stop the upload.
                return;
            }

            if (thisUploadJob.getTemporaryUploadAlbum() > 0) {
                // check it still exists (ensure one is created again if not) - could have been deleted by a user manually.
                boolean tempAlbumExists = false;
                for (CategoryItemStub cat : availableAlbumsOnServer) {
                    if (cat.getId() == thisUploadJob.getTemporaryUploadAlbum()) {
                        tempAlbumExists = true;
                    }
                }
                if (!tempAlbumExists) {
                    // allow a new one to be created and tracked
                    thisUploadJob.setTemporaryUploadAlbum(-1);
                }
            }

            saveStateToDisk(thisUploadJob);


            thisUploadJob.calculateChecksums();

            if (thisUploadJob.isRunInBackground() && listener != null) {
                listener.onJobReadyToUpload(this, thisUploadJob);
            }

            // is name or md5sum used for uniqueness on this server?
            boolean nameUnique = isUseFilenamesOverMd5ChecksumForUniqueness();
            Collection<String> uniqueIdsList;
            if (nameUnique) {
                uniqueIdsList = thisUploadJob.getFileToFilenamesMap().values();
            } else {
                uniqueIdsList = thisUploadJob.getFileChecksums().values();
            }


            if (!thisUploadJob.getFilesForUpload().isEmpty()) {
                // remove any files that already exist on the server from the upload.
                ImageFindExistingImagesResponseHandler imageFindExistingHandler = new ImageFindExistingImagesResponseHandler(uniqueIdsList, nameUnique);
                invokeWithRetries(thisUploadJob, imageFindExistingHandler, 2);
                if (imageFindExistingHandler.isSuccess()) {
                    ArrayList<Long> orphans;
                    if(PiwigoSessionDetails.isAdminUser(thisUploadJob.getConnectionPrefs())) {
                        orphans = getOrphanImagesOnServer(thisUploadJob);
                        if(orphans == null) {
                            // there has been an error which is reported within the getOrphanImagesOnServer method.
                            return;
                        }
                    } else {
                        orphans = new ArrayList<>();
                    }
                    processFindPreexistingImagesResponse(thisUploadJob, (ImageFindExistingImagesResponseHandler.PiwigoFindExistingImagesResponse) imageFindExistingHandler.getResponse(), orphans);
                }
                if (!imageFindExistingHandler.isSuccess()) {
                    // notify the listener of the final error we received from the server
                    postNewResponse(thisUploadJob.getJobId(), new PiwigoPrepareUploadFailedResponse(getNextMessageId(), imageFindExistingHandler.getResponse()));
                    return;
                }

                boolean useTempFolder = !PiwigoSessionDetails.isUseCommunityPlugin(thisUploadJob.getConnectionPrefs());

                // create a secure folder to upload to if required
                if (useTempFolder && !createTemporaryUploadAlbum(thisUploadJob)) {
                    return;
                }
            }

            saveStateToDisk(thisUploadJob);

            if (!thisUploadJob.isCancelUploadAsap()) {
                if (!thisUploadJob.getFilesForUpload().isEmpty()) {
                    uploadFilesInJob(maxChunkUploadAutoRetries, thisUploadJob, availableAlbumsOnServer);
                }
            }

            if (!thisUploadJob.isCancelUploadAsap()) {
                if (sessionDetails.isUseCommunityPlugin() && sessionDetails.isCommunityApiAvailable()) {
                    Set<Long> ids = thisUploadJob.getIdsOfResourcesForFilesSuccessfullyUploaded();
                    CommunityNotifyUploadCompleteResponseHandler hndlr = new CommunityNotifyUploadCompleteResponseHandler(ids, thisUploadJob.getUploadToCategory());
                    if (sessionDetails.isMethodAvailable(hndlr.getPiwigoMethod())) {
                        invokeWithRetries(thisUploadJob, hndlr, 2);
                    }
                }
            }

            if (!thisUploadJob.isCancelUploadAsap()) {

                if (thisUploadJob.getFilesNotYetUploaded().size() == 0 && thisUploadJob.getTemporaryUploadAlbum() > 0) {
                    boolean success = deleteTemporaryUploadAlbum(thisUploadJob);
                    if (!success) {
                        return;
                    }
                }
            }

            if (thisUploadJob.hasJobCompletedAllActionsSuccessfully()) {
                deleteStateFromDisk(this, thisUploadJob);
            }

            thisUploadJob.setFinished();

        } finally {
            thisUploadJob.setRunning(false);
            thisUploadJob.clearCancelUploadAsapFlag();
            if (!thisUploadJob.hasJobCompletedAllActionsSuccessfully()) {
                saveStateToDisk(thisUploadJob);
            } else {
                deleteStateFromDisk(this, thisUploadJob);
            }

            postNewResponse(thisUploadJob.getJobId(), new PiwigoUploadFileJobCompleteResponse(getNextMessageId(), thisUploadJob));
            PiwigoResponseBufferingHandler.getDefault().deRegisterResponseHandler(thisUploadJob.getJobId());
            AbstractPiwigoDirectResponseHandler.unblockMessageId(thisUploadJob.getJobId());
        }
    }

    private boolean isUseFilenamesOverMd5ChecksumForUniqueness() {
        return "name".equals(prefs.getString(getString(R.string.preference_gallery_unique_id_key), getResources().getString(R.string.preference_gallery_unique_id_default)));
    }

    private void invokeWithRetries(UploadJob thisUploadJob, AbstractPiwigoWsResponseHandler handler, int maxRetries) {
        int allowedAttempts = maxRetries;
        while (!handler.isSuccess() && allowedAttempts > 0 && !thisUploadJob.isCancelUploadAsap()) {
            allowedAttempts--;
            // this is blocking
            handler.invokeAndWait(this, thisUploadJob.getConnectionPrefs());
        }
        if(!handler.isSuccess()) {
            thisUploadJob.recordError(new Date(), buildErrorMessage(handler));
            recordServerCallError(handler, thisUploadJob.getConnectionPrefs());
        }
    }

    protected String buildErrorMessage(AbstractPiwigoWsResponseHandler handler) {
        StringBuilder sb = new StringBuilder();
        sb.append("PiwigoMethod:");
        sb.append('\n');
        sb.append(handler.getPiwigoMethod());
        sb.append('\n');
        sb.append("Error:");
        sb.append('\n');
        if(handler.getError() != null) {
            sb.append(handler.getError().getMessage());
        } else {
            boolean detailAdded = false;
            if(handler.getResponse() != null && handler.getResponse() instanceof PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) {
                PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse errorResponse = (PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse)handler.getResponse();
                if(errorResponse.getResponse() != null) {
                    sb.append(errorResponse.getResponse());
                    detailAdded = true;
                }
                if(errorResponse.getErrorMessage() != null && !"java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1 path $".equals(errorResponse.getErrorMessage())) {
                    if(detailAdded) {
                        sb.append('\n');
                    }
                    sb.append(errorResponse.getErrorMessage());
                    detailAdded = true;
                }
                if(errorResponse.getErrorDetail() != null) {
                    if(detailAdded) {
                        sb.append('\n');
                    }
                    sb.append(errorResponse.getErrorDetail());
                    detailAdded = true;
                }
            }
            if(!detailAdded){
                sb.append("???");
            }
        }
        return sb.toString();
    }

    protected void recordServerCallError(AbstractPiwigoWsResponseHandler handler, ConnectionPreferences.ProfilePreferences connectionPrefs) {
        Bundle b = new Bundle();
        b.putString("piwigoMethod", handler.getPiwigoMethod());
        b.putString("requestParams", handler.getRequestParameters()==null?"null":handler.getRequestParameters().toString());
        b.putString("responseType", handler.getResponse() == null ? null : handler.getResponse().getClass().getName());
        b.putSerializable("error", handler.getError());
        PiwigoSessionDetails.writeToBundle(b, connectionPrefs);
        FirebaseAnalytics.getInstance(this).logEvent("uploadError", b);
    }

    private ArrayList<CategoryItemStub> retrieveListOfAlbumsOnServer(UploadJob thisUploadJob, PiwigoSessionDetails sessionDetails) {
        if (sessionDetails.isAdminUser()) {
            AlbumGetSubAlbumsAdminResponseHandler handler = new AlbumGetSubAlbumsAdminResponseHandler();
            invokeWithRetries(thisUploadJob, handler, 2);
            if (handler.isSuccess()) {
                AlbumGetSubAlbumsAdminResponseHandler.PiwigoGetSubAlbumsAdminResponse rsp = (AlbumGetSubAlbumsAdminResponseHandler.PiwigoGetSubAlbumsAdminResponse) handler.getResponse();
                return rsp.getAdminList().flattenTree();
            } else {
                postNewResponse(thisUploadJob.getJobId(), new PiwigoPrepareUploadFailedResponse(getNextMessageId(), handler.getResponse()));
            }
        } else if (sessionDetails.isUseCommunityPlugin() && sessionDetails.isCommunityApiAvailable()) {
            final boolean recursive = true;
            CommunityGetSubAlbumNamesResponseHandler handler = new CommunityGetSubAlbumNamesResponseHandler(CategoryItem.ROOT_ALBUM.getId()/*currentGallery.id*/, recursive);
            invokeWithRetries(thisUploadJob, handler, 2);
            if (handler.isSuccess()) {
                CommunityGetSubAlbumNamesResponseHandler.PiwigoCommunityGetSubAlbumNamesResponse rsp = (CommunityGetSubAlbumNamesResponseHandler.PiwigoCommunityGetSubAlbumNamesResponse) handler.getResponse();
                return rsp.getAlbumNames();
            } else {
                postNewResponse(thisUploadJob.getJobId(), new PiwigoPrepareUploadFailedResponse(getNextMessageId(), handler.getResponse()));
            }
        } else {
            AlbumGetSubAlbumNamesResponseHandler handler = new AlbumGetSubAlbumNamesResponseHandler(CategoryItem.ROOT_ALBUM.getId()/*currentGallery.id*/, true);
            invokeWithRetries(thisUploadJob, handler, 2);
            if (handler.isSuccess()) {
                AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse rsp = (AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) handler.getResponse();
                return rsp.getAlbumNames();
            } else {
                postNewResponse(thisUploadJob.getJobId(), new PiwigoPrepareUploadFailedResponse(getNextMessageId(), handler.getResponse()));
            }
        }
        return null;
    }

    public void saveStateToDisk(UploadJob thisUploadJob) {
        IOUtils.saveObjectToFile(getJobStateFile(this, thisUploadJob.isRunInBackground(), thisUploadJob.getJobId()), thisUploadJob);
    }

    private boolean deleteTemporaryUploadAlbum(UploadJob thisUploadJob) {

        if (thisUploadJob.getFilesNotYetUploaded().size() == 0 && thisUploadJob.getTemporaryUploadAlbum() < 0) {
            throw new IllegalStateException("Cannot delete upload album when job is still incomplete");
        }
        // all files were uploaded successfully.
        //delete temporary hidden album
        AlbumDeleteResponseHandler albumDelHandler = new AlbumDeleteResponseHandler(thisUploadJob.getTemporaryUploadAlbum());
        invokeWithRetries(thisUploadJob, albumDelHandler, 2);
        if (!albumDelHandler.isSuccess()) {
            // notify the listener of the final error we received from the server
            postNewResponse(thisUploadJob.getJobId(), new PiwigoCleanupPostUploadFailedResponse(getNextMessageId(), albumDelHandler.getResponse()));
        } else {
            thisUploadJob.setTemporaryUploadAlbum(-1);
        }
        return albumDelHandler.isSuccess();
    }

    private boolean createTemporaryUploadAlbum(UploadJob thisUploadJob) {

        long uploadAlbumId = thisUploadJob.getTemporaryUploadAlbum();

        if (uploadAlbumId < 0) {
            // create temporary hidden album
            UploadAlbumCreateResponseHandler albumGenHandler = new UploadAlbumCreateResponseHandler(thisUploadJob.getUploadToCategory());
            invokeWithRetries(thisUploadJob, albumGenHandler, 2);
            if (albumGenHandler.isSuccess()) {
                uploadAlbumId = ((AlbumCreateResponseHandler.PiwigoAlbumCreatedResponse) albumGenHandler.getResponse()).getNewAlbumId();
            }
            if (!albumGenHandler.isSuccess() || uploadAlbumId < 0) {
                // notify the listener of the final error we received from the server
                postNewResponse(thisUploadJob.getJobId(), new PiwigoPrepareUploadFailedResponse(getNextMessageId(), albumGenHandler.getResponse()));
                return false;
            } else {
                thisUploadJob.setTemporaryUploadAlbum(uploadAlbumId);
            }
        }
        return true;
    }

    private void processFindPreexistingImagesResponse(UploadJob thisUploadJob, ImageFindExistingImagesResponseHandler.PiwigoFindExistingImagesResponse response, List<Long> orphans) {
        HashMap<String, Long> preexistingItemsMap = response.getExistingImages();
        ArrayList<File> filesExistingOnServerAlready = new ArrayList<>();
        HashMap<File, Long> resourcesToRetrieve = new HashMap<>();

        // is name or md5sum used for uniqueness on this server?
        boolean nameUnique = "name".equals(prefs.getString(getString(R.string.preference_gallery_unique_id_key), getResources().getString(R.string.preference_gallery_unique_id_default)));
        Map<File, String> uniqueIdsSet;
        if (nameUnique) {
            uniqueIdsSet = thisUploadJob.getFileToFilenamesMap();
        } else {
            uniqueIdsSet = thisUploadJob.getFileChecksums();
        }

        for (Map.Entry<File, String> fileCheckSumEntry : uniqueIdsSet.entrySet()) {
            String uploadedFileUid = fileCheckSumEntry.getValue(); // usually MD5Sum (less chance of collision).

            if (preexistingItemsMap.containsKey(uploadedFileUid)) {
                File fileFoundOnServer = fileCheckSumEntry.getKey();
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
            postNewResponse(thisUploadJob.getJobId(), new PiwigoUploadFileFilesExistAlreadyResponse(getNextMessageId(), filesExistingOnServerAlready));
        }

        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(thisUploadJob.getConnectionPrefs());

        Set<String> multimediaExtensionList = AlbumViewPreferences.getKnownMultimediaExtensions(prefs, this);
        for (Map.Entry<File, Long> entry : resourcesToRetrieve.entrySet()) {

            long imageId = entry.getValue();
            if(orphans.contains(imageId)) {
                ResourceItem item = new ResourceItem(imageId, null, null, null, null, null);
                item.setFileChecksum(uniqueIdsSet.get(entry.getKey()));
                item.setLinkedAlbums(new HashSet<Long>(1));
                thisUploadJob.addFileUploaded(entry.getKey(), item);
            } else {
                ImageGetInfoResponseHandler<ResourceItem> getImageInfoHandler = new ImageGetInfoResponseHandler<>(new ResourceItem(imageId, null, null, null, null, null), multimediaExtensionList);
                int allowedAttempts = 2;
                boolean success = false;
                while (!success && allowedAttempts > 0) {
                    allowedAttempts--;
                    // this is blocking
                    getImageInfoHandler.invokeAndWait(this, thisUploadJob.getConnectionPrefs());
                    if (getImageInfoHandler.isSuccess()) {
                        success = true;
                        BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse rsp = (BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse) getImageInfoHandler.getResponse();
                        thisUploadJob.addFileUploaded(entry.getKey(), rsp.getResource());
                    } else if (sessionDetails.isUseCommunityPlugin() && getImageInfoHandler.getResponse() instanceof PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) {
                        PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse rsp = (PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) getImageInfoHandler.getResponse();
                        if (rsp.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                            success = true; // image is on the server, but not yet approved.
                            thisUploadJob.addFileUploaded(entry.getKey(), null);
                            postNewResponse(thisUploadJob.getJobId(), new PiwigoUploadProgressUpdateResponse(getNextMessageId(), entry.getKey(), thisUploadJob.getUploadProgress(entry.getKey())));
                        } else {
                            recordServerCallError(getImageInfoHandler, thisUploadJob.getConnectionPrefs());
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
            invokeWithRetries(thisUploadJob, orphanListHandler, 2);
            if (orphanListHandler.isSuccess()) {
                ImagesListOrphansResponseHandler.PiwigoGetOrphansResponse resp = (ImagesListOrphansResponseHandler.PiwigoGetOrphansResponse) orphanListHandler.getResponse();
                if (resp.getTotalCount() > resp.getResources().size()) {
                    postNewResponse(thisUploadJob.getJobId(), new PiwigoPrepareUploadFailedResponse(getNextMessageId(), new PiwigoResponseBufferingHandler.CustomErrorResponse(thisUploadJob.getJobId(), getString(R.string.upload_error_too_many_orphaned_files_exist_on_server))));
                    return null;
                } else {
                    orphans = resp.getResources();
                }
            }
            postNewResponse(thisUploadJob.getJobId(), new PiwigoPrepareUploadFailedResponse(getNextMessageId(), new PiwigoResponseBufferingHandler.CustomErrorResponse(thisUploadJob.getJobId(), getString(R.string.upload_error_orphaned_file_retrieval_failed))));
        } else {
            orphans = new ArrayList<>(0);
            thisUploadJob.recordError(new Date(), getString(R.string.upload_error_orphaned_file_retrieval_unavailable));
        }

        return orphans;
    }

    private void notifyListenersOfCustomErrorUploadingFile(UploadJob thisUploadJob, File fileBeingUploaded, String errorMessage) {
        long jobId = thisUploadJob.getJobId();
        PiwigoResponseBufferingHandler.CustomErrorResponse errorResponse = new PiwigoResponseBufferingHandler.CustomErrorResponse(jobId, errorMessage);
        PiwigoUploadFileAddToAlbumFailedResponse r1 = new PiwigoUploadFileAddToAlbumFailedResponse(getNextMessageId(), fileBeingUploaded, errorResponse);
        postNewResponse(jobId, r1);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private File compressVideo(UploadJob uploadJob, File rawVideo) {

        ExoPlayerCompression compressor = new ExoPlayerCompression();
        ExoPlayerCompression.CompressionParameters compressionSettings = new ExoPlayerCompression.CompressionParameters();

        double desiredBitratePerPixelPerSec = uploadJob.getVideoCompressionParams().getQuality();
        int desiredAudioBitrate = uploadJob.getVideoCompressionParams().getAudioBitrate();
        compressionSettings.getVideoCompressionParameters().setWantedBitRatePerPixelPerSecond(desiredBitratePerPixelPerSec);
        compressionSettings.getAudioCompressionParameters().setBitRate(desiredAudioBitrate);

        File outputVideo = uploadJob.addCompressedFile(this, rawVideo, compressionSettings.getOutputFileMimeType());
        UploadFileCompressionListener listener = new UploadFileCompressionListener(this, uploadJob.getJobId(), rawVideo, outputVideo);

        compressor.invokeFileCompression(this, rawVideo, outputVideo, listener, compressionSettings);

        while (!listener.isCompressionComplete() && null == listener.getCompressionError()) {
            try {
                synchronized (listener) {
                    listener.wait(1000);
                    if (uploadJob.isCancelUploadAsap()) {
                        compressor.cancel();
                        return null;
                    }
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Listener awoken!");
                // either spurious wakeup or the upload job wished to be cancelled.
            }
        }
        if (listener.getCompressionError() != null) {
            if (outputVideo.exists()) {
                if (!outputVideo.delete()) {
                    Crashlytics.log(Log.ERROR, TAG, "Unable to delete corrupt compressed file.");
                }
            }

            Exception e = listener.getCompressionError();
            if (listener.isUnsupportedVideoFormat() && uploadJob.isAllowUploadOfRawVideosIfIncompressible()) {
                Bundle b = new Bundle();
                b.putString("file_ext", IOUtils.getFileExt(rawVideo.getName()));
                FirebaseAnalytics.getInstance(this).logEvent("incompressible_video_encountered", b);
                uploadJob.markFileAsCompressed(rawVideo);
                outputVideo = rawVideo;
            } else {
                Crashlytics.logException(e);
                postNewResponse(uploadJob.getJobId(), new PiwigoUploadFileLocalErrorResponse(getNextMessageId(), rawVideo, e));
                uploadJob.cancelFileUpload(rawVideo);
            }
        }
        return outputVideo;
    }

    private File compressImage(UploadJob uploadJob, File rawImage) {

        File outputPhoto = null;

        try {
            UploadJob.ImageCompressionParams compressionParams = uploadJob.getImageCompressionParams();
            String format = compressionParams.getOutputFormat();
            Bitmap.CompressFormat outputFormat;
            if ("jpeg".equals(format)) {
                outputFormat = Bitmap.CompressFormat.JPEG;
            } else if ("webp".equals(format)) {
                outputFormat = Bitmap.CompressFormat.WEBP;
            } else if ("png".equals(format)) {
                outputFormat = Bitmap.CompressFormat.PNG;
            } else {
                throw new IllegalArgumentException("Unsupported image compression format : " + format);
            }

            outputPhoto = uploadJob.addCompressedFile(this, rawImage, "image/" + format);

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(rawImage.getAbsolutePath(), options);
            int imgWidth = options.outWidth;
            int imgHeight = options.outHeight;

            int maxWidth = compressionParams.getMaxWidth();
            int maxHeight = compressionParams.getMaxHeight();

            if (maxWidth < 0) {
                maxWidth = imgWidth;
            }
            if (maxHeight < 0) {
                maxHeight = imgHeight;
            }

            outputPhoto = new Compressor(this)
                    .setMaxHeight(maxHeight)
                    .setMaxWidth(maxWidth)
                    .setQuality(uploadJob.getImageCompressionParams().getQuality())
                    .setCompressFormat(outputFormat)
                    .setDestinationDirectoryPath(outputPhoto.getParent())
                    .compressToFile(rawImage, outputPhoto.getName());

            if (outputFormat == Bitmap.CompressFormat.JPEG) {
                ExifInterface originalExifData = new ExifInterface(rawImage.getAbsolutePath());
                ExifInterface newExifData = new ExifInterface(outputPhoto.getAbsolutePath());
                Field[] fields = ExifInterface.class.getFields();
                try {
                    for (Field f : fields) {
                        if (f.getName().startsWith("TAG_")) {
                            String tagName = f.get(originalExifData).toString();
                            String tagValue = originalExifData.getAttribute(tagName);
                            if (tagValue != null) {
                                newExifData.setAttribute(tagName, tagValue);
                            }
                        }
                    }
                } catch (IllegalAccessException e) {
                    throw new IOException("Unable to migrate EXIF information");
                }
                newExifData.saveAttributes();
            }

        } catch (IOException e) {
            if (outputPhoto != null && outputPhoto.exists()) {
                if (!outputPhoto.delete()) {
                    onFileDeleteFailed(tag, outputPhoto, "compressed image - post compression");
                }
            }
            Crashlytics.logException(e);
            postNewResponse(uploadJob.getJobId(), new PiwigoUploadFileLocalErrorResponse(getNextMessageId(), rawImage, e));
            return null;
        }
        return outputPhoto;
    }

    private void uploadFilesInJob(int maxChunkUploadAutoRetries, UploadJob thisUploadJob, ArrayList<CategoryItemStub> availableAlbumsOnServer) {

        long jobId = thisUploadJob.getJobId();
        byte[] chunkBuffer = buildSensibleBuffer();

        Set<Long> allServerAlbumIds = null;
        if (availableAlbumsOnServer != null) {
            allServerAlbumIds = new HashSet<>(availableAlbumsOnServer.size());
            for (CategoryItemStub cat : availableAlbumsOnServer) {
                allServerAlbumIds.add(cat.getId());
            }
        }

        for (File fileForUpload : thisUploadJob.getFilesForUpload()) {

            boolean isHaveUploadedCompressedFile = false;

            if (!fileForUpload.exists()) {
                thisUploadJob.cancelFileUpload(fileForUpload);
            }

            if (thisUploadJob.needsUpload(fileForUpload)) {
                File compressedFile = null;
                if (thisUploadJob.isVideo(fileForUpload)) {
                    // it is compression wanted, and it this particular video compressible.
                    if (thisUploadJob.isCompressVideosBeforeUpload() && thisUploadJob.canCompressVideoFile(fileForUpload)) {
                        //Check if we've already compressed it
                        compressedFile = getCompressedVersionOfFileToUpload(thisUploadJob, fileForUpload);
                        if (thisUploadJob.isUploadProcessNotYetStarted(fileForUpload)) {
                            // need to compress this file
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                                // compression only possible for Android API 18 and up.
                                compressedFile = compressVideo(thisUploadJob, fileForUpload);
                            }
                        }
                    }
                } else if (thisUploadJob.isPhoto(fileForUpload)) {
                    if (thisUploadJob.isCompressPhotosBeforeUpload()) {
                        compressedFile = getCompressedVersionOfFileToUpload(thisUploadJob, fileForUpload);
                        if (thisUploadJob.isUploadProcessNotYetStarted(fileForUpload)) {
                            // need to compress this file
                            compressedFile = compressImage(thisUploadJob, fileForUpload);
                        }
                    }
                }
                if (compressedFile != null) {
                    // we use this checksum to check the file was uploaded successfully
                    thisUploadJob.addFileChecksum(fileForUpload, compressedFile);
                    thisUploadJob.markFileAsCompressed(fileForUpload);
                    saveStateToDisk(thisUploadJob);
                }
            }

            if (thisUploadJob.isCancelUploadAsap()) {
                return;
            }

            if (thisUploadJob.needsUpload(fileForUpload)) {
                if (thisUploadJob.isFileCompressed(fileForUpload)) {
                    File compressedVideoFile = thisUploadJob.getCompressedFile(fileForUpload);
                    uploadFileData(thisUploadJob, fileForUpload, compressedVideoFile, chunkBuffer, maxChunkUploadAutoRetries);
                    isHaveUploadedCompressedFile = thisUploadJob.needsVerification(fileForUpload); // if the file uploaded all chunks
                } else {
                    uploadFileData(thisUploadJob, fileForUpload, fileForUpload, chunkBuffer, maxChunkUploadAutoRetries);
                }

            }

            if (thisUploadJob.isCancelUploadAsap()) {
                return;
            }

            saveStateToDisk(thisUploadJob);

            if (thisUploadJob.needsVerification(fileForUpload)) {
                verifyUploadedFileData(thisUploadJob, fileForUpload);
                isHaveUploadedCompressedFile &= thisUploadJob.isUploadedFileVerified(fileForUpload);
            }

            if (thisUploadJob.isCancelUploadAsap()) {
                return;
            }

            saveStateToDisk(thisUploadJob);

            if (isHaveUploadedCompressedFile && thisUploadJob.isUploadVerified(fileForUpload)) {
                // delete the temporarily created compressed file.
                File compressedVideoFile = thisUploadJob.getCompressedFile(fileForUpload);
                if (compressedVideoFile != null && !compressedVideoFile.delete()) {
                    onFileDeleteFailed(tag, compressedVideoFile, "compressed video - post upload");
                }
            }

            if (thisUploadJob.needsConfiguration(fileForUpload)) {
                configureUploadedFileDetails(thisUploadJob, jobId, fileForUpload, allServerAlbumIds);
            }

            saveStateToDisk(thisUploadJob);

            if (thisUploadJob.isCancelUploadAsap()) {
                return;
            }

            // Once added to album its too late the cancel the upload.
//            if (!thisUploadJob.isFileUploadStillWanted(fileForUpload)) {
//                thisUploadJob.markFileAsNeedsDelete(fileForUpload);
//            }

            if (thisUploadJob.needsDelete(fileForUpload)) {
                if (deleteUploadedResourceFromServer(thisUploadJob, thisUploadJob.getUploadedFileResource(fileForUpload))) {
                    thisUploadJob.markFileAsDeleted(fileForUpload);
                    // notify the listener that upload has been cancelled for this file
                    postNewResponse(jobId, new FileUploadCancelledResponse(getNextMessageId(), fileForUpload));
                } else {
                    //TODO notify user the uploaded file couldn't be deleted - needs manual intervention to remove it. Will be handled on Retry?
                }
            }

            if (thisUploadJob.needsDeleteAndThenReUpload(fileForUpload)) {
                if (deleteUploadedResourceFromServer(thisUploadJob, thisUploadJob.getUploadedFileResource(fileForUpload))) {
                    thisUploadJob.clearUploadProgress(fileForUpload);
                    //TODO notify user that the file bytes were deleted and upload must be started over
                } else {
                    //TODO notify user the uploaded file couldn't be deleted - needs manual intervention to remove it. Will be handled on Retry?
                }
            }

            saveStateToDisk(thisUploadJob);

        }
    }

    protected File getCompressedVersionOfFileToUpload(UploadJob uploadJob, File fileForUpload) {
        File compressedFile = uploadJob.getCompressedFile(fileForUpload);
        if (compressedFile != null && compressedFile.exists() && !uploadJob.isFileCompressed(fileForUpload)) {
            if (!compressedFile.delete()) { // the compression failed - delete file and allow restart
                onFileDeleteFailed(tag, compressedFile, "compressed file - pre upload");
            }
            compressedFile = null; // clear reference to trigger re-compression
        }
        return compressedFile;
    }

    private ResourceItem uploadFileInChunks(UploadJob thisUploadJob, byte[] uploadChunkBuffer, File uploadJobKey, File fileForUpload, String uploadName, int maxChunkUploadAutoRetries) throws IOException {

        long totalBytesInFile = fileForUpload.length();
        long fileBytesUploaded = 0;
        long chunkId = 0;
        int bytesOfDataInChunk;
        String uploadToFilename = uploadName;
        long chunkCount;
        long chunksUploadedAlready = 0;


        String newChecksum = thisUploadJob.getFileChecksum(uploadJobKey);
        if (isUseFilenamesOverMd5ChecksumForUniqueness()) {
            newChecksum = fileForUpload.getName();
        }
        // pre-set the upload progress through file to where we got to last time.
        UploadJob.PartialUploadData skipChunksData = thisUploadJob.getChunksAlreadyUploadedData(uploadJobKey);
        if (skipChunksData != null) {
            thisUploadJob.deleteChunksAlreadyUploadedData(uploadJobKey);
            if (ObjectUtils.areEqual(skipChunksData.getFileChecksum(), newChecksum)) {
                // If the checksums still match (file to upload hasn't been altered)
                chunksUploadedAlready = skipChunksData.getCountChunksUploaded();
                chunkId = chunksUploadedAlready;
                fileBytesUploaded = skipChunksData.getBytesUploaded();
                uploadToFilename = skipChunksData.getUploadName();
            }
        }
        // chunk count is chunks in part of file left plus uploaded chunk count.
        chunkCount = (long) Math.ceil((double) (totalBytesInFile - fileBytesUploaded) / uploadChunkBuffer.length);
        chunkCount += chunksUploadedAlready;


        String fileMimeType = null; // MimeTypeMap.getSingleton().getMimeTypeFromExtension(IOUtils.getFileExt(fileForUpload.getName()));
        //FIXME get the fileMimeType from somewhere - why?!

        BufferedInputStream bis = null;
        Pair<Boolean, ResourceItem> lastChunkUploadResult = null;

        try {
            bis = new BufferedInputStream(new FileInputStream(fileForUpload));
            if (fileBytesUploaded > 0) {
                if (fileBytesUploaded != bis.skip(fileBytesUploaded)) {
                    throw new IOException("Unable to skip through previously uploaded bytes in file. File has likely been altered");
                }
            }

            do {
                if (!thisUploadJob.isCancelUploadAsap() && thisUploadJob.isFileUploadStillWanted(uploadJobKey)) {

                    bytesOfDataInChunk = bis.read(uploadChunkBuffer);

                    if (bytesOfDataInChunk > 0) {

//                        String data = Base64.encodeToString(buffer, 0, bytesOfData, Base64.DEFAULT);
                        ByteArrayInputStream data = new ByteArrayInputStream(uploadChunkBuffer, 0, bytesOfDataInChunk);

                        long uploadToAlbumId = thisUploadJob.getTemporaryUploadAlbum();
                        if (uploadToAlbumId < 0) {
                            uploadToAlbumId = thisUploadJob.getUploadToCategory();
                        }

                        UploadFileChunk currentUploadFileChunk = new UploadFileChunk(thisUploadJob.getJobId(), fileForUpload, uploadToFilename, uploadToAlbumId, data, chunkId, chunkCount, fileMimeType);
                        lastChunkUploadResult = uploadStreamChunk(thisUploadJob, uploadJobKey, currentUploadFileChunk, maxChunkUploadAutoRetries);

                        chunkId++;
                        @SuppressWarnings("ConstantConditions") boolean chunkUploadedOk = lastChunkUploadResult.first;
                        if (chunkUploadedOk) {
                            fileBytesUploaded += bytesOfDataInChunk;
                            String fileChecksum = thisUploadJob.getFileChecksum(uploadJobKey);
                            if (isUseFilenamesOverMd5ChecksumForUniqueness()) {
                                fileChecksum = fileForUpload.getName();
                            }
                            thisUploadJob.markFileAsPartiallyUploaded(uploadJobKey, uploadToFilename, fileChecksum, fileForUpload.length(), fileBytesUploaded, chunkId);
                            saveStateToDisk(thisUploadJob);
                            postNewResponse(thisUploadJob.getJobId(), new PiwigoUploadProgressUpdateResponse(getNextMessageId(), uploadJobKey, thisUploadJob.getUploadProgress(uploadJobKey)));
                        } else {
                            bytesOfDataInChunk = -1; // don't upload the rest of the chunks.
                        }

                    }

                } else {
                    deleteCompressedVersionIfExists(thisUploadJob, uploadJobKey);
                    bytesOfDataInChunk = -1;
                }
            } while (bytesOfDataInChunk >= 0);

            if (fileBytesUploaded < totalBytesInFile) {
                if (!thisUploadJob.isFileUploadStillWanted(uploadJobKey)) {
                    // notify the listener that upload has been cancelled for this file (at user's request)
                    postNewResponse(thisUploadJob.getJobId(), new FileUploadCancelledResponse(getNextMessageId(), uploadJobKey));
                }
            }
        } finally {
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException e) {
                    Crashlytics.logException(e);
                    if (BuildConfig.DEBUG) {
                        Log.e(tag, "Exception on closing File input stream", e);
                    }
                }
            }
        }

        return lastChunkUploadResult == null ? null : lastChunkUploadResult.second;
    }

    private void configureUploadedFileDetails(UploadJob thisUploadJob, long jobId, File fileForUpload, Set<Long> allServerAlbumIds) {
        if (thisUploadJob.isFileUploadStillWanted(fileForUpload)) {

            ResourceItem uploadedResource = thisUploadJob.getUploadedFileResource(fileForUpload);
            if (uploadedResource != null) {
                PiwigoResponseBufferingHandler.BaseResponse response = updateImageInfoAndPermissions(thisUploadJob, fileForUpload, uploadedResource, allServerAlbumIds);

                if (response instanceof PiwigoResponseBufferingHandler.ErrorResponse) {

                    // notify the listener of the final error we received from the server
                    postNewResponse(jobId, new PiwigoUploadFileAddToAlbumFailedResponse(getNextMessageId(), fileForUpload, response));

                } else {
                    thisUploadJob.markFileAsConfigured(fileForUpload);
                    postNewResponse(thisUploadJob.getJobId(), new PiwigoUploadProgressUpdateResponse(getNextMessageId(), fileForUpload, thisUploadJob.getUploadProgress(fileForUpload)));
                }
            }
        } else {
            thisUploadJob.markFileAsNeedsDelete(fileForUpload);
        }
    }

    private void verifyUploadedFileData(UploadJob thisUploadJob, File fileForUpload) {
        if (thisUploadJob.isFileUploadStillWanted(fileForUpload)) {

            Boolean verifiedUploadedFile = verifyFileNotCorrupted(thisUploadJob, thisUploadJob.getUploadedFileResource(fileForUpload));
            if (verifiedUploadedFile == null) {
                // notify the listener of the final error we received from the server
                String errorMsg = getString(R.string.error_upload_file_verification_failed, fileForUpload.getName());
                notifyListenersOfCustomErrorUploadingFile(thisUploadJob, fileForUpload, errorMsg);
            } else if (verifiedUploadedFile) {
                thisUploadJob.markFileAsVerified(fileForUpload);
                postNewResponse(thisUploadJob.getJobId(), new PiwigoUploadProgressUpdateResponse(getNextMessageId(), fileForUpload, thisUploadJob.getUploadProgress(fileForUpload)));
                deleteCompressedVersionIfExists(thisUploadJob, fileForUpload);
            } else {
                // the file verification failed - this file is corrupt (needs delete but then re-upload).
                thisUploadJob.markFileAsCorrupt(fileForUpload);
            }
        } else {
            thisUploadJob.markFileAsNeedsDelete(fileForUpload);
        }
    }

    private void deleteCompressedVersionIfExists(UploadJob thisUploadJob, File fileForUpload) {
        File compressedFile = getCompressedVersionOfFileToUpload(thisUploadJob, fileForUpload);
        if (compressedFile != null && compressedFile.exists()) {
            if (!compressedFile.delete()) {
                onFileDeleteFailed(TAG, compressedFile, "compressed file after upload of file (or upload cancelled)");
            }
        }
    }

    private void uploadFileData(UploadJob thisUploadJob, File uploadJobKey, File fileForUpload, byte[] chunkBuffer, int maxChunkUploadAutoRetries) {
        long jobId = thisUploadJob.getJobId();

        if (!thisUploadJob.isCancelUploadAsap() && thisUploadJob.isFileUploadStillWanted(uploadJobKey)) {

            postNewResponse(jobId, new PiwigoStartUploadFileResponse(getNextMessageId(), uploadJobKey));

            try {
                String filename = fileForUpload.getName();
                String ext = IOUtils.getFileExt(filename);


                if (ext.length() == 0) {
                    thisUploadJob.cancelFileUpload(uploadJobKey);
                    // notify the listener of the final error we received from the server
                    String errorMsg = getString(R.string.error_upload_file_ext_missing_pattern, fileForUpload.getName());
                    notifyListenersOfCustomErrorUploadingFile(thisUploadJob, uploadJobKey, errorMsg);
                }

                if (thisUploadJob.isFileUploadStillWanted(uploadJobKey)) {
                    String tempUploadName = "PiwigoClient_Upload_" + random.nextLong() + '.' + ext;

                    ResourceItem uploadedResource = uploadFileInChunks(thisUploadJob, chunkBuffer, uploadJobKey, fileForUpload, tempUploadName, maxChunkUploadAutoRetries);

                    if (uploadedResource != null) {
                        // this should ALWAYS be the case!

                        uploadedResource.setName(fileForUpload.getName());
                        uploadedResource.setParentageChain(thisUploadJob.getUploadToCategoryParentage(), thisUploadJob.getUploadToCategory());
                        uploadedResource.setPrivacyLevel(thisUploadJob.getPrivacyLevelWanted());
                        uploadedResource.setFileChecksum(thisUploadJob.getFileChecksum(uploadJobKey));

                        long lastModifiedTime = uploadJobKey.lastModified();
                        if (lastModifiedTime > 0) {
                            Date lastModDate = new Date(lastModifiedTime);
                            uploadedResource.setCreationDate(lastModDate);
                        }

                        setUploadedImageDetailsFromExifData(fileForUpload, uploadedResource);

                        thisUploadJob.addFileUploaded(uploadJobKey, uploadedResource);

                    } else if (!thisUploadJob.isFileUploadStillWanted(uploadJobKey)) {
                        //This block gets entered when the upload is cancelled for a file
                    } else {
                        // notify the listener of the final error we received from the server
                        String errorMsg = getString(R.string.error_upload_file_chunk_upload_failed_after_retries, maxChunkUploadAutoRetries);
                        notifyListenersOfCustomErrorUploadingFile(thisUploadJob, uploadJobKey, errorMsg);
                    }
                }
            } catch (FileNotFoundException e) {
                Crashlytics.logException(e);
                postNewResponse(jobId, new PiwigoUploadFileLocalErrorResponse(getNextMessageId(), uploadJobKey, e));
            } catch (final IOException e) {
                Crashlytics.logException(e);
                postNewResponse(jobId, new PiwigoUploadFileLocalErrorResponse(getNextMessageId(), uploadJobKey, e));
            }
        }
    }

    private void setUploadedImageDetailsFromExifData(File fileForUpload, ResourceItem uploadedResource) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(fileForUpload);
            Directory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if(directory != null) {
                Date creationDate = directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
                if (creationDate != null) {
                    uploadedResource.setCreationDate(creationDate);
                }
                String imageDescription = directory.getString(ExifSubIFDDirectory.TAG_IMAGE_DESCRIPTION);
                if (imageDescription != null) {
                    uploadedResource.setName(imageDescription);
                }
            }
            directory = metadata.getFirstDirectoryOfType(IptcDirectory.class);
            if(directory != null) {
                Date creationDate = directory.getDate(IptcDirectory.TAG_DATE_CREATED);
                if (creationDate != null) {
                    uploadedResource.setCreationDate(creationDate);
                }
                String imageDescription = directory.getString(IptcDirectory.TAG_CAPTION);
                if (imageDescription != null) {
                    uploadedResource.setName(imageDescription);
                }
            }
        } catch (ImageProcessingException e) {
            Crashlytics.logException(e);
            // ignore for now
            if (BuildConfig.DEBUG) {
                Log.e(tag, "Error parsing EXIF data : sinking", e);
            } else {
                Crashlytics.log(Log.ERROR, tag, "Error parsing EXIF data : sinking");
            }
        } catch(FileNotFoundException e) {
            Crashlytics.log(Log.WARN, tag, "File Not found - Unable to parse EXIF data : sinking");
        } catch (IOException e) {
            Crashlytics.logException(e);
            // ignore for now
            if (BuildConfig.DEBUG) {
                Log.e(tag, "Error parsing EXIF data : sinking", e);
            } else {
                Crashlytics.log(Log.ERROR, tag, "Error parsing EXIF data : sinking");
            }
        }
    }

    private boolean deleteUploadedResourceFromServer(UploadJob uploadJob, ResourceItem uploadedResource) {

        if(uploadedResource == null) {
            Log.w(TAG, "cannot delete uploaded resource from server, as we are missing a reference to it (presumably upload has not been started)!");
            return true;
        }
        if (PiwigoSessionDetails.isAdminUser(uploadJob.getConnectionPrefs())) {
            ImageDeleteResponseHandler<ResourceItem> imageDeleteHandler = new ImageDeleteResponseHandler<>(uploadedResource);
            invokeWithRetries(uploadJob, imageDeleteHandler, 2);
            return imageDeleteHandler.isSuccess();
        } else {
            // community plugin... can't delete files... have to pretend we did...
            return true;
        }
    }

    private Boolean verifyFileNotCorrupted(UploadJob uploadJob, ResourceItem uploadedResource) {

        ImageCheckFilesResponseHandler<ResourceItem> imageFileCheckHandler = new ImageCheckFilesResponseHandler<>(uploadedResource);
        invokeWithRetries(uploadJob, imageFileCheckHandler, 2);
        Boolean val = imageFileCheckHandler.isSuccess() ? imageFileCheckHandler.isFileMatch() : null;
        if (Boolean.FALSE.equals(val)) {
            Set<String> multimediaExtensionList = AlbumViewPreferences.getKnownMultimediaExtensions(prefs, this);
            ResourceItem uploadedResourceDummy = new ResourceItem(uploadedResource.getId(), uploadedResource.getName(), null, null, null, null);
            ImageGetInfoResponseHandler<ResourceItem> imageDetailsHandler = new ImageGetInfoResponseHandler<>(uploadedResourceDummy, multimediaExtensionList);
            invokeWithRetries(uploadJob, imageDetailsHandler, 2);
            if (imageDetailsHandler.isSuccess()) {
                BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse rsp = (ImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse) imageDetailsHandler.getResponse();
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

    private PiwigoResponseBufferingHandler.BaseResponse updateImageInfoAndPermissions(UploadJob thisUploadJob, File fileForUpload, ResourceItem uploadedResource, Set<Long> allServerAlbumIds) {
        uploadedResource.getLinkedAlbums().add(thisUploadJob.getUploadToCategory());
        if(thisUploadJob.getTemporaryUploadAlbum() > 0) {
            uploadedResource.getLinkedAlbums().remove(thisUploadJob.getTemporaryUploadAlbum());
        }
        // Don't update the tags because we aren't altering this aspect of the the image during upload and it (could) cause problems
        ImageUpdateInfoResponseHandler<ResourceItem> imageInfoUpdateHandler = new ImageUpdateInfoResponseHandler<>(uploadedResource, false);
        imageInfoUpdateHandler.setFilename(fileForUpload.getName());
        invokeWithRetries(thisUploadJob, imageInfoUpdateHandler, 2);
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
                invokeWithRetries(thisUploadJob, imageInfoUpdateHandler, 2);
            }
        }
        return imageInfoUpdateHandler.getResponse();
    }

    private byte[] buildSensibleBuffer() {
        int wantedUploadSizeInKbB = UploadPreferences.getMaxUploadChunkSizeMb(this, prefs);
        int bufferSizeBytes = 1024 * wantedUploadSizeInKbB; // 512Kb chunk size
//        bufferSizeBytes -= bufferSizeBytes % 3; // ensure 3 byte blocks so base64 encoded pieces fit together again.
        return new byte[bufferSizeBytes];
    }

    private Pair<Boolean, ResourceItem> uploadStreamChunk(UploadJob thisUploadJob, File uploadJobKey, UploadFileChunk currentUploadFileChunk, int maxChunkUploadAutoRetries) {

        // Attempt to upload this chunk of the file
        NewImageUploadFileChunkResponseHandler imageChunkUploadHandler = new NewImageUploadFileChunkResponseHandler(currentUploadFileChunk);
        invokeWithRetries(thisUploadJob, imageChunkUploadHandler, maxChunkUploadAutoRetries);

        ResourceItem uploadedResource = null;
        if (imageChunkUploadHandler.isSuccess()) {
            uploadedResource = ((NewImageUploadFileChunkResponseHandler.PiwigoUploadFileChunkResponse) imageChunkUploadHandler.getResponse()).getUploadedResource();
        }

        if (!imageChunkUploadHandler.isSuccess()) {
            // notify listener of failure
            postNewResponse(thisUploadJob.getJobId(), new PiwigoUploadFileChunkFailedResponse(getNextMessageId(), uploadJobKey, imageChunkUploadHandler.getResponse()));
        } else {
            if (currentUploadFileChunk.getChunkId() == 0) {
                thisUploadJob.markFileAsUploading(uploadJobKey);
            }
        }

        return new Pair<>(imageChunkUploadHandler.isSuccess(), uploadedResource);
    }

    private static class UploadFileCompressionListener implements ExoPlayerCompression.CompressionListener {

        private final File rawVideo;
        private final File compressedVideo;
        private boolean compressionComplete;
        private BasePiwigoUploadService uploadService;
        private long jobId;
        private Exception compressionError;

        public UploadFileCompressionListener(BasePiwigoUploadService uploadService, long jobId, File rawVideo, File compressedVideo) {
            this.uploadService = uploadService;
            this.jobId = jobId;
            this.rawVideo = rawVideo;
            this.compressedVideo = compressedVideo;
        }

        @Override
        public void onCompressionStarted() {

        }

        @Override
        public void onCompressionComplete() {
            uploadService.postNewResponse(jobId, new PiwigoVideoCompressionProgressUpdateResponse(getNextMessageId(), rawVideo, compressedVideo, 100));
            compressionComplete = true;
            // wake the main upload thread.
            synchronized (this) {
                notifyAll();
            }
        }

        public boolean isCompressionComplete() {
            return compressionComplete;
        }

        @Override
        public void onCompressionProgress(double compressionProgress, long mediaDurationMs) {
            int intCompProgress = (int) Math.round(compressionProgress);
            uploadService.postNewResponse(jobId, new PiwigoVideoCompressionProgressUpdateResponse(getNextMessageId(), rawVideo, compressedVideo, intCompProgress));
        }

        @Override
        public void onCompressionError(Exception e) {
            compressionError = e;
            // wake the main upload thread.
            synchronized (this) {
                notifyAll();
            }
        }

        public Exception getCompressionError() {
            return compressionError;
        }

        public boolean isUnsupportedVideoFormat() {
            if (compressionError instanceof ExoPlaybackException) {
                ExoPlaybackException err = (ExoPlaybackException) compressionError;
                return err.type == ExoPlaybackException.TYPE_SOURCE;
            }
            return false;
        }
    }

    public interface JobUploadListener {
        void onJobReadyToUpload(Context c, UploadJob thisUploadJob);
    }

    public static class MessageForUserResponse extends PiwigoResponseBufferingHandler.BaseResponse {
        private final String message;

        public MessageForUserResponse(long jobId, String message) {
            super(jobId, true);
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class PiwigoCleanupPostUploadFailedResponse extends PiwigoResponseBufferingHandler.BaseResponse {
        private final PiwigoResponseBufferingHandler.Response error;

        public PiwigoCleanupPostUploadFailedResponse(long jobId, PiwigoResponseBufferingHandler.Response error) {
            super(jobId, true);
            this.error = error;
        }

        public PiwigoResponseBufferingHandler.Response getError() {
            return error;
        }

        public long getJobId() {
            return getMessageId();
        }
    }

    public static class PiwigoPrepareUploadFailedResponse extends PiwigoResponseBufferingHandler.BaseResponse {

        private final PiwigoResponseBufferingHandler.Response error;

        public PiwigoPrepareUploadFailedResponse(long jobId, PiwigoResponseBufferingHandler.Response error) {
            super(jobId, true);
            this.error = error;
        }

        public PiwigoResponseBufferingHandler.Response getError() {
            return error;
        }

        public long getJobId() {
            return getMessageId();
        }
    }

    public static class PiwigoUploadProgressUpdateResponse extends PiwigoResponseBufferingHandler.BaseResponse {

        private final int progress;
        private final File fileForUpload;

        public PiwigoUploadProgressUpdateResponse(long jobId, File fileForUpload, int progress) {
            super(jobId, true);
            this.progress = progress;
            this.fileForUpload = fileForUpload;
        }

        public File getFileForUpload() {
            return fileForUpload;
        }

        public int getProgress() {
            return progress;
        }

        public long getJobId() {
            return getMessageId();
        }
    }

    public static class PiwigoVideoCompressionProgressUpdateResponse extends PiwigoResponseBufferingHandler.BaseResponse {

        private final int progress;
        private final File fileForUpload;
        private final File compressedFileUpload;

        public PiwigoVideoCompressionProgressUpdateResponse(long jobId, File fileForUpload, File compressedFileUpload, int progress) {
            super(jobId, true);
            this.progress = progress;
            this.fileForUpload = fileForUpload;
            this.compressedFileUpload = compressedFileUpload;
        }

        public File getFileForUpload() {
            return fileForUpload;
        }

        public File getCompressedFileUpload() {
            return compressedFileUpload;
        }

        public int getProgress() {
            return progress;
        }

        public long getJobId() {
            return getMessageId();
        }
    }

    public static class PiwigoUploadFileLocalErrorResponse extends PiwigoResponseBufferingHandler.BaseResponse implements PiwigoResponseBufferingHandler.ErrorResponse {

        private final Exception error;
        private final File fileForUpload;

        public PiwigoUploadFileLocalErrorResponse(long jobId, File fileForUpload, Exception error) {
            super(jobId, true);
            this.error = error;
            this.fileForUpload = fileForUpload;
        }

        public File getFileForUpload() {
            return fileForUpload;
        }

        public Exception getError() {
            return error;
        }

        public long getJobId() {
            return getMessageId();
        }
    }

    public static class PiwigoUploadFileAddToAlbumFailedResponse extends PiwigoResponseBufferingHandler.BaseResponse {

        private final PiwigoResponseBufferingHandler.Response error;
        private final File fileForUpload;

        public PiwigoUploadFileAddToAlbumFailedResponse(long jobId, File fileForUpload, PiwigoResponseBufferingHandler.Response error) {
            super(jobId, true);
            this.error = error;
            this.fileForUpload = fileForUpload;
        }

        public File getFileForUpload() {
            return fileForUpload;
        }

        public PiwigoResponseBufferingHandler.Response getError() {
            return error;
        }

        public long getJobId() {
            return getMessageId();
        }
    }

    public static class PiwigoUploadFileChunkFailedResponse extends PiwigoResponseBufferingHandler.BaseResponse {

        private final PiwigoResponseBufferingHandler.Response error;
        private final File fileForUpload;

        public PiwigoUploadFileChunkFailedResponse(long jobId, File fileForUpload, PiwigoResponseBufferingHandler.Response error) {
            super(jobId, true);
            this.error = error;
            this.fileForUpload = fileForUpload;
        }

        public File getFileForUpload() {
            return fileForUpload;
        }

        public PiwigoResponseBufferingHandler.Response getError() {
            return error;
        }

        public long getJobId() {
            return getMessageId();
        }
    }

    public static class PiwigoUploadFileFilesExistAlreadyResponse extends PiwigoResponseBufferingHandler.BaseResponse {

        private final ArrayList<File> existingFiles;

        public PiwigoUploadFileFilesExistAlreadyResponse(long jobId, ArrayList<File> existingFiles) {
            super(jobId, true);
            this.existingFiles = existingFiles;
        }

        public ArrayList<File> getExistingFiles() {
            return existingFiles;
        }

        public long getJobId() {
            return getMessageId();
        }
    }

    public static class PiwigoUploadFileJobCompleteResponse extends PiwigoResponseBufferingHandler.BaseResponse {

        private final UploadJob job;

        public PiwigoUploadFileJobCompleteResponse(long messageId, UploadJob job) {

            super(messageId, true);
            this.job = job;
        }

        public long getJobId() {
            return getMessageId();
        }

        public UploadJob getJob() {
            return job;
        }
    }

    public static class PiwigoStartUploadFileResponse extends PiwigoResponseBufferingHandler.BaseResponse {

        private final File fileForUpload;

        public PiwigoStartUploadFileResponse(long jobId, File fileForUpload) {
            super(jobId, true);
            this.fileForUpload = fileForUpload;
        }

        public File getFileForUpload() {
            return fileForUpload;
        }

        public long getJobId() {
            return getMessageId();
        }
    }

    public static class FileUploadCancelledResponse extends PiwigoResponseBufferingHandler.BaseResponse {

        private final File cancelledFile;

        public FileUploadCancelledResponse(long messageId, File cancelledFile) {
            super(messageId, true);
            this.cancelledFile = cancelledFile;
        }

        public File getCancelledFile() {
            return cancelledFile;
        }
    }
}
