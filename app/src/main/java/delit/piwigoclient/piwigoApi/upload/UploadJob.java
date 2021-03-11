package delit.piwigoclient.piwigoApi.upload;

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.MimeTypeFilter;
import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.ParcelUtils;
import delit.libs.util.IOUtils;
import delit.libs.util.progress.DividableProgressTracker;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.ResourceItem;

public class UploadJob implements Parcelable {
    //Total of this work must equal TOTAL WORK. I'm trying to keep this around 100 so its roughly percentages of time user waits. N.b. this isn't accurate as job size changes.
    public static final long WORK_DIVISION_CHECKSUM_PERC = 5;
    public static final long WORK_DIVISION_POST_CHECKED_FOR_EXISTING_FILES = 2;
    public static final long WORK_DIVISION_COMPRESS_AND_UPLOAD_PERC = 87;
    public static final long WORK_DIVISION_DELETE_TEMP_FOLDER = 5;
    public static final long WORK_DIVISION_POST_UPLOAD_CALLS = 1;
    public static final long TOTAL_WORK = WORK_DIVISION_CHECKSUM_PERC + WORK_DIVISION_POST_CHECKED_FOR_EXISTING_FILES + WORK_DIVISION_COMPRESS_AND_UPLOAD_PERC + WORK_DIVISION_DELETE_TEMP_FOLDER + WORK_DIVISION_POST_UPLOAD_CALLS;
    public static final String CHECKSUMS_CALCULATION_TASK = "Checksums calculation";
    public static final String FILES_CHUNKS_UPLOAD_TASK = "Files Chunks Upload";
    public static final String SINGLE_FILE_COMPRESSION_TASK = "file compression";

    public static final Creator<UploadJob> CREATOR = new Creator<UploadJob>() {
        @Override
        public UploadJob createFromParcel(Parcel in) {
            return new UploadJob(in);
        }

        @Override
        public UploadJob[] newArray(int size) {
            return new UploadJob[size];
        }
    };
    private static final String TAG = "UploadJob";
    private static final String OVERALL_DATA_UPLOAD_TASK = "All files Compression and Upload";
    private static final String OVERALL_JOB_TASK = "Overall Job";
    private static final int JOB_STATUS_STOPPED = 0;
    private static final int JOB_STATUS_SUBMITTED = 1;
    private static final int JOB_STATUS_RUNNING = 2;
    private static final int JOB_STATUS_FINISHED = 3;
    private final long jobId;
    private final long responseHandlerId;
    private final CategoryItemStub uploadToCategory;
    private final byte privacyLevelWanted;
    private final ConnectionPreferences.ProfilePreferences connectionPrefs;
    private final boolean isDeleteFilesAfterUpload;
    private final Map<Uri, FileUploadDetails> fileUploadDetails;
    private int jobConfigId = -1;
    private boolean runInBackground;
    private long temporaryUploadAlbumId = -1;
    private ProcessErrors generalErrors = new ProcessErrors();
    private VideoCompressionParams playableMediaCompressionParams;
    private ImageCompressionParams imageCompressionParams;
    private boolean allowUploadOfRawVideosIfIncompressible;
    private int jobStatus = JOB_STATUS_STOPPED;
    private volatile boolean cancelUploadAsap;
    private DocumentFile loadedFromFile;
    private boolean wasLastRunCancelled;
    private DividableProgressTracker overallProgressTracker;
    private int runAttempts;

    public UploadJob(ConnectionPreferences.ProfilePreferences connectionPrefs, long jobId, long responseHandlerId, Map<Uri, Long> filesForUploadAndBytes, CategoryItemStub destinationCategory, byte uploadedFilePrivacyLevel, boolean isDeleteFilesAfterUpload) {
        this.jobId = jobId;
        this.connectionPrefs = connectionPrefs;
        this.responseHandlerId = responseHandlerId;
        this.uploadToCategory = destinationCategory;
        this.privacyLevelWanted = uploadedFilePrivacyLevel;
        this.fileUploadDetails = buildFileUploadDetails(filesForUploadAndBytes);
        this.isDeleteFilesAfterUpload = isDeleteFilesAfterUpload;
    }

    protected UploadJob(Parcel in) {
        jobId = in.readLong();
        responseHandlerId = in.readLong();
        fileUploadDetails = buildFileUploadDetails(ParcelUtils.readArrayListOf(in, FileUploadDetails.class));
        uploadToCategory = ParcelUtils.readParcelable(in, CategoryItemStub.class);
        privacyLevelWanted = in.readByte();
        jobConfigId = in.readInt();
        runInBackground = ParcelUtils.readBool(in);
        connectionPrefs = ParcelUtils.readParcelable(in, ConnectionPreferences.ProfilePreferences.class);
        jobStatus = in.readInt();
        temporaryUploadAlbumId = in.readLong();
        generalErrors = ParcelUtils.readParcelable(in, ProcessErrors.class);
        playableMediaCompressionParams = ParcelUtils.readParcelable(in, UploadJob.VideoCompressionParams.class);
        imageCompressionParams = ParcelUtils.readParcelable(in, UploadJob.ImageCompressionParams.class);
        allowUploadOfRawVideosIfIncompressible = ParcelUtils.readBool(in);
        isDeleteFilesAfterUpload = ParcelUtils.readBool(in);
        overallProgressTracker = ParcelUtils.readValue(in, DividableProgressTracker.class);
        runAttempts = in.readInt();
    }

    private @NonNull
    Map<Uri, FileUploadDetails> buildFileUploadDetails(@Nullable List<FileUploadDetails> filesForUploadAndBytes) {
        if (filesForUploadAndBytes == null) {
            return new HashMap<>(0);
        }
        Map<Uri, FileUploadDetails> fileUploadDetails = new HashMap<>(filesForUploadAndBytes.size());
        for (FileUploadDetails item : filesForUploadAndBytes) {
            fileUploadDetails.put(item.getFileUri(), item);
        }
        return fileUploadDetails;
    }

    private @NonNull
    Map<Uri, FileUploadDetails> buildFileUploadDetails(@NonNull Map<Uri, Long> filesForUploadAndBytes) {
        Map<Uri, FileUploadDetails> fileUploadDetails = new HashMap<>(filesForUploadAndBytes.size());
        for (Map.Entry<Uri, Long> entry : filesForUploadAndBytes.entrySet()) {
            fileUploadDetails.put(entry.getKey(), new FileUploadDetails(entry.getKey(), entry.getValue()));
        }
        return fileUploadDetails;
    }

    private long calculateTotalCompressionAndUploadingWork(@NonNull Context context) {
        long total = 0;
        for (FileUploadDetails item : fileUploadDetails.values()) {
            Uri f = item.getFileUri();
            total += getUploadUploadProgressTicksForFile(f);
            if ((isPhoto(context, f) && isCompressPhotosBeforeUpload()) || (isPlayableMedia(context, f) && isCompressPlayableMediaBeforeUpload())) {
                total += getCompressionUploadProgressTicksForFile(f);
            }
        }
        return total;
    }

    public boolean hasProcessableFiles() {
        boolean hasFilesToUpload = !fileUploadDetails.isEmpty();
        if (hasFilesToUpload) {
            // at least one file isn't marked with error state
            for (FileUploadDetails fud : fileUploadDetails.values()) {
                if (!fud.isProcessingFailed()) {
                    return true;
                }
            }
        }
        return false;
    }

    public void cancelAllFailedUploads() {
        for (FileUploadDetails fud : fileUploadDetails.values()) {
            if (fud.isProcessingFailed()) {
                fud.cancelUpload();
            }
        }
    }

    public void resetProcessingErrors() {
        for (FileUploadDetails fud : fileUploadDetails.values()) {
            fud.allowProcessing();
        }
    }

    public void clearUploadErrors() {
        for (FileUploadDetails fud : fileUploadDetails.values()) {
            fud.clearErrors();
        }
    }

    public void deleteAnyCompressedFiles(@NonNull Context context) {
        for (FileUploadDetails fud : fileUploadDetails.values()) {
            Uri uri = fud.getCompressedFileUri();
            if (uri != null && !IOUtils.delete(context, uri)) {
                Logging.log(Log.WARN, TAG, "Unable to delete compressed file: %1$s", uri);
            }
        }
    }

    public CategoryItemStub getUploadToCategory() {
        return uploadToCategory;
    }

    public void resetProgressTrackers() {
        // will cause all to be rebuilt from the stored data.
        overallProgressTracker.rollbackProgress();
    }

    public Collection<FileUploadDetails> getFilesForUploadDetails() {
        return fileUploadDetails.values();
    }

    public boolean hasNeedOfTemporaryFolder() {
        for (FileUploadDetails fud : fileUploadDetails.values()) {
            if (!(fud.isAlreadyUploadedAndConfigured() || fud.isReadyForConfiguration() || fud.isUploadCancelled())) {
                return true;
            }
        }
        return false;
    }

    public double getOverallUploadProgress() {
        if(overallProgressTracker == null) {
            return 0;
        }
        return overallProgressTracker.getProgressPercentage();
    }

    public int getOverallUploadProgressInt() {
        return (int) Math.rint(100 * getOverallUploadProgress());
    }

    public DividableProgressTracker getProgressTrackerForJob() {
        if (overallProgressTracker == null) {
            overallProgressTracker = new DividableProgressTracker(OVERALL_JOB_TASK, TOTAL_WORK/*, new SimpleProgressListener(0.01)*/);
        }
        return overallProgressTracker;
    }

    public DividableProgressTracker buildTaskProgressTrackerForOverallCompressionAndUploadOfData(Context context) {
        long totalUploadProgressTicksInJob = calculateTotalCompressionAndUploadingWork(context);
        DividableProgressTracker task = overallProgressTracker.getChildTask(OVERALL_DATA_UPLOAD_TASK);
        if (task == null) {
            task = overallProgressTracker.addChildTask(OVERALL_DATA_UPLOAD_TASK, totalUploadProgressTicksInJob, WORK_DIVISION_COMPRESS_AND_UPLOAD_PERC);
        }
        return task;
    }

    public DividableProgressTracker getTaskProgressTrackerForSingleFileChunkParsing(Uri uri, long totalBytes) {
        DividableProgressTracker parentTracker = overallProgressTracker.getChildTask(OVERALL_DATA_UPLOAD_TASK);
        if (parentTracker == null) {
            throw new IllegalStateException("Unable to find data upload task tracker - must be created in service due to need for context");
        }
        DividableProgressTracker tracker = parentTracker.getChildTask(FILES_CHUNKS_UPLOAD_TASK + "_" + uri.getPath());
        if(tracker == null) {
            tracker = parentTracker.addChildTask(FILES_CHUNKS_UPLOAD_TASK + "_" + uri.getPath(), totalBytes, getCompressionUploadProgressTicksForFile(uri));
        }
        return tracker;
    }

    public DividableProgressTracker getTaskProgressTrackerForAllChecksumCalculation() {
        DividableProgressTracker tracker = overallProgressTracker.getChildTask(CHECKSUMS_CALCULATION_TASK);
        if (tracker == null) {
            tracker = overallProgressTracker.addChildTask(CHECKSUMS_CALCULATION_TASK, fileUploadDetails.size(), WORK_DIVISION_CHECKSUM_PERC);
        }
        return tracker;
    }

    public DividableProgressTracker getTaskProgressTrackerForSingleFileCompression(Uri uri) {
        DividableProgressTracker tracker = overallProgressTracker.getChildTask(OVERALL_DATA_UPLOAD_TASK);
        if (tracker == null) {
            throw new IllegalStateException("Unable to find data upload task tracker");
        }
        return tracker.addChildTask(SINGLE_FILE_COMPRESSION_TASK + "_" + uri.getPath(), 100, getCompressionUploadProgressTicksForFile(uri));
    }

    public @NonNull
    FileUploadDetails getFileUploadDetails(@NonNull Uri file) {
        FileUploadDetails uploadDetails = fileUploadDetails.get(file);
        if (uploadDetails == null) {
            throw new IllegalStateException("File not found in Upload job file " + file);
        }
        return uploadDetails;
    }

    public long getUploadUploadProgressTicksForFile(Uri rawFile) {
        //FIXME this will be less if the file is compressed or going to be
        // use file-size because we assume file-size will be proportional to the time taken.
        return getFileUploadDetails(rawFile).getFileSize();
    }

    private long getCompressionUploadProgressTicksForFile(Uri rawFile) {
        // use file-size because we assume compressing will double time to upload
        return getFileUploadDetails(rawFile).getFileSize();
    }

    public void setToRunInBackground() {
        this.runInBackground = true;
    }

    public boolean isRunInBackground() {
        return runInBackground;
    }

    public void cancelUploadAsap() {
        this.cancelUploadAsap = true;
    }

    public int getJobConfigId() {
        return jobConfigId;
    }

    public void setJobConfigId(int jobConfigId) {
        this.jobConfigId = jobConfigId;
    }

    public HashSet<Uri> getFilesProcessedToEnd() {
        HashSet<Uri> matches = new HashSet<>();
        for (FileUploadDetails fud : fileUploadDetails.values()) {
            if (fud.isHasNoActionToTake()) {
                matches.add(fud.getFileUri());
            }
        }
        return matches;
    }

    private HashSet<Uri> getFilesWhereUploadedDataHasBeenVerified() {
        HashSet<Uri> matches = new HashSet<>();
        for (FileUploadDetails fud : fileUploadDetails.values()) {
            if (fud.isVerified()) {
                matches.add(fud.getFileUri());
            }
        }
        return matches;
    }

    public long getResponseHandlerId() {
        return responseHandlerId;
    }

    /**
     * @param f file to cancel upload of
     * @return true if was possible to immediately cancel, false if there might be a little delay.
     */
    public synchronized boolean cancelFileUpload(Uri f) {
        FileUploadDetails fud = getFileUploadDetails(f);
        boolean immediateCancelPossible = fud.isUploadProcessStarted();
        fud.setStatusUserCancelled();
        // any other threads waiting (watching the job should now wake and check for changes)
        wakeAnyWaitingThreads();
        return immediateCancelPossible;
    }

    public long getJobId() {
        return jobId;
    }

    public synchronized Collection<FileUploadDetails> getFilesForUpload() {
        return fileUploadDetails.values();
    }

    public byte getPrivacyLevelWanted() {
        return privacyLevelWanted;
    }

    public boolean isCancelUploadAsap() {
        return cancelUploadAsap;
    }

    public ConnectionPreferences.ProfilePreferences getConnectionPrefs() {
        return connectionPrefs;
    }

    /**
     * @return File this job was loaded from
     */
    public DocumentFile getLoadedFromFile() {
        return loadedFromFile;
    }

    /**
     * @param loadedFromFile File this job was loaded from
     */
    public void setLoadedFromFile(DocumentFile loadedFromFile) {
        this.loadedFromFile = loadedFromFile;
    }

    public Map<Uri, String> getFileChecksumsForServerCheck(@NonNull Context context, boolean useFilenamesAsChecksum) {
        Map<Uri, String> filenamesToUpload = new HashMap<>();
        for (FileUploadDetails fud : fileUploadDetails.values()) {
            if (!fud.isHasNoActionToTake() && fud.getServerResource() == null) {
                if(useFilenamesAsChecksum) {
                    filenamesToUpload.put(fud.getFileUri(), fud.getFilename(context));
                } else {
                    filenamesToUpload.put(fud.getFileUri(), fud.getChecksumOfFileToUpload());
                }
            }
        }
        return filenamesToUpload;
    }

    public synchronized boolean isStatusFinished() {
        return jobStatus == JOB_STATUS_FINISHED;
    }

    public synchronized void setStatusFinished() {
        jobStatus = JOB_STATUS_FINISHED;
    }

    public boolean hasJobCompletedAllActionsSuccessfully() {
        for (FileUploadDetails fud : fileUploadDetails.values()) {
            if (!fud.isHasNoActionToTake()) {
                return false;
            }
        }
        return getTemporaryUploadAlbumId() < 0;
    }

    public synchronized ArrayList<Uri> getFilesNotYetUploaded() {
        ArrayList<Uri> filesToUpload = new ArrayList<>(fileUploadDetails.keySet());
        filesToUpload.removeAll(getFilesProcessedToEnd());
        filesToUpload.removeAll(getFilesWhereUploadedDataHasBeenVerified());
        return filesToUpload;
    }

    public long getTemporaryUploadAlbumId() {
        return temporaryUploadAlbumId;
    }

    public void setTemporaryUploadAlbumId(long temporaryUploadAlbumId) {
        this.temporaryUploadAlbumId = temporaryUploadAlbumId;
    }

    public void setStatusStopped() {
        jobStatus = JOB_STATUS_STOPPED;
    }

    public void setStatusRunning() {
        runAttempts++;
        jobStatus = JOB_STATUS_RUNNING;
    }

    public boolean isStatusRunningNow() {
        return jobStatus == JOB_STATUS_RUNNING;
    }

    public boolean isStatusSubmitted() {
        return jobStatus == JOB_STATUS_SUBMITTED;
    }

    public void setStatusSubmitted() {
        jobStatus = JOB_STATUS_SUBMITTED;
    }

    public void filterPreviouslyUploadedFiles(Map<Uri, String> fileUploadedHashMap) {
        for (FileUploadDetails fud : fileUploadDetails.values()) {
            if (fileUploadedHashMap.containsKey(fud.getFileUri())) {
                String previousUploadedFileChecksum = fileUploadedHashMap.get(fud.getFileUri());
                String selectedFileChecksum = fud.getChecksumOfSelectedFile();
                if (Objects.equals(previousUploadedFileChecksum, selectedFileChecksum)) {
                    if (runAttempts == 0) {
                        // this is the first time we're attempting upload of this job
                        Logging.log(Log.WARN, TAG, "Upload contains files previously uploaded to this PIWIGO server. Ignoring.");
                        fud.setStatusUserCancelled();
                    }
                }
            }
        }
    }

    public void recordError(String message) {
        generalErrors.addError(new Date(), message);
    }

    public boolean hasErrors() {
        if (!generalErrors.isEmpty()) {
            return true;
        }
        for (FileUploadDetails fud : fileUploadDetails.values()) {
            if (fud.hasErrors()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return A copy of the errors.
     */
    public LinkedHashMap<String, ProcessErrors> getCopyOfErrors() {
        LinkedHashMap<String, ProcessErrors> allErrors = new LinkedHashMap<>();
        allErrors.put("other", new ProcessErrors(generalErrors));
        for (FileUploadDetails fud : fileUploadDetails.values()) {
            if (fud.hasErrors()) {
                allErrors.put(fud.getFileUri().getPath(), new ProcessErrors(Objects.requireNonNull(fud.getErrors())));
            }
        }
        return allErrors;
    }

    public boolean isPlayableMedia(@NonNull Context context, @NonNull Uri file) {
        return IOUtils.isPlayableMedia(context, file);
    }

    public boolean isPhoto(@NonNull Context context, @NonNull Uri file) {
        String mimeType = IOUtils.getMimeType(context, file);
        return MimeTypeFilter.matches(mimeType, "image/*");
    }

    public DocumentFile buildCompressedFile(@NonNull Context context, @NonNull Uri baseFile, @NonNull String mimeType) {
        String uploadFileDisplayName = IOUtils.getFilename(context, baseFile);
        if (uploadFileDisplayName == null) {
            throw new IllegalStateException("Unable to retrieve filename for file : " + baseFile);
        }
        uploadFileDisplayName = IOUtils.getFileNameWithoutExt(uploadFileDisplayName);
        return getCompressedFilesFolder(context).createFile(mimeType, uploadFileDisplayName);
    }

    private DocumentFile getCompressedFilesFolder(@NonNull Context c) {
        DocumentFile f = DocumentFile.fromFile(Objects.requireNonNull(c.getExternalCacheDir()));
        DocumentFile compressedFolder = f.findFile("compressed_vids_for_upload");
        if (compressedFolder == null) {
            compressedFolder = f.createDirectory("compressed_vids_for_upload");
        }
        if (compressedFolder == null) {
            Logging.log(Log.ERROR, TAG, "Unable to create folder for compressed files to be placed");
        }
        return compressedFolder;
    }

    public void clearCancelUploadAsapFlag() {
        wasLastRunCancelled = cancelUploadAsap;
        cancelUploadAsap = false;
    }

    public boolean getAndClearWasLastRunCancelled() {
        boolean ret = wasLastRunCancelled;
        wasLastRunCancelled = false;
        return ret;
    }

    public ImageCompressionParams getImageCompressionParams() {
        return imageCompressionParams;
    }

    public void setImageCompressionParams(ImageCompressionParams imageCompressionParams) {
        this.imageCompressionParams = imageCompressionParams;
    }

    public boolean isCompressPlayableMediaBeforeUpload() {
        return playableMediaCompressionParams != null;
    }

    public boolean isCompressPhotosBeforeUpload() {
        return imageCompressionParams != null;
    }

    public VideoCompressionParams getPlayableMediaCompressionParams() {
        return playableMediaCompressionParams;
    }

    public void setPlayableMediaCompressionParams(VideoCompressionParams playableMediaCompressionParams) {
        this.playableMediaCompressionParams = playableMediaCompressionParams;
    }

    public boolean isAllowUploadOfRawVideosIfIncompressible() {
        return allowUploadOfRawVideosIfIncompressible;
    }

    public void setAllowUploadOfRawVideosIfIncompressible(boolean allowUploadOfRawVideosIfIncompressible) {
        this.allowUploadOfRawVideosIfIncompressible = allowUploadOfRawVideosIfIncompressible;
    }

    public Set<Long> getIdsOfResourcesForFilesSuccessfullyUploaded() {
        Set<Long> resourceIds = new HashSet<>();
        for (FileUploadDetails fud : fileUploadDetails.values()) {
            if (fud.isSuccessfullyUploaded()) {
                ResourceItem r = fud.getServerResource();
                if (r != null) {
                    resourceIds.add(r.getId());
                }
            }
        }
        return resourceIds;
    }

    public HashMap<Uri, String> getSelectedFileChecksumsForBlockingFutureUploads() {
        HashMap<Uri, String> uploadedFileChecksums = new HashMap<>(fileUploadDetails.size());
        for (FileUploadDetails fud : fileUploadDetails.values()) {
            if (fud.isSuccessfullyUploaded() && !fud.isDeleteAfterUpload()) {
                uploadedFileChecksums.put(fud.getFileUri(), fud.getChecksumOfSelectedFile());
            }
        }
        return uploadedFileChecksums;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(jobId);
        dest.writeLong(responseHandlerId);
        ParcelUtils.writeArrayList(dest, fileUploadDetails == null ? null : new ArrayList<>(fileUploadDetails.values()));
        ParcelUtils.writeParcelable(dest, uploadToCategory);
        dest.writeByte(privacyLevelWanted);
        dest.writeInt(jobConfigId);
        ParcelUtils.writeBool(dest, runInBackground);
        ParcelUtils.writeParcelable(dest, connectionPrefs);
        dest.writeInt(jobStatus);
        dest.writeLong(temporaryUploadAlbumId);
        ParcelUtils.writeParcelable(dest, generalErrors);
        ParcelUtils.writeParcelable(dest, playableMediaCompressionParams);
        ParcelUtils.writeParcelable(dest, imageCompressionParams);
        ParcelUtils.writeBool(dest, allowUploadOfRawVideosIfIncompressible);
        ParcelUtils.writeBool(dest, isDeleteFilesAfterUpload);
        ParcelUtils.writeParcelable(dest, overallProgressTracker);
        dest.writeInt(runAttempts);
    }

    public boolean isDeleteFilesAfterUpload() {
        return isDeleteFilesAfterUpload;
    }

    public HashSet<Uri> getFilesMatchingStatus(int status) {
        HashSet<Uri> matches = new HashSet<>();
        for (FileUploadDetails fud : fileUploadDetails.values()) {
            if (fud.getStatus() == status) {
                matches.add(fud.getFileUri());
            }
        }
        return matches;
    }

    public HashSet<Uri> getFilesMidTransfer() {
        return getFilesMatchingStatus(FileUploadDetails.UPLOADING);
    }

    public HashSet<Uri> getFilesAwaitingVerification() {
        return getFilesMatchingStatus(FileUploadDetails.UPLOADED);
    }

    public HashSet<Uri> getFilesAwaitingConfiguration() {
        return getFilesMatchingStatus(FileUploadDetails.VERIFIED);
    }

    public HashSet<Uri> getFilesWithoutFurtherActionNeeded() {
        HashSet<Uri> matches = new HashSet<>();
        for (FileUploadDetails fud : fileUploadDetails.values()) {
            if (fud.isHasNoActionToTake()) {
                matches.add(fud.getFileUri());
            }
        }
        return matches;
    }

    public HashSet<Uri> getFilesRequiringDelete() {
        return getFilesMatchingStatus(FileUploadDetails.REQUIRES_DELETE);
    }

    public HashSet<Uri> getFilesAwaitingUpload() {
        return getFilesMatchingStatus(FileUploadDetails.NOT_STARTED);
    }

    public HashSet<Uri> getFilesPendingCommunityApproval() {
        return getFilesMatchingStatus(FileUploadDetails.PENDING_APPROVAL);
    }

    public Collection<Uri> getFilesRequiringRetry() {
        HashSet<Uri> matches = new HashSet<>();
        for (FileUploadDetails fud : fileUploadDetails.values()) {
            if (fud.isProcessingFailed()) {
                matches.add(fud.getFileUri());
            }
        }
        return matches;
    }

    public boolean isHasRunBefore() {
        return runAttempts > 0;
    }

    public void waitUntilNotified() throws InterruptedException {
        synchronized (this) {
            this.wait();
        }
    }

    public void wakeAnyWaitingThreads() {
        synchronized (this) {
            this.notifyAll();
        }
    }

    public int getActionableFilesCount() {
        HashSet<Uri> matches = new HashSet<>();
        for (FileUploadDetails fud : fileUploadDetails.values()) {
            if (!fud.isHasNoActionToTake() && !fud.isProcessingFailed()) {
                matches.add(fud.getFileUri());
            }
        }
        return matches.size();
    }

    public Set<FileUploadDetails> getFilesRequiringProcessing() {
        Set<FileUploadDetails> filesNeedingProcessing = new HashSet<>(getFilesForUpload());
        for (Iterator<FileUploadDetails> iterator = filesNeedingProcessing.iterator(); iterator.hasNext(); ) {
            FileUploadDetails uploadDetails = iterator.next();
            if(uploadDetails.isHasNoActionToTake()) {
                iterator.remove();
            }
        }
        return filesNeedingProcessing;

    }

    public boolean getHasFilesRequiringCompression(List<FileUploadDetails> excludeFromCheck) {
        Set<FileUploadDetails> filesNeedingProcessing = new HashSet<>(getFilesForUpload());
        for (Iterator<FileUploadDetails> iterator = filesNeedingProcessing.iterator(); iterator.hasNext(); ) {
            FileUploadDetails uploadDetails = iterator.next();
            if(excludeFromCheck.contains(uploadDetails)) {
                continue;
            }
            if(uploadDetails.isCompressionWanted() && !uploadDetails.isUploadStarted()) {
                return true;
            }
        }
        return false;
    }

    public static class ImageCompressionParams implements Parcelable {
        public static final Creator<ImageCompressionParams> CREATOR = new Creator<ImageCompressionParams>() {
            @Override
            public ImageCompressionParams createFromParcel(Parcel in) {
                return new ImageCompressionParams(in);
            }

            @Override
            public ImageCompressionParams[] newArray(int size) {
                return new ImageCompressionParams[size];
            }
        };
        private String outputFormat;
        private @IntRange(from = 0, to = 100)
        int quality;
        private int maxWidth = -1;
        private int maxHeight = -1;

        public ImageCompressionParams() {
        }

        protected ImageCompressionParams(Parcel in) {
            outputFormat = in.readString();
            quality = in.readInt();
            maxWidth = in.readInt();
            maxHeight = in.readInt();
        }

        public int getQuality() {
            return quality;
        }

        public void setQuality(int quality) {
            this.quality = quality;
        }

        public String getOutputFormat() {
            return outputFormat;
        }

        public void setOutputFormat(String outputFormat) {
            this.outputFormat = outputFormat;
        }

        public int getMaxHeight() {
            return maxHeight;
        }

        public void setMaxHeight(int maxHeight) {
            this.maxHeight = maxHeight;
        }

        public int getMaxWidth() {
            return maxWidth;
        }

        public void setMaxWidth(int maxWidth) {
            this.maxWidth = maxWidth;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(outputFormat);
            dest.writeInt(quality);
            dest.writeInt(maxWidth);
            dest.writeInt(maxHeight);
        }
    }

    public static class VideoCompressionParams implements Parcelable {
        public static final Creator<VideoCompressionParams> CREATOR = new Creator<VideoCompressionParams>() {
            @Override
            public VideoCompressionParams createFromParcel(Parcel in) {
                return new VideoCompressionParams(in);
            }

            @Override
            public VideoCompressionParams[] newArray(int size) {
                return new VideoCompressionParams[size];
            }
        };
        private double quality = -1;
        private int audioBitrate = -1;

        public VideoCompressionParams() {

        }

        protected VideoCompressionParams(Parcel in) {
            quality = in.readDouble();
            audioBitrate = in.readInt();
        }

        public double getQuality() {
            return quality;
        }


        public void setQuality(double quality) {
            this.quality = quality;
        }

        public int getAudioBitrate() {
            return audioBitrate;
        }

        public void setAudioBitrate(int audioBitrate) {
            this.audioBitrate = audioBitrate;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeDouble(quality);
            dest.writeInt(audioBitrate);
        }

        public boolean hasAStream() {
            return audioBitrate > 0 || quality - 0.0001 > 0;
        }
    }

}