package delit.piwigoclient.piwigoApi.upload;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.util.Md5SumUtils;

public class UploadJob implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Integer CANCELLED = -1;
    private static final Integer UPLOADING = 0;
    private static final Integer UPLOADED = 1;
    private static final Integer VERIFIED = 2;
    private static final Integer CONFIGURED = 3;
    private static final Integer PENDING_APPROVAL = 4;
    private static final Integer REQUIRES_DELETE = 5;
    private static final Integer DELETED = 6;
    private final long jobId;
    private final long responseHandlerId;
    private final boolean useTempFolder;
    private final ArrayList<File> filesForUpload;
    private final HashMap<File, Integer> fileUploadStatus;
    private final HashMap<File, PartialUploadData> filePartialUploadProgress;
    private final ArrayList<Long> uploadToCategoryParentage;
    private final long uploadToCategory;
    private final int privacyLevelWanted;
    private ConnectionPreferences.ProfilePreferences connectionPrefs;
    private HashMap<File, String> fileChecksums;
    private boolean finished;
    private long temporaryUploadAlbum = -1;
    private volatile transient boolean submitted = false;
    private volatile transient boolean runningNow = false;

    public UploadJob(ConnectionPreferences.ProfilePreferences connectionPrefs, long jobId, long responseHandlerId, ArrayList<File> filesForUpload, CategoryItemStub destinationCategory, int uploadedFilePrivacyLevel, boolean useTempFolder) {
        this.jobId = jobId;
        this.connectionPrefs = connectionPrefs;
        this.responseHandlerId = responseHandlerId;
        this.uploadToCategory = destinationCategory.getId();
        this.uploadToCategoryParentage = new ArrayList<>(destinationCategory.getParentageChain());
        this.privacyLevelWanted = uploadedFilePrivacyLevel;
        this.filesForUpload = new ArrayList<>(filesForUpload);
        this.fileUploadStatus = new HashMap<>(filesForUpload.size());
        this.filePartialUploadProgress = new HashMap<>(filesForUpload.size());
        this.useTempFolder = useTempFolder;
    }

    public synchronized void markFileAsUploading(File fileForUpload) {
        fileUploadStatus.put(fileForUpload, UPLOADING);
    }

    public synchronized void markFileAsVerified(File fileForUpload) {
        fileUploadStatus.put(fileForUpload, VERIFIED);
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
        return status == null || UPLOADING.equals(status);
    }

    public boolean isUseTempFolder() {
        return useTempFolder;
    }


    //
//    public synchronized boolean isUploadOfFileInProgress(File fileForUpload) {
//        Integer status = fileUploadStatus.get(fileForUpload);
//        return status != null && status != CONFIGURED && status != DELETED && status != CANCELLED;
//    }

    public HashSet<File> getFilesProcessedToEnd() {
        return getFilesWithStatus(PENDING_APPROVAL, CONFIGURED, DELETED, CANCELLED);
    }

    public HashSet<File> getFilesWithStatus(Integer... statuses) {
        HashSet<File> filesProcessedToEnd = new HashSet<>();
        for (Map.Entry<File, Integer> fileStatusEntry : fileUploadStatus.entrySet()) {
            Integer status = fileStatusEntry.getValue();
            for (Integer i : statuses) {
                if (status.equals(i)) {
                    filesProcessedToEnd.add(fileStatusEntry.getKey());
                }
            }
        }
        return filesProcessedToEnd;
    }

    public HashSet<File> getFilesPendingApproval() {
        return getFilesWithStatus(PENDING_APPROVAL);
    }


    public synchronized boolean needsVerification(File fileForUpload) {
        return UPLOADED.equals(fileUploadStatus.get(fileForUpload));
    }

    public boolean isUploadingData(File fileForUpload) {
        return UPLOADING.equals(fileUploadStatus.get(fileForUpload));
    }

    public synchronized boolean needsConfiguration(File fileForUpload) {
        return VERIFIED.equals(fileUploadStatus.get(fileForUpload));
    }

    public synchronized boolean needsDelete(File fileForUpload) {
        return REQUIRES_DELETE.equals(fileUploadStatus.get(fileForUpload));
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

    public synchronized int getUploadProgress(File f) {

//        private static final Integer CANCELLED = -1;
//        private static final Integer UPLOADING = 0;
//        private static final Integer UPLOADED = 1;
//        private static final Integer VERIFIED = 2;
//        private static final Integer CONFIGURED = 3;
//        private static final Integer PENDING_APPROVAL = 4;
//        private static final Integer REQUIRES_DELETE = 5;
//        private static final Integer DELETED = 6;
        Integer status = fileUploadStatus.get(f);
        if (status == null) {
            status = Integer.MIN_VALUE;
        }
        int progress = 0;
        if (UPLOADING.equals(status)) {

            PartialUploadData progressData = filePartialUploadProgress.get(f);
            if (progressData == null) {
                progress = 0;
            } else {
                progress = Math.round((float) (((double) progressData.getBytesUploaded()) / f.length() * 90));
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
        if (fileChecksums == null) {
            fileChecksums = new HashMap<>(filesForUpload.size());
        } else {
            fileChecksums.clear();
        }
        for (File f : filesNotFinished) {
            final String checksum = Md5SumUtils.calculateMD5(f);
            fileChecksums.put(f, checksum);
        }
    }

    public synchronized Map<File, String> getFileChecksums() {
        return fileChecksums;
    }

    public synchronized String getFileChecksum(File fileForUpload) {
        return fileChecksums.get(fileForUpload);
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
        return filesToUpload;
    }

    public synchronized List<Long> getUploadToCategoryParentage() {
        return uploadToCategoryParentage;
    }

    public void markFileAsPartiallyUploaded(File fileForUpload, String uploadName, long bytesUploaded, long countChunksUploadedOkay) {
        String fileChecksum = fileChecksums.get(fileForUpload);
        PartialUploadData data = filePartialUploadProgress.get(fileForUpload);
        if (data == null) {
            filePartialUploadProgress.put(fileForUpload, new PartialUploadData(uploadName, fileChecksum, bytesUploaded, countChunksUploadedOkay));
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

    protected static class PartialUploadData implements Serializable {
        private final String uploadName;
        private long bytesUploaded;
        private long countChunksUploaded;
        private String fileChecksum;
        private ResourceItem uploadedItem;

        public PartialUploadData(ResourceItem uploadedItem) {
            this.uploadName = uploadedItem != null ? uploadedItem.getName() : null;
            this.uploadedItem = uploadedItem;
        }

        public PartialUploadData(String uploadName, String fileChecksum, long bytesUploaded, long countChunksUploaded) {
            this.fileChecksum = fileChecksum;
            this.uploadName = uploadName;
            this.bytesUploaded = bytesUploaded;
            this.countChunksUploaded = countChunksUploaded;
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