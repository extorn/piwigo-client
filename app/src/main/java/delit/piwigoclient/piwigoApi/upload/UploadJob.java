package delit.piwigoclient.piwigoApi.upload;

import android.content.Context;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.util.IOUtils;
import delit.piwigoclient.util.Md5SumUtils;

public class UploadJob implements Serializable {

    public static final Integer COMPRESSED = 8; // file has been compressed successfully.

    public static final Integer CANCELLED = -1; // file has been removed from the upload job
    private static final long serialVersionUID = 3L;
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
    private final ArrayList<File> filesForUpload;
    private final HashMap<File, Integer> fileUploadStatus;
    private final HashMap<File, PartialUploadData> filePartialUploadProgress;
    private final ArrayList<Long> uploadToCategoryParentage;
    private final long uploadToCategory;
    private final int privacyLevelWanted;
    private final boolean compressVideosBeforeUpload;
    private int jobConfigId = -1;
    private boolean runInBackground;
    private ConnectionPreferences.ProfilePreferences connectionPrefs;
    private HashMap<File, String> fileChecksums;
    private boolean finished;
    private long temporaryUploadAlbum = -1;
    private volatile transient boolean submitted = false;
    private volatile transient boolean runningNow = false;
    private volatile transient boolean cancelUploadAsap;
    private transient File loadedFromFile;
    private LinkedHashMap<Date, String> errors = new LinkedHashMap<>();

    public UploadJob(ConnectionPreferences.ProfilePreferences connectionPrefs, long jobId, long responseHandlerId, ArrayList<File> filesForUpload, CategoryItemStub destinationCategory, boolean compressVideosBeforeUpload, int uploadedFilePrivacyLevel) {
        this.jobId = jobId;
        this.connectionPrefs = connectionPrefs;
        this.responseHandlerId = responseHandlerId;
        this.uploadToCategory = destinationCategory.getId();
        this.uploadToCategoryParentage = new ArrayList<>(destinationCategory.getParentageChain());
        this.privacyLevelWanted = uploadedFilePrivacyLevel;
        this.filesForUpload = new ArrayList<>(filesForUpload);
        this.fileUploadStatus = new HashMap<>(filesForUpload.size());
        this.filePartialUploadProgress = new HashMap<>(filesForUpload.size());
        this.compressVideosBeforeUpload = compressVideosBeforeUpload;
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

    public synchronized void markFileAsUploading(File fileForUpload) {
        fileUploadStatus.put(fileForUpload, UPLOADING);
    }

    public synchronized void markFileAsVerified(File fileForUpload) {
        fileUploadStatus.put(fileForUpload, VERIFIED);
    }

    public synchronized void markFileAsCorrupt(File fileForUpload) {
        fileUploadStatus.put(fileForUpload, CORRUPT);
    }

    public synchronized void markFileAsCompressed(File fileForUpload) {
        fileUploadStatus.put(fileForUpload, COMPRESSED);
    }

    public synchronized void markFileAsConfigured(File fileForUpload) {
        fileUploadStatus.put(fileForUpload, CONFIGURED);
    }

    public synchronized void markFileAsNeedsDelete(File fileForUpload) {
        fileUploadStatus.put(fileForUpload, REQUIRES_DELETE);
    }

    public synchronized void markFileAsDeleted(File fileForUpload) {
        fileUploadStatus.put(fileForUpload, DELETED);
    }

    public synchronized boolean needsUpload(File fileForUpload) {
        Integer status = fileUploadStatus.get(fileForUpload);
        return status == null || COMPRESSED.equals(status) || UPLOADING.equals(status);
    }

    public int getJobConfigId() {
        return jobConfigId;
    }

    public void setJobConfigId(int jobConfigId) {
        this.jobConfigId = jobConfigId;
    }

    public ArrayList<File> getFilesAwaitingUpload() {
        Set<File> filesProcessedToSomeDegree = fileUploadStatus.keySet();
        ArrayList<File> filesAwaitingUpload = new ArrayList<File>(filesForUpload);
        filesAwaitingUpload.removeAll(filesProcessedToSomeDegree);
        return filesAwaitingUpload;
    }

    //
//    public synchronized boolean isUploadOfFileInProgress(File fileForUpload) {
//        Integer status = fileUploadStatus.get(fileForUpload);
//        return status != null && status != CONFIGURED && status != DELETED && status != CANCELLED;
//    }

    public HashSet<File> getFilesProcessedToEnd() {
        return getFilesWithStatus(PENDING_APPROVAL, CONFIGURED, DELETED, CANCELLED);
    }

    public HashSet<File> getFilesSuccessfullyUploaded() {
        return getFilesWithStatus(PENDING_APPROVAL, CONFIGURED);
    }

    public HashSet<File> getFilesWithStatus(Integer... statuses) {
        HashSet<File> filesWithStatus = new HashSet<>();
        for (Map.Entry<File, Integer> fileStatusEntry : fileUploadStatus.entrySet()) {
            Integer status = fileStatusEntry.getValue();
            for (Integer i : statuses) {
                if (status.equals(i)) {
                    filesWithStatus.add(fileStatusEntry.getKey());
                }
            }
        }
        return filesWithStatus;
    }

    public HashSet<File> getFilesPendingApproval() {
        return getFilesWithStatus(PENDING_APPROVAL);
    }


    public synchronized boolean needsVerification(File fileForUpload) {
        return UPLOADED.equals(fileUploadStatus.get(fileForUpload));
    }

    public boolean isUploadVerified(File fileForUpload) {
        return VERIFIED.equals(fileUploadStatus.get(fileForUpload));
    }

    public boolean isUploadingData(File fileForUpload) {
        return UPLOADING.equals(fileUploadStatus.get(fileForUpload));
    }

    public boolean isFileCompressed(File fileForUpload) {
        return COMPRESSED.equals(fileUploadStatus.get(fileForUpload));
    }

    public boolean isUploadedFileVerified(File fileForUpload) {
        return VERIFIED.equals(fileUploadStatus.get(fileForUpload));
    }

    public synchronized boolean needsConfiguration(File fileForUpload) {
        return VERIFIED.equals(fileUploadStatus.get(fileForUpload));
    }

    public synchronized boolean needsDelete(File fileForUpload) {
        return REQUIRES_DELETE.equals(fileUploadStatus.get(fileForUpload));
    }

    public synchronized boolean needsDeleteAndThenReUpload(File fileForUpload) {
        return CORRUPT.equals(fileUploadStatus.get(fileForUpload));
    }

    public synchronized boolean isFileUploadComplete(File fileForUpload) {
        Integer status = fileUploadStatus.get(fileForUpload);
        return PENDING_APPROVAL.equals(status) || CONFIGURED.equals(status);
    }

    public synchronized boolean uploadItemRequiresAction(File file) {
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
    public synchronized boolean cancelFileUpload(File f) {
        Integer status = fileUploadStatus.get(f);
        fileUploadStatus.put(f, CANCELLED);
        return status == null;
    }

    public long getJobId() {
        return jobId;
    }

    public synchronized ArrayList<File> getFilesForUpload() {
        return filesForUpload;
    }

    public int getPrivacyLevelWanted() {
        return privacyLevelWanted;
    }

    public boolean isCancelUploadAsap() {
        return cancelUploadAsap;
    }

    public synchronized boolean isFileUploadStillWanted(File file) {
        Integer status = fileUploadStatus.get(file);
        return !CANCELLED.equals(status);
    }

    public ConnectionPreferences.ProfilePreferences getConnectionPrefs() {
        return connectionPrefs;
    }

    public long getUploadToCategory() {
        return uploadToCategory;
    }

    public synchronized void clearUploadProgress(File f) {
        fileUploadStatus.remove(f);
        filePartialUploadProgress.remove(f);
    }

    public synchronized int getCompressionProgress(File uploadJobKey) {
        Integer status = fileUploadStatus.get(uploadJobKey);
        if (COMPRESSED.equals(status)) {
            return 100;
        }
        if (compressVideosBeforeUpload && canCompressVideoFile(uploadJobKey)) {
            // if we've started uploading this file it must have been compressed first!
            return getUploadProgress(uploadJobKey) > 0 ? 100 : 0;
        }
        return 0;
    }

    public synchronized int getUploadProgress(File uploadJobKey) {

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

    public synchronized void calculateChecksums() {
        ArrayList<File> filesNotFinished = getFilesNotYetUploaded();
        boolean newJob = false;
        if (fileChecksums == null) {
            fileChecksums = new HashMap<>(filesForUpload.size());
            newJob = true;
        }
        for (File f : filesNotFinished) {
            if(!f.exists()) {
                // Remove file from upload list
                cancelFileUpload(f);
            } else {
                // recalculate checksums for all files not yet uploaded
                final String checksum = Md5SumUtils.calculateMD5(f);
                if (!newJob) {
                    fileChecksums.remove(f);
                }
                fileChecksums.put(f, checksum);
            }
        }
    }

    public synchronized Map<File, String> getFileChecksums() {
        return fileChecksums;
    }

    public synchronized String getFileChecksum(File fileForUpload) {
        return fileChecksums.get(fileForUpload);
    }

    public synchronized void addFileChecksum(File uploadJobKey, File fileForUpload) {
        String checksum = Md5SumUtils.calculateMD5(fileForUpload);
        fileChecksums.put(uploadJobKey, checksum);
    }

    public synchronized boolean isFinished() {
        return finished && !isRunningNow();
    }

    public synchronized void setFinished() {
        finished = true;
    }

    public synchronized void addFileUploaded(File fileForUpload, ResourceItem itemOnServer) {
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

    public synchronized ResourceItem getUploadedFileResource(File fileUploaded) {
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

    public synchronized ArrayList<File> getFilesNotYetUploaded() {
        ArrayList<File> filesToUpload = new ArrayList<>(filesForUpload);
        filesToUpload.removeAll(getFilesProcessedToEnd());
        Iterator<File> filesToUploadIter = filesToUpload.iterator();
        while(filesToUploadIter.hasNext()) {
            File f = filesToUploadIter.next();
            if(f.isDirectory()) {
                filesToUpload.remove(f);
            }
        }
        return filesToUpload;
    }

    public synchronized List<Long> getUploadToCategoryParentage() {
        return uploadToCategoryParentage;
    }

    public void markFileAsPartiallyUploaded(File uploadJobKey, String uploadName, String fileChecksum, long totalBytesToUpload, long bytesUploaded, long countChunksUploadedOkay) {
        PartialUploadData data = filePartialUploadProgress.get(uploadJobKey);
        if (data == null) {
            filePartialUploadProgress.put(uploadJobKey, new PartialUploadData(uploadName, fileChecksum, totalBytesToUpload, bytesUploaded, countChunksUploadedOkay));
        } else {
            data.setUploadStatus(fileChecksum, bytesUploaded, countChunksUploadedOkay);
        }
    }

    public Set<File> getFilesPartiallyUploaded() {
        return filePartialUploadProgress.keySet();
    }

    public PartialUploadData getChunksAlreadyUploadedData(File fileForUpload) {
        return filePartialUploadProgress.get(fileForUpload);
    }

    public void deleteChunksAlreadyUploadedData(File fileForUpload) {
        filePartialUploadProgress.remove(fileForUpload);
    }

    public boolean isFilePartiallyUploaded(File cancelledFile) {
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

    public Map<File, String> getFileToFilenamesMap() {
        Map<File, String> filenamesMap = new HashMap<>(filesForUpload.size());
        for (File f : filesForUpload) {
            filenamesMap.put(f, f.getName());
        }
        return filenamesMap;
    }

    public void filterPreviouslyUploadedFiles(HashMap<File, String> fileUploadedHashMap) {
        for (HashMap.Entry<File, String> fileUploadedEntry : fileUploadedHashMap.entrySet()) {
            File potentialDuplicateUpload = fileUploadedEntry.getKey();
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
        return loadedFromFile != null || filePartialUploadProgress.size() > 0;
    }

    public File getLoadedFromFile() {
        return loadedFromFile;
    }

    public void setLoadedFromFile(File loadedFromFile) {
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

    public boolean isCompressVideosBeforeUpload() {
        return compressVideosBeforeUpload;
    }

    public ArrayList<File> getVideosForUpload() {
        ArrayList<File> allFiles = getFilesForUpload();
        if (allFiles == null || allFiles.isEmpty()) {
            return new ArrayList<>();
        }

        MimeTypeMap map = MimeTypeMap.getSingleton();


        ArrayList<File> videoFilesToCompress = new ArrayList<>(allFiles.size());
        for (File f : allFiles) {
            String fileExt = IOUtils.getFileExt(f.getName());
            String mimeType = map.getMimeTypeFromExtension(fileExt);
            if (mimeType != null && mimeType.startsWith("video/")) {
                videoFilesToCompress.add(f);
            }
        }
        return videoFilesToCompress;
    }

    public File getCompressedFile(Context c, File f) {
        return new File(getCompressedVideosFolder(c), f.getName());
    }

    private File getCompressedVideosFolder(Context c) {
        File f = new File(c.getExternalCacheDir(), "compressed_vids_for_upload");
        if (!f.exists()) {
            f.mkdirs();
        }
        return f;
    }

    public boolean canCompressVideoFile(File rawVideo) {
        String fileExt = IOUtils.getFileExt(rawVideo.getName());
        if (fileExt == null) {
            return false;
        }
        fileExt = fileExt.toLowerCase();
        return fileExt.equals("mp4");
    }


    protected static class PartialUploadData implements Serializable {
        private static final long serialVersionUID = 3574283238335920169L;
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
    }
}