package delit.piwigoclient.piwigoApi.upload;

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.core.content.MimeTypeFilter;
import androidx.documentfile.provider.DocumentFile;

import com.crashlytics.android.Crashlytics;

import java.io.File;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import delit.libs.ui.util.ParcelUtils;
import delit.libs.util.IOUtils;
import delit.libs.util.LegacyIOUtils;
import delit.libs.util.Md5SumUtils;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;

public class UploadJob implements Parcelable {

    private static final String TAG = "UploadJob";
    public static final Integer COMPRESSED = 8; // file has been compressed successfully.

    public static final Integer CANCELLED = -1; // file has been removed from the upload job
    public static final Integer UPLOADING = 0; // file bytes transfer in process
    public static final Integer UPLOADED = 1; // all file bytes uploaded but not checksum verified
    public static final Integer VERIFIED = 2; // file bytes match those on the client device
    public static final Integer CONFIGURED = 3; // all permissions etc sorted and file renamed
    public static final Integer PENDING_APPROVAL = 4; // if community plugin in use and file is otherwise completely sorted
    public static final Integer REQUIRES_DELETE = 5; // user cancels upload after file partially uploaded
    public static final Integer DELETED = 6; // file has been deleted from the server
    public static final Integer CORRUPT = 7; // (moves to this state if verification fails)

    private final long jobId;
    private final long responseHandlerId;
    private final ArrayList<Uri> filesForUpload;
    private HashMap<Uri, Uri> compressedFilesMap;
    private final HashMap<Uri, Integer> fileUploadStatus;
    private final HashMap<Uri, PartialUploadData> filePartialUploadProgress;
    private final ArrayList<Long> uploadToCategoryParentage;
    private final long uploadToCategory;
    private final byte privacyLevelWanted;
    private int jobConfigId = -1;
    private boolean runInBackground;
    private ConnectionPreferences.ProfilePreferences connectionPrefs;
    private HashMap<Uri, String> fileChecksums;
    private boolean finished;
    private long temporaryUploadAlbum = -1;
    private LinkedHashMap<Date, String> errors = new LinkedHashMap<>();
    private VideoCompressionParams videoCompressionParams;
    private ImageCompressionParams imageCompressionParams;
    private boolean allowUploadOfRawVideosIfIncompressible;
    private boolean isDeleteFilesAfterUpload;

    private volatile boolean submitted = false;
    private volatile boolean runningNow = false;
    private volatile boolean cancelUploadAsap;
    private DocumentFile loadedFromFile;
    private boolean wasLastRunCancelled;
    private WeakReference<Context> contextRef;


    public UploadJob(ConnectionPreferences.ProfilePreferences connectionPrefs, long jobId, long responseHandlerId, List<Uri> filesForUpload, CategoryItemStub destinationCategory, byte uploadedFilePrivacyLevel, boolean isDeleteFilesAfterUpload) {
        this.jobId = jobId;
        this.connectionPrefs = connectionPrefs;
        this.responseHandlerId = responseHandlerId;
        this.uploadToCategory = destinationCategory.getId();
        this.uploadToCategoryParentage = new ArrayList<>(destinationCategory.getParentageChain());
        this.privacyLevelWanted = uploadedFilePrivacyLevel;
        this.filesForUpload = new ArrayList<>(filesForUpload);
        this.fileUploadStatus = new HashMap<>(filesForUpload.size());
        this.filePartialUploadProgress = new HashMap<>(filesForUpload.size());
        this.compressedFilesMap = new HashMap<>();
        this.isDeleteFilesAfterUpload = isDeleteFilesAfterUpload;
    }

    protected UploadJob(Parcel in) {
        jobId = in.readLong();
        responseHandlerId = in.readLong();
        filesForUpload = ParcelUtils.readArrayList(in, Uri.class.getClassLoader());
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
        errors = ParcelUtils.readMap(in, errors, Date.class.getClassLoader());
        videoCompressionParams = ParcelUtils.readParcelable(in, UploadJob.VideoCompressionParams.class);
        imageCompressionParams = ParcelUtils.readParcelable(in, UploadJob.ImageCompressionParams.class);
        allowUploadOfRawVideosIfIncompressible = ParcelUtils.readBool(in);
        isDeleteFilesAfterUpload = ParcelUtils.readBool(in);
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

    public void withContext(Context context) {
        contextRef = new WeakReference<>(context);
    }

    public int getUploadProgress() {
        int filesCount = filesForUpload.size();
        long totalProgress = filesCount * 100;
        long jobProgress = 0;
        for (Uri f : filesForUpload) {
            if (CANCELLED.equals(fileUploadStatus.get(f))) {
                totalProgress -= 100;
                continue;
            }
            if ((isPhoto(f) && isCompressPhotosBeforeUpload()) || (isVideo(f) && isCompressVideosBeforeUpload())) {
                totalProgress += 100;
            }
            jobProgress += getCompressionProgress(f);
            jobProgress += getUploadProgress(f);

        }
        return (int) Math.rint(((double) jobProgress) / totalProgress * 100);
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
        ArrayList<Uri> filesAwaitingUpload = new ArrayList<>(filesForUpload);
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

    public HashSet<Uri> getFilesSuccessfullyUploaded() {
        return getFilesWithStatus(PENDING_APPROVAL, CONFIGURED);
    }

    public HashSet<Uri> getFilesWithStatus(Integer... statuses) {
        HashSet<Uri> filesWithStatus = new HashSet<>();
        for (Map.Entry<Uri, Integer> fileStatusEntry : fileUploadStatus.entrySet()) {
            Integer status = fileStatusEntry.getValue();
            for (Integer i : statuses) {
                if (status.equals(i)) {
                    filesWithStatus.add(fileStatusEntry.getKey());
                }
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
        Integer status = fileUploadStatus.get(fileForUpload);
        return PENDING_APPROVAL.equals(status) || CONFIGURED.equals(status);
    }

    public synchronized boolean uploadItemRequiresAction(Uri file) {
        Integer uploadStatus = fileUploadStatus.get(file);
        return !(uploadStatus == null || PENDING_APPROVAL.equals(uploadStatus) || CONFIGURED.equals(uploadStatus) || CANCELLED.equals(uploadStatus) || DELETED.equals(uploadStatus));
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

    public long getJobId() {
        return jobId;
    }

    public synchronized ArrayList<Uri> getFilesForUpload() {
        return filesForUpload;
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

    public synchronized int getCompressionProgress(Uri uploadJobKey) {
        Integer status = fileUploadStatus.get(uploadJobKey);
        if (COMPRESSED.equals(status)) {
            return 100;
        }
        if ((isCompressVideosBeforeUpload() && canCompressVideoFile(uploadJobKey)) || isCompressPhotosBeforeUpload()) {
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

    public synchronized Map<Uri, Md5SumUtils.Md5SumException> calculateChecksums() {
        Map<Uri, Md5SumUtils.Md5SumException> failures = new HashMap<>(0);
        ArrayList<Uri> filesNotFinished = getFilesNotYetUploaded();
        boolean newJob = false;
        if (fileChecksums == null) {
            fileChecksums = new HashMap<>(filesForUpload.size());
            newJob = true;
        }
        for (Uri f : filesNotFinished) {
            if(!IOUtils.exists(getContext(), f)) {
                // Remove file from upload list
                cancelFileUpload(f);
            } else if (needsUpload(f) || needsVerification(f)) {
                Uri fileForChecksumCalc = null;
                if (!((isPhoto(f) && isCompressPhotosBeforeUpload())
                        || canCompressVideoFile(f) && isCompressVideosBeforeUpload())) {
                    fileForChecksumCalc = f;
                } else if(getCompressedFile(f) != null) {
                    fileForChecksumCalc = getCompressedFile(f);
                }
                if(fileForChecksumCalc != null) {
                    // if its not a file we're going to compress but haven't yet

                    // recalculate checksums for all files not yet uploaded
                    String checksum = null;
                    try {
                        checksum = Md5SumUtils.calculateMD5(getContext().getContentResolver(), fileForChecksumCalc);
                    } catch (Md5SumUtils.Md5SumException e) {
                        failures.put(f, e);
                        Crashlytics.log(Log.DEBUG, TAG, "Error calculating MD5 hash for file. Noting failure");
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
        }
        return failures;
    }

    public synchronized Map<Uri, String> getFileChecksums() {
        return fileChecksums;
    }

    public synchronized String getFileChecksum(Uri fileForUpload) {
        return fileChecksums.get(fileForUpload);
    }

    public synchronized void addFileChecksum(Uri uploadJobKey, Uri fileForUpload) throws Md5SumUtils.Md5SumException {
        String checksum = Md5SumUtils.calculateMD5(getContext().getContentResolver(), fileForUpload);
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
        return getFilesNotYetUploaded().size() == 0 && getTemporaryUploadAlbum() < 0;
    }

    private @NonNull Context getContext() {
        return Objects.requireNonNull(contextRef.get());
    }

    public synchronized ArrayList<Uri> getFilesNotYetUploaded() {
        ArrayList<Uri> filesToUpload = new ArrayList<>(filesForUpload);
        filesToUpload.removeAll(getFilesProcessedToEnd());
        Iterator<Uri> filesToUploadIter = filesToUpload.iterator();
        while(filesToUploadIter.hasNext()) {
            Uri f = filesToUploadIter.next();
            DocumentFile docFile = DocumentFile.fromSingleUri(getContext(), f);
            if(docFile != null && docFile.isDirectory()) {
                filesToUploadIter.remove();
            }
        }
        return filesToUpload;
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

    public Set<Uri> getFilesPartiallyUploaded() {
        return filePartialUploadProgress.keySet();
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

    public Map<Uri, String> getFileToFilenamesMap(Context context) {
        Map<Uri, String> filenamesMap = new HashMap<>(filesForUpload.size());
        for (Uri f : filesForUpload) {
            filenamesMap.put(f, DocumentFile.fromSingleUri(context, f).getName());
        }
        return filenamesMap;
    }

    public void filterPreviouslyUploadedFiles(Map<Uri, String> fileUploadedHashMap) {
        for (HashMap.Entry<Uri, String> fileUploadedEntry : fileUploadedHashMap.entrySet()) {
            Uri potentialDuplicateUpload = fileUploadedEntry.getKey();
            if (filesForUpload.contains(potentialDuplicateUpload)) {
                // a file at this absolute path has been uploaded previously to this end point
                String checksum = fileChecksums.get(potentialDuplicateUpload);
                if (checksum != null) {
                    if (checksum.equals(fileUploadedEntry.getValue())) {
                        // the file is identical to that previously uploaded (checksum check)
                        if (!fileUploadStatus.containsKey(potentialDuplicateUpload)) {
                            // this is a fresh target for this job
                            filesForUpload.remove(fileUploadedEntry.getKey());
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

    public void recordError(Date date, String message) {
        errors.put(date, message);
    }

    public boolean hasErrors() {
        return errors.size() > 0;
    }

    public LinkedHashMap<Date, String> getErrors() {
        return errors;
    }

    public boolean isVideo(@NonNull Uri file) {
        String mimeType = getContext().getContentResolver().getType(file);
        return MimeTypeFilter.matches(mimeType,"video/*");
    }

    public boolean isPhoto(@NonNull Uri file) {
        String mimeType = getContext().getContentResolver().getType(file);
        return MimeTypeFilter.matches(mimeType,"image/*");
    }

    public DocumentFile addCompressedFile(Context c, Uri rawFileForUpload, String compressedMimeType) {
        String rawUploadFilename = IOUtils.getFilename(c, rawFileForUpload);
        String updatedFilename = LegacyIOUtils.changeFileExt(new File(rawUploadFilename), MimeTypeMap.getSingleton().getExtensionFromMimeType(compressedMimeType)).getName();
        DocumentFile compressedFile = getCompressedFilesFolder(c).createFile(compressedMimeType, updatedFilename);
        if (compressedFilesMap == null) {
            compressedFilesMap = new HashMap<>();
        }
        compressedFilesMap.put(rawFileForUpload, compressedFile.getUri());
        return compressedFile;
    }

    public Uri getCompressedFile(Uri rawFileForUpload) {
        if (compressedFilesMap == null) {
            return null;
        }
        return compressedFilesMap.get(rawFileForUpload);
    }

    private DocumentFile getCompressedFilesFolder(Context c) {
        File f = new File(c.getExternalCacheDir(), "compressed_vids_for_upload");
        if (!f.exists()) {
            if (!f.mkdirs()) {
                Crashlytics.log(Log.ERROR, TAG, "Unable to create folder for comrepessed files to be placed");
            }
        }
        return DocumentFile.fromFile(f);
    }

    public boolean canCompressVideoFile(Uri rawVideo) {
        return isVideo(rawVideo);
    }

    public void clearCancelUploadAsapFlag() {
        wasLastRunCancelled = true;
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

    public boolean isCompressVideosBeforeUpload() {
        return videoCompressionParams != null;
    }

    public boolean isCompressPhotosBeforeUpload() {
        return imageCompressionParams != null;
    }

    public VideoCompressionParams getVideoCompressionParams() {
        return videoCompressionParams;
    }

    public void setVideoCompressionParams(VideoCompressionParams videoCompressionParams) {
        this.videoCompressionParams = videoCompressionParams;
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

    public HashMap<Uri, String> getUploadedFilesLocalFileChecksums() {
        HashSet<Uri> filesUploaded = getFilesSuccessfullyUploaded();
        HashMap<Uri, String> uploadedFileChecksums = new HashMap<>(filesUploaded.size());
        for (Uri f : filesUploaded) {
            DocumentFile docFile = DocumentFile.fromSingleUri(getContext(), f);
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
        ParcelUtils.writeArrayList(dest, filesForUpload);
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
        ParcelUtils.writeParcelable(dest, videoCompressionParams);
        ParcelUtils.writeParcelable(dest, imageCompressionParams);
        ParcelUtils.writeBool(dest, allowUploadOfRawVideosIfIncompressible);
        ParcelUtils.writeBool(dest, isDeleteFilesAfterUpload);
    }

    public boolean isDeleteFilesAfterUpload() {
        return isDeleteFilesAfterUpload;
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

    public static class ImageCompressionParams implements Serializable, Parcelable {
        private static final long serialVersionUID = -646493907951140373L;
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

    public static class VideoCompressionParams implements Serializable, Parcelable {
        private static final long serialVersionUID = -2089863017299368689L;
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
    }
}