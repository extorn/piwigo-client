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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.ParcelUtils;
import delit.libs.util.IOUtils;
import delit.libs.util.Md5SumUtils;
import delit.libs.util.progress.ProgressListener;
import delit.libs.util.progress.TaskProgressTracker;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;

public class UploadJob implements Parcelable {


    private static final String TAG = "UploadJob";
    public static final Integer COMPRESSED = 8; // file has been compressed successfully.

    public static final Integer ERROR = -2; // files that could not be uploaded due to error
    public static final Integer CANCELLED = -1; // file has been removed from the upload job
    public static final Integer UPLOADING = 0; // file bytes transfer in process
    public static final Integer UPLOADED = 1; // all file bytes uploaded but not checksum verified
    public static final Integer VERIFIED = 2; // file bytes match those on the client device
    public static final Integer CONFIGURED = 3; // all permissions etc sorted and file renamed
    public static final Integer PENDING_APPROVAL = 4; // if community plugin in use and file is otherwise completely sorted
    public static final Integer REQUIRES_DELETE = 5; // user cancels upload after file partially uploaded
    public static final Integer DELETED = 6; // file has been deleted from the server
    public static final Integer CORRUPT = 7; // (moves to this state if verification fails)

    //Total of this work must equal TOTAL WORK. I'm tring to keep this around 100 so its roughly percentages. N.b. this isn't accurate as job size changes.
    public static final long WORK_DIVISION_CHECKSUM_PERC = 5;
    public static final long WORK_DIVISION_POST_CHECKED_FOR_EXISTING_FILES = 2;
    public static final long WORK_DIVISION_COMPRESS_AND_UPLOAD_PERC = 87;
    public static final long WORK_DIVISION_DELETE_TEMP_FOLDER = 5;
    public static final long WORK_DIVISION_POST_UPLOAD_CALLS = 1;
    public static final long TOTAL_WORK = WORK_DIVISION_CHECKSUM_PERC + WORK_DIVISION_POST_CHECKED_FOR_EXISTING_FILES + WORK_DIVISION_COMPRESS_AND_UPLOAD_PERC + WORK_DIVISION_DELETE_TEMP_FOLDER + WORK_DIVISION_POST_UPLOAD_CALLS;

    private final long jobId;
    private final long responseHandlerId;
    private final HashMap<Uri,Long> filesForUploadAndSize;
    private HashMap<Uri, Uri> compressedFilesMap;
    private final HashMap<Uri, Integer> fileUploadStatus;
    private final HashMap<Uri, PartialUploadData> filePartialUploadProgress;
    private final ArrayList<Long> uploadToCategoryParentage;
    private final long uploadToCategory;
    private final byte privacyLevelWanted;
    private int jobConfigId = -1;
    private boolean runInBackground;
    private final ConnectionPreferences.ProfilePreferences connectionPrefs;
    private HashMap<Uri, String> fileChecksums;
    private boolean finished;
    private long temporaryUploadAlbum = -1;
    private final SortedSet<Uri> filesWithError = new TreeSet<>();
    private LinkedHashMap<String, AreaErrors> errors = new LinkedHashMap<>();
    private VideoCompressionParams playableMediaCompressionParams;
    private ImageCompressionParams imageCompressionParams;
    private boolean allowUploadOfRawVideosIfIncompressible;
    private final boolean isDeleteFilesAfterUpload;

    private volatile boolean submitted = false;
    private volatile boolean runningNow = false;
    private volatile boolean cancelUploadAsap;
    private DocumentFile loadedFromFile;
    private boolean wasLastRunCancelled;
    private double overallUploadProgress;
    private TaskProgressTracker overallJobProgressTracker;
    private TaskProgressTracker uploadProgressTracker;
    private long totalDataToWorkOn;
    private long dataWorkAlreadyDone;


    public UploadJob(ConnectionPreferences.ProfilePreferences connectionPrefs, long jobId, long responseHandlerId, Map<Uri, Long> filesForUploadAndBytes, CategoryItemStub destinationCategory, byte uploadedFilePrivacyLevel, boolean isDeleteFilesAfterUpload) {
        this.jobId = jobId;
        this.connectionPrefs = connectionPrefs;
        this.responseHandlerId = responseHandlerId;
        this.uploadToCategory = destinationCategory.getId();
        this.uploadToCategoryParentage = new ArrayList<>(destinationCategory.getParentageChain());
        this.privacyLevelWanted = uploadedFilePrivacyLevel;
        this.filesForUploadAndSize = new HashMap<>(filesForUploadAndBytes);
        this.fileUploadStatus = new HashMap<>(filesForUploadAndSize.size());
        this.filePartialUploadProgress = new HashMap<>(filesForUploadAndSize.size());
        this.compressedFilesMap = new HashMap<>();
        this.isDeleteFilesAfterUpload = isDeleteFilesAfterUpload;
    }

    protected UploadJob(Parcel in) {
        jobId = in.readLong();
        responseHandlerId = in.readLong();
        filesForUploadAndSize = ParcelUtils.readMap(in, Uri.class.getClassLoader());
        compressedFilesMap = ParcelUtils.readMap(in, Uri.class.getClassLoader());
        fileUploadStatus = ParcelUtils.readMap(in, Uri.class.getClassLoader());
        filePartialUploadProgress = ParcelUtils.readMap(in, UploadJob.PartialUploadData.class.getClassLoader());
        uploadToCategoryParentage = ParcelUtils.readLongArrayList(in);
        uploadToCategory = in.readLong();
        privacyLevelWanted = in.readByte();
        jobConfigId = in.readInt();
        runInBackground = ParcelUtils.readBool(in);
        connectionPrefs = ParcelUtils.readParcelable(in, ConnectionPreferences.ProfilePreferences.class);
        fileChecksums = ParcelUtils.readMap(in, Uri.class.getClassLoader());
        finished = ParcelUtils.readBool(in);
        temporaryUploadAlbum = in.readLong();
        errors = ParcelUtils.readMapTypedValues(in, errors, AreaErrors.class);
        playableMediaCompressionParams = ParcelUtils.readParcelable(in, UploadJob.VideoCompressionParams.class);
        imageCompressionParams = ParcelUtils.readParcelable(in, UploadJob.ImageCompressionParams.class);
        allowUploadOfRawVideosIfIncompressible = ParcelUtils.readBool(in);
        isDeleteFilesAfterUpload = ParcelUtils.readBool(in);
        overallUploadProgress = in.readDouble();
        totalDataToWorkOn = in.readLong();
        dataWorkAlreadyDone = in.readLong();
    }

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

    private long calculateTotalWork(@NonNull Context context) {
        long total = 0;
        for(Map.Entry<Uri,Long> entry : filesForUploadAndSize.entrySet()) {
            long val = entry.getValue();
            Uri f = entry.getKey();
            total += val;
            if ((isPhoto(context, f) && isCompressPhotosBeforeUpload()) || (isPlayableMedia(context, f) && isCompressPlayableMediaBeforeUpload())) {
                total += val; // double the file for compression
            }
        }
        return total;
    }

    public boolean hasFilesForUpload() {
        boolean hasFilesToUpload = !filesForUploadAndSize.isEmpty();
        if(hasFilesToUpload) {
            // at least one file isn't marked with error state
            return !filesWithError.containsAll(filesForUploadAndSize.keySet());
        }
        return false;
    }

    public long getFileSize(@NonNull Uri toUpload) {
        Long size = filesForUploadAndSize.get(toUpload);
        if(size == null) {
            Logging.log(Log.ERROR, TAG, "getFilesize requested for Uri not present in job");
            throw new IllegalStateException("UploadJob does not contain uri");
        }
        return size;
    }

    public void cancelAllFailedUploads() {
        for(Uri file : filesWithError) {
            cancelFileUpload(file);
        }
        filesWithError.clear();
    }

    public void clearUploadErrors() {
        filesWithError.clear();
    }


    public static class ProgressAdapterChain extends TaskProgressTracker.ProgressAdapter {
        private final UploadJob uploadJob;
        private final ProgressListener chained;
        @Override
        public void onProgress(double percent) {
            uploadJob.overallUploadProgress = percent;
            if(uploadJob.uploadProgressTracker != null) {
                uploadJob.dataWorkAlreadyDone = uploadJob.totalDataToWorkOn - uploadJob.uploadProgressTracker.getRemainingWork();
            }
            if(chained != null) {
                chained.onProgress(percent);
            }
        }

        public ProgressAdapterChain(@NonNull UploadJob uploadJob) {
            this(uploadJob, null);
        }

        public ProgressAdapterChain(@NonNull UploadJob uploadJob, @Nullable ProgressListener chained) {
            this.chained = chained;
            this.uploadJob = uploadJob;
        }

        @Override
        public double getUpdateStep() {
            return 0;//chained.getUpdateStep();
        }
    }

    private long calculateWorkDone(@NonNull Context context) {
        long workDone = 0;
        HashSet<Uri> filesToIgnoreWhenRunningJob = getFilesWithStatus(ERROR, CANCELLED);
        for (Map.Entry<Uri,Long> entry : filesForUploadAndSize.entrySet()) {
            Uri key = entry.getKey();
            long size = entry.getValue();
            if (filesToIgnoreWhenRunningJob.contains(key)) {
                workDone += size;
                continue;
            }
            if ((isPhoto(context, key) && isCompressPhotosBeforeUpload()) || (isPlayableMedia(context, key) && isCompressPlayableMediaBeforeUpload())) {
                if(getCompressionProgress(context, key) == 100) {
                    workDone += size; // if it is partially done, then it's going to be scrapped and re-done.
                }
            }
            workDone += Math.rint(((double)size) / 100 * getUploadProgress(key));
        }
        return workDone;
    }

    public int getOverallUploadProgressInt() {
        return (int) Math.rint(100 * overallUploadProgress);
    }

    public TaskProgressTracker getProgressTrackerForJob(@NonNull Context context) {
        if(overallJobProgressTracker == null) {
            if(this.totalDataToWorkOn == 0) {
                this.totalDataToWorkOn = calculateTotalWork(context);
                this.dataWorkAlreadyDone = calculateWorkDone(context); // this will assume checksums need to occur still, but will deal with files uploaded or partially so as best it can.
            }
            overallJobProgressTracker = new TaskProgressTracker("Upload Job", TOTAL_WORK, new ProgressAdapterChain(this));
            overallJobProgressTracker.setExactProgress(overallUploadProgress);
        }
        return overallJobProgressTracker;
    }

    public TaskProgressTracker getTaskProgressTrackerForOverallCompressionAndUploadOfData() {
        if(uploadProgressTracker == null) {
            uploadProgressTracker = overallJobProgressTracker.addSubTask("Compression and Upload", totalDataToWorkOn, WORK_DIVISION_COMPRESS_AND_UPLOAD_PERC);
            uploadProgressTracker.setWorkDone(dataWorkAlreadyDone);
        }
        return uploadProgressTracker;
    }

    public TaskProgressTracker getTaskProgressTrackerForSingleFileChunkParsing(long totalBytes, long bytesUploaded) {
        return uploadProgressTracker.addSubTask("Files Chunks Upload", totalBytes - bytesUploaded, totalBytes - bytesUploaded);
    }

    public TaskProgressTracker getTaskProgressTrackerForAllChecksumCalculation() {
        return overallJobProgressTracker.addSubTask("Checksums calculation", filesForUploadAndSize.size(), WORK_DIVISION_CHECKSUM_PERC);
    }

    public TaskProgressTracker getTaskProgressTrackerForSingleFileCompression(Uri uri) {
        long filesize = getFileSize(uri);
        return uploadProgressTracker.addSubTask("file compression", 100, filesize);
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

    public synchronized void markFileAsUploading(Uri fileForUpload) {
        fileUploadStatus.put(fileForUpload, UPLOADING);
    }

    public synchronized void markFileAsVerified(Uri fileForUpload) {
        fileUploadStatus.put(fileForUpload, VERIFIED);
    }

    public synchronized void markFileAsCorrupt(Uri fileForUpload) {
        fileUploadStatus.put(fileForUpload, CORRUPT);
    }

    public synchronized void markFileAsCompressed(Uri fileForUpload) {
        fileUploadStatus.put(fileForUpload, COMPRESSED);
    }

    public synchronized void markFileAsConfigured(Uri fileForUpload) {
        fileUploadStatus.put(fileForUpload, CONFIGURED);
    }

    public synchronized void markFileAsNeedsDelete(Uri fileForUpload) {
        fileUploadStatus.put(fileForUpload, REQUIRES_DELETE);
    }

    public synchronized void markFileAsDeleted(Uri fileForUpload) {
        fileUploadStatus.put(fileForUpload, DELETED);
    }

    public synchronized boolean needsUpload(Uri fileForUpload) {
        Integer status = fileUploadStatus.get(fileForUpload);
        return status == null || COMPRESSED.equals(status) || UPLOADING.equals(status);
    }

    public int getJobConfigId() {
        return jobConfigId;
    }

    public void setJobConfigId(int jobConfigId) {
        this.jobConfigId = jobConfigId;
    }

    public ArrayList<Uri> getFilesAwaitingUpload() {
        Set<Uri> filesProcessedToSomeDegree = fileUploadStatus.keySet();
        ArrayList<Uri> filesAwaitingUpload = new ArrayList<>(filesForUploadAndSize.keySet());
        filesAwaitingUpload.removeAll(filesProcessedToSomeDegree);
        return filesAwaitingUpload;
    }

    //
//    public synchronized boolean isUploadOfFileInProgress(Uri fileForUpload) {
//        Integer status = fileUploadStatus.get(fileForUpload);
//        return status != null && status != CONFIGURED && status != DELETED && status != CANCELLED;
//    }

    public HashSet<Uri> getFilesProcessedToEnd() {
        return getFilesWithStatus(PENDING_APPROVAL, CONFIGURED, DELETED, CANCELLED);
    }

    public HashSet<Uri> getFilesWhereUploadedDataHasBeenVerified() {
        return getFilesWithStatus(VERIFIED);
    }

    public HashSet<Uri> getFilesSuccessfullyUploaded() {
        return getFilesWithStatus(PENDING_APPROVAL, CONFIGURED);
    }

    public HashSet<Uri> getFilesWithStatus(Integer... statuses) {
        HashSet<Uri> filesWithStatus = new HashSet<>();
        for (Map.Entry<Uri, Long> filesForUpload : filesForUploadAndSize.entrySet()) {
            Uri fileForUpload = filesForUpload.getKey();
            if(isUploadStatusIn(fileForUpload, statuses)) {
                filesWithStatus.add(fileForUpload);
            }
        }
        return filesWithStatus;
    }

    public HashSet<Uri> getFilesPendingApproval() {
        return getFilesWithStatus(PENDING_APPROVAL);
    }

    public synchronized boolean isUploadProcessNotYetStarted(Uri fileForUpload) {
        return null == fileUploadStatus.get(fileForUpload);
    }

    public synchronized boolean needsVerification(Uri fileForUpload) {
        return UPLOADED.equals(fileUploadStatus.get(fileForUpload));
    }

    public boolean isUploadVerified(Uri fileForUpload) {
        return VERIFIED.equals(fileUploadStatus.get(fileForUpload));
    }

    public boolean isUploadingData(Uri fileForUpload) {
        return UPLOADING.equals(fileUploadStatus.get(fileForUpload));
    }

    public boolean isFileCompressed(Uri fileForUpload) {
        return COMPRESSED.equals(fileUploadStatus.get(fileForUpload));
    }

    public boolean isUploadedFileVerified(Uri fileForUpload) {
        return VERIFIED.equals(fileUploadStatus.get(fileForUpload));
    }

    public synchronized boolean needsConfiguration(Uri fileForUpload) {
        return VERIFIED.equals(fileUploadStatus.get(fileForUpload));
    }

    public synchronized boolean needsDelete(Uri fileForUpload) {
        return REQUIRES_DELETE.equals(fileUploadStatus.get(fileForUpload));
    }

    public synchronized boolean needsDeleteAndThenReUpload(Uri fileForUpload) {
        return CORRUPT.equals(fileUploadStatus.get(fileForUpload));
    }

    public synchronized boolean isFileUploadComplete(Uri fileForUpload) {
        return isUploadStatusIn(fileForUpload, PENDING_APPROVAL, CONFIGURED);
    }

    public synchronized boolean uploadItemRequiresAction(Uri file) {
        return !isUploadStatusIn(file, null, PENDING_APPROVAL, CONFIGURED, CANCELLED, DELETED);
    }

    private boolean isUploadStatusIn(Uri fileToUpload, Integer ... statuses) {
        Integer uploadStatus = fileUploadStatus.get(fileToUpload);
        for (Integer status : statuses) {
            if(Objects.equals(uploadStatus, status) || ERROR.equals(status) && filesWithError.contains(fileToUpload)) {
                return true;
            }
        }
        return false;
    }

    public long getResponseHandlerId() {
        return responseHandlerId;
    }

    /**
     * @param f file to cancel upload of
     * @return true if was possible to immediately cancel, false if there might be a little delay.
     */
    public synchronized boolean cancelFileUpload(Uri f) {
        Integer status = fileUploadStatus.get(f);
        fileUploadStatus.put(f, CANCELLED);
        return status == null;
    }

    public synchronized void setErrorFlag(Uri f) {
        filesWithError.add(f);
    }

    public long getJobId() {
        return jobId;
    }

    public synchronized Set<Uri> getFilesForUpload() {
        return filesForUploadAndSize.keySet();
    }

    public byte getPrivacyLevelWanted() {
        return privacyLevelWanted;
    }

    public boolean isCancelUploadAsap() {
        return cancelUploadAsap;
    }

    public synchronized boolean isFileUploadStillWanted(Uri file) {
        Integer status = fileUploadStatus.get(file);
        return !CANCELLED.equals(status);
    }

    public ConnectionPreferences.ProfilePreferences getConnectionPrefs() {
        return connectionPrefs;
    }

    public long getUploadToCategory() {
        return uploadToCategory;
    }

    public synchronized void clearUploadProgress(Uri f) {
        fileUploadStatus.remove(f);
        filePartialUploadProgress.remove(f);
    }

    public synchronized int getCompressionProgress(@NonNull Context context, Uri uploadJobKey) {
        Integer status = fileUploadStatus.get(uploadJobKey);
        if (COMPRESSED.equals(status)) {
            return 100;
        }
        if ((isCompressPlayableMediaBeforeUpload() && canCompressVideoFile(context, uploadJobKey)) || isCompressPhotosBeforeUpload()) {
            // if we've started uploading this file it must have been compressed first!
            return getUploadProgress(uploadJobKey) > 0 ? 100 : 0;
        }
        return 0;
    }

    public synchronized int getUploadProgress(Uri uploadJobKey) {

//        private static final Integer CANCELLED = -1;
//        private static final Integer UPLOADING = 0;
//        private static final Integer UPLOADED = 1;
//        private static final Integer VERIFIED = 2;
//        private static final Integer CONFIGURED = 3;
//        private static final Integer PENDING_APPROVAL = 4;
//        private static final Integer REQUIRES_DELETE = 5;
//        private static final Integer DELETED = 6;
        Integer status = fileUploadStatus.get(uploadJobKey);
        if (status == null) {
            status = Integer.MIN_VALUE;
        }
        int progress = 0;
        if (UPLOADING.equals(status)) {

            PartialUploadData progressData = filePartialUploadProgress.get(uploadJobKey);
            if (progressData == null) {
                progress = 0;
            } else {
                progress = Math.round((float) (((double) progressData.getBytesUploaded()) / progressData.getTotalBytesToUpload() * 90));
            }
        }
        if (status >= UPLOADED) {
            progress = 90;
        }
        if (status >= VERIFIED) {
            progress += 5;
        }
        if (status >= CONFIGURED) {
            progress += 5;
        }
        return progress;
    }

    public synchronized Map<Uri, Md5SumUtils.Md5SumException> calculateChecksums(@NonNull Context context) {
        TaskProgressTracker checksumProgressTracker = getTaskProgressTrackerForAllChecksumCalculation();
        try {
            Map<Uri, Md5SumUtils.Md5SumException> failures = new HashMap<>(0);
            ArrayList<Uri> filesNotFinished = getFilesNotYetUploaded();
            ArrayList<Uri> filesNoLongerAvailable = getListOfFilesNoLongerUnavailable(context, filesNotFinished);
            boolean newJob = false;
            if (fileChecksums == null) {
                fileChecksums = new HashMap<>(filesForUploadAndSize.size());
                newJob = true;
            }
            for (Uri f : filesNotFinished) {
                TaskProgressTracker fileChecksumProgressTracker = checksumProgressTracker.addSubTask("file checksum", 100, 1); // tick one file off. Each file has 0 - 100% completion
                try {
                    if (filesNoLongerAvailable.contains(f)) {
                        // Remove file from upload list
                        failures.put(f, new Md5SumUtils.Md5SumException(context.getString(R.string.error_file_not_found)));
                    } else if (needsUpload(f) || needsVerification(f)) {
                        Uri fileForChecksumCalc = null;
                        if (!((isPhoto(context, f) && isCompressPhotosBeforeUpload())
                                || canCompressVideoFile(context, f) && isCompressPlayableMediaBeforeUpload())) {
                            fileForChecksumCalc = f;
                        } else if (getCompressedFile(f) != null) {
                            fileForChecksumCalc = getCompressedFile(f);
                        }
                        if (fileForChecksumCalc != null) {
                            // if its not a file we're going to compress but haven't yet

                            // recalculate checksums for all files not yet uploaded
                            String checksum = null;
                            try {
                                checksum = Md5SumUtils.calculateMD5(context.getContentResolver(), fileForChecksumCalc, fileChecksumProgressTracker);
                            } catch (Md5SumUtils.Md5SumException e) {
                                failures.put(f, e);
                                Logging.log(Log.DEBUG, TAG, "Error calculating MD5 hash for file. Noting failure");
                            } finally {
                                if (!newJob) {
                                    fileChecksums.remove(f);
                                }
                                if (checksum != null) {
                                    fileChecksums.put(f, checksum);
                                }
                            }
                        }
                    }
                } finally {
                    fileChecksumProgressTracker.markComplete();
                }
            }
            return failures;
        } finally {
            checksumProgressTracker.markComplete();
        }
    }

    public synchronized Map<Uri, String> getFileChecksums() {
        return fileChecksums;
    }

    public synchronized String getFileChecksum(Uri fileForUpload) {
        return fileChecksums.get(fileForUpload);
    }

    public synchronized void addFileChecksum(@NonNull Context context, Uri uploadJobKey, Uri fileForUpload) throws Md5SumUtils.Md5SumException {
        String checksum = Md5SumUtils.calculateMD5(context.getContentResolver(), fileForUpload);
        fileChecksums.put(uploadJobKey, checksum);
    }

    public synchronized boolean isFinished() {
        return finished && !isRunningNow();
    }

    public synchronized void setFinished() {
        finished = true;
    }

    public synchronized void addFileUploaded(Uri fileForUpload, ResourceItem itemOnServer) {
        PartialUploadData uploadData = filePartialUploadProgress.get(fileForUpload);
        if (uploadData == null) {
            filePartialUploadProgress.put(fileForUpload, new PartialUploadData(itemOnServer));
        } else {
            uploadData.setUploadedItem(itemOnServer);
        }
        if (itemOnServer == null && PiwigoSessionDetails.isUseCommunityPlugin(getConnectionPrefs())) {
            // to be expected if already uploaded but not yet approved.
            fileUploadStatus.put(fileForUpload, PENDING_APPROVAL);
        } else {
            fileUploadStatus.put(fileForUpload, UPLOADED);
        }
    }

    public synchronized ResourceItem getUploadedFileResource(Uri fileUploaded) {
        PartialUploadData partialUploadData = filePartialUploadProgress.get(fileUploaded);
        if (partialUploadData == null) {
            // this file has been uploaded before by a different job.
            return null;
        }
        return partialUploadData.getUploadedItem();
    }

    public boolean hasJobCompletedAllActionsSuccessfully() {
        ArrayList<Uri> filesToUpload = getFilesNotYetUploaded();
        return (filesToUpload.isEmpty() && filesWithError.isEmpty()) && getTemporaryUploadAlbum() < 0;
    }

    public synchronized ArrayList<Uri> getFilesNotYetUploaded() {
        ArrayList<Uri> filesToUpload = new ArrayList<>(filesForUploadAndSize.keySet());
        filesToUpload.removeAll(getFilesProcessedToEnd());
        filesToUpload.removeAll(getFilesWhereUploadedDataHasBeenVerified());
        return filesToUpload;
    }

    public synchronized ArrayList<Uri> getListOfFilesNoLongerUnavailable(@NonNull Context context, ArrayList<Uri> files) {
        ArrayList<Uri> unavailableFiles = new ArrayList<>();
        for (Uri f : files) {
            IOUtils.getSingleDocFile(context, f);
            DocumentFile docFile = IOUtils.getSingleDocFile(context, f);
            //FIXME add this to the if statement for testing with job failure:   || !docFile.isFile()
            if (docFile != null && (docFile.isDirectory() || !docFile.exists())) {
                unavailableFiles.add(f);
            }
        }
        return unavailableFiles;
    }

    public synchronized List<Long> getUploadToCategoryParentage() {
        return uploadToCategoryParentage;
    }

    public void markFileAsPartiallyUploaded(Uri uploadJobKey, String uploadName, String fileChecksum, long totalBytesToUpload, long bytesUploaded, long countChunksUploadedOkay) {
        PartialUploadData data = filePartialUploadProgress.get(uploadJobKey);
        if (data == null) {
            filePartialUploadProgress.put(uploadJobKey, new PartialUploadData(uploadName, fileChecksum, totalBytesToUpload, bytesUploaded, countChunksUploadedOkay));
        } else {
            data.setUploadStatus(fileChecksum, bytesUploaded, countChunksUploadedOkay);
        }
    }

    public PartialUploadData getChunksAlreadyUploadedData(Uri fileForUpload) {
        return filePartialUploadProgress.get(fileForUpload);
    }

    public void deleteChunksAlreadyUploadedData(Uri fileForUpload) {
        filePartialUploadProgress.remove(fileForUpload);
    }

    public boolean isFilePartiallyUploaded(Uri cancelledFile) {
        PartialUploadData data = filePartialUploadProgress.get(cancelledFile);
        return !(data == null || data.getBytesUploaded() == 0);
    }

    public long getTemporaryUploadAlbum() {
        return temporaryUploadAlbum;
    }

    public void setTemporaryUploadAlbum(long temporaryUploadAlbum) {
        this.temporaryUploadAlbum = temporaryUploadAlbum;
    }

    public void setRunning(boolean isRunningNow) {
        runningNow = isRunningNow;
    }

    public boolean isRunningNow() {
        return runningNow;
    }

    public boolean isSubmitted() {
        return submitted;
    }

    public void setSubmitted(boolean submitted) {
        this.submitted = submitted;
    }

    public Map<Uri, String> getFileToFilenamesMap(@NonNull Context context) {
        Map<Uri, String> filenamesMap = new HashMap<>(filesForUploadAndSize.size());
        for (Uri f : filesForUploadAndSize.keySet()) {
            filenamesMap.put(f, IOUtils.getSingleDocFile(context, f).getName());
        }
        return filenamesMap;
    }

    public void filterPreviouslyUploadedFiles(Map<Uri, String> fileUploadedHashMap) {
        for (HashMap.Entry<Uri, String> fileUploadedEntry : fileUploadedHashMap.entrySet()) {
            Uri potentialDuplicateUpload = fileUploadedEntry.getKey();
            if (filesForUploadAndSize.containsKey(potentialDuplicateUpload)) {
                // a file at this absolute path has been uploaded previously to this end point
                String checksum = fileChecksums.get(potentialDuplicateUpload);
                if (checksum != null) {
                    if (checksum.equals(fileUploadedEntry.getValue())) {
                        // the file is identical to that previously uploaded (checksum check)
                        if (!fileUploadStatus.containsKey(potentialDuplicateUpload)) {
                            // this is a fresh target for this job
                            filesForUploadAndSize.remove(fileUploadedEntry.getKey());
                            fileChecksums.remove(fileUploadedEntry.getKey());
                        }
                    }
                }
            }
        }

    }

    public boolean hasBeenRunBefore() {
        return loadedFromFile != null || filePartialUploadProgress.size() > 0 || fileUploadStatus.size() > 0;
    }

    public DocumentFile getLoadedFromFile() {
        return loadedFromFile;
    }

    public void setLoadedFromFile(DocumentFile loadedFromFile) {
        this.loadedFromFile = loadedFromFile;
    }

    private @NonNull AreaErrors getErrorsForKey(String key) {
        AreaErrors keyedErrors = errors.get(key);
        if(keyedErrors == null) {
            keyedErrors = new AreaErrors();
            errors.put(key, keyedErrors);
        }
        return keyedErrors;
    }

    public void recordError(String message) {
        AreaErrors generalErrors = getErrorsForKey("General");
        generalErrors.addError(new Date(), message);
    }

    public void recordErrorLinkedToFile(Uri fileLinkedToError, String message) {
        AreaErrors fileErrors = getErrorsForKey(fileLinkedToError.toString());
        fileErrors.addError(new Date(), message);
    }

    public boolean hasErrors() {
        return errors.size() > 0;
    }

    public LinkedHashMap<String, AreaErrors> getErrors() {
        return errors;
    }

    public boolean isPlayableMedia(@NonNull Context context, @NonNull Uri file) {
        return IOUtils.isPlayableMedia(context, file);
    }

    public boolean isPhoto(@NonNull Context context, @NonNull Uri file) {
        String mimeType = IOUtils.getMimeType(context, file);
        return MimeTypeFilter.matches(mimeType,"image/*");
    }

    public DocumentFile buildCompressedFile(@NonNull Context context, @NonNull Uri baseFile, @NonNull String mimeType) {
        String uploadFileDisplayName = IOUtils.getFilename(context, baseFile);
        if(uploadFileDisplayName == null) {
            throw new IllegalStateException("Unable to retrieve filename for file : " + baseFile);
        }
        uploadFileDisplayName = IOUtils.getFileNameWithoutExt(uploadFileDisplayName);
        DocumentFile compressedFile = getCompressedFilesFolder(context).createFile(mimeType, uploadFileDisplayName);
        if (compressedFilesMap == null) {
            compressedFilesMap = new HashMap<>();
        }
        return compressedFile;
    }

    public void addCompressedFile(Uri rawFileForUpload, DocumentFile compressedFile) {
        compressedFilesMap.put(rawFileForUpload, compressedFile.getUri());
    }

    public Uri getCompressedFile(Uri rawFileForUpload) {
        if (compressedFilesMap == null) {
            return null;
        }
        return compressedFilesMap.get(rawFileForUpload);
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

    public boolean canCompressVideoFile(@NonNull Context context, Uri rawMediaUri) {
        return isPlayableMedia(context, rawMediaUri);
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
        for (Uri f : getFilesSuccessfullyUploaded()) {
            ResourceItem r = getUploadedFileResource(f);
            if (r != null) {
                resourceIds.add(r.getId());
            }
        }
        return resourceIds;
    }

    public HashMap<Uri, String> getUploadedFilesLocalFileChecksums(@NonNull Context context) {
        HashSet<Uri> filesUploaded = getFilesSuccessfullyUploaded();
        HashMap<Uri, String> uploadedFileChecksums = new HashMap<>(filesUploaded.size());
        for (Uri f : filesUploaded) {
            DocumentFile docFile = IOUtils.getSingleDocFile(context, f);
            if (docFile != null && docFile.exists()) {
                uploadedFileChecksums.put(f, getFileChecksum(f));
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
        ParcelUtils.writeMap(dest, filesForUploadAndSize);
        ParcelUtils.writeMap(dest, compressedFilesMap);
        ParcelUtils.writeMap(dest, fileUploadStatus);
        ParcelUtils.writeMap(dest, filePartialUploadProgress);
        ParcelUtils.writeLongArrayList(dest, uploadToCategoryParentage);
        dest.writeLong(uploadToCategory);
        dest.writeByte(privacyLevelWanted);
        dest.writeInt(jobConfigId);
        ParcelUtils.writeBool(dest, runInBackground);
        ParcelUtils.writeParcelable(dest, connectionPrefs);
        ParcelUtils.writeMap(dest, fileChecksums);
        ParcelUtils.writeBool(dest, finished);
        dest.writeLong(temporaryUploadAlbum);
        ParcelUtils.writeMap(dest, errors);
        ParcelUtils.writeParcelable(dest, playableMediaCompressionParams);
        ParcelUtils.writeParcelable(dest, imageCompressionParams);
        ParcelUtils.writeBool(dest, allowUploadOfRawVideosIfIncompressible);
        ParcelUtils.writeBool(dest, isDeleteFilesAfterUpload);
        dest.writeDouble(overallUploadProgress);
        dest.writeLong(totalDataToWorkOn);
        dest.writeLong(dataWorkAlreadyDone);
    }

    public boolean isDeleteFilesAfterUpload() {
        return isDeleteFilesAfterUpload;
    }

    public boolean isLocalFileNeededForUpload(Uri fileForUploadUri) {
        Integer status = fileUploadStatus.get(fileForUploadUri);
        return status == null || UploadJob.UPLOADING == status.intValue() || UploadJob.UPLOADED == status.intValue();
    }

    protected static class PartialUploadData implements Parcelable {

        private final String uploadName;
        private long totalBytesToUpload;
        private long bytesUploaded;
        private long countChunksUploaded;
        private String fileChecksum;
        private ResourceItem uploadedItem;

        public PartialUploadData(ResourceItem uploadedItem) {
            this.uploadName = uploadedItem != null ? uploadedItem.getName() : null;
            this.uploadedItem = uploadedItem;
        }

        public PartialUploadData(String uploadName, String fileChecksum, long totalBytesToUpload, long bytesUploaded, long countChunksUploaded) {
            this.fileChecksum = fileChecksum;
            this.uploadName = uploadName;
            this.bytesUploaded = bytesUploaded;
            this.countChunksUploaded = countChunksUploaded;
            this.totalBytesToUpload = totalBytesToUpload;
        }

        protected PartialUploadData(Parcel in) {
            uploadName = in.readString();
            totalBytesToUpload = in.readLong();
            bytesUploaded = in.readLong();
            countChunksUploaded = in.readLong();
            fileChecksum = in.readString();
            uploadedItem = in.readParcelable(ResourceItem.class.getClassLoader());
        }

        public static final Creator<PartialUploadData> CREATOR = new Creator<PartialUploadData>() {
            @Override
            public PartialUploadData createFromParcel(Parcel in) {
                return new PartialUploadData(in);
            }

            @Override
            public PartialUploadData[] newArray(int size) {
                return new PartialUploadData[size];
            }
        };

        public long getTotalBytesToUpload() {
            return totalBytesToUpload;
        }

        public ResourceItem getUploadedItem() {
            return uploadedItem;
        }

        public void setUploadedItem(ResourceItem uploadedItem) {
            this.uploadedItem = uploadedItem;
        }

        public long getBytesUploaded() {
            return bytesUploaded;
        }

        public long getCountChunksUploaded() {
            return countChunksUploaded;
        }

        public String getUploadName() {
            return uploadName;
        }

        public String getFileChecksum() {
            return fileChecksum;
        }

        public void setUploadStatus(String fileChecksum, long bytesUploaded, long countChunksUploaded) {
            this.fileChecksum = fileChecksum;
            this.bytesUploaded = bytesUploaded;
            this.countChunksUploaded = countChunksUploaded;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(uploadName);
            dest.writeLong(totalBytesToUpload);
            dest.writeLong(bytesUploaded);
            dest.writeLong(countChunksUploaded);
            dest.writeString(fileChecksum);
            dest.writeParcelable(uploadedItem, flags);
        }
    }

    public static class ImageCompressionParams implements Parcelable {
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
        private double quality = -1;
        private int audioBitrate = -1;

        public VideoCompressionParams() {

        }

        protected VideoCompressionParams(Parcel in) {
            quality = in.readDouble();
            audioBitrate = in.readInt();
        }

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

    public static class AreaErrors implements Parcelable {
        private final LinkedHashMap<Date,String> errorsRecorded = new LinkedHashMap<>();

        protected AreaErrors() {}

        protected AreaErrors(Parcel in) {
            ParcelUtils.readMap(in, errorsRecorded);
        }

        public void addError(@NonNull Date time, @NonNull String error) {
            synchronized (errorsRecorded) {
                errorsRecorded.put(time, error);
            }
        }

        /**
         * @return A copy of the current state.
         */
        public Set<Map.Entry<Date, String>> getEntrySet() {
            synchronized (errorsRecorded) {
                return new LinkedHashMap<>(errorsRecorded).entrySet();
            }
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            synchronized (errorsRecorded) {
                ParcelUtils.writeMap(dest, errorsRecorded);
            }
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<AreaErrors> CREATOR = new Creator<AreaErrors>() {
            @Override
            public AreaErrors createFromParcel(Parcel in) {
                return new AreaErrors(in);
            }

            @Override
            public AreaErrors[] newArray(int size) {
                return new AreaErrors[size];
            }
        };
    }

}