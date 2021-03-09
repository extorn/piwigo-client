package delit.piwigoclient.piwigoApi.upload;

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.util.Date;
import java.util.Objects;

import delit.libs.core.util.Logging;
import delit.libs.util.IOUtils;
import delit.piwigoclient.model.piwigo.ResourceItem;

public class FileUploadDetails implements Parcelable {
    public static final Creator<FileUploadDetails> CREATOR = new Creator<FileUploadDetails>() {
        @Override
        public FileUploadDetails createFromParcel(Parcel in) {
            return new FileUploadDetails(in);
        }

        @Override
        public FileUploadDetails[] newArray(int size) {
            return new FileUploadDetails[size];
        }
    };
    private static final String TAG = "FileUploadDetails";
    protected static final int NOT_STARTED = 0;
    protected static final int COMPRESSING = 10; // file is mid compression
    protected static final int COMPRESSED = 20; // file has been compressed successfully.
    protected static final int UPLOADING = 30; // file bytes transfer in process
    protected static final int UPLOADED = 40; // all file bytes uploaded but not checksum verified
    protected static final int VERIFIED = 50; // file bytes match those on the client device
    protected static final int CONFIGURED = 60; // all permissions etc sorted and file renamed
    protected static final int PENDING_APPROVAL = 70; // if community plugin in use and file is otherwise completely sorted
    protected static final int CORRUPT = -400; // (moves to this state if verification fails)
    protected static final int DELETED = -410; // file has been deleted from the server (after user cancel OR after corrupted upload)
    protected static final int REQUIRES_DELETE = -420; // user cancels upload after file partially uploaded
    protected static final int CANCELLED = -423; // file has been removed from the upload job
    protected static final int UNAVAILABLE = -440;


    private final Uri fileUri;
    private final long fileSize;
    private FileUploadDataTxInfo uploadData;
    private int status = NOT_STARTED;
    private Uri compressedFileUri;
    private String checksum;
    private boolean processingFailed;
    private ProcessErrors errors;
    private boolean compressFileBeforeUpload;
    private boolean isDeleteAfterUpload;
    private ResourceItem uploadedResource;
    private String compressedFileChecksum;

    public FileUploadDetails(@NonNull Uri fileUri, long fileSize) {
        this.fileUri = fileUri;
        this.fileSize = fileSize;
    }

    protected FileUploadDetails(Parcel in) {
        fileUri = in.readParcelable(Uri.class.getClassLoader());
        uploadData = in.readParcelable(FileUploadDataTxInfo.class.getClassLoader());
        status = in.readInt();
        compressedFileUri = in.readParcelable(Uri.class.getClassLoader());
        fileSize = in.readLong();
        checksum = in.readString();
        processingFailed = in.readByte() != 0;
        errors = in.readParcelable(ProcessErrors.class.getClassLoader());
        compressFileBeforeUpload = in.readByte() != 0;
        isDeleteAfterUpload = in.readByte() != 0;
        uploadedResource = in.readParcelable(ResourceItem.class.getClassLoader());
        compressedFileChecksum = in.readString();
    }

    public static boolean isFileAvailable(Context context, @Nullable Uri f) {
        if (f == null) {
            return false;
        }
        IOUtils.getSingleDocFile(context, f);
        DocumentFile docFile = IOUtils.getSingleDocFile(context, f);
        return docFile == null || (!docFile.isDirectory() && docFile.exists());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(fileUri, flags);
        dest.writeParcelable(uploadData, flags);
        dest.writeInt(status);
        dest.writeParcelable(compressedFileUri, flags);
        dest.writeLong(fileSize);
        dest.writeString(checksum);
        dest.writeByte((byte) (processingFailed ? 1 : 0));
        dest.writeParcelable(errors, flags);
        dest.writeByte((byte) (compressFileBeforeUpload ? 1 : 0));
        dest.writeByte((byte) (isDeleteAfterUpload ? 1 : 0));
        dest.writeParcelable(uploadedResource, flags);
        dest.writeString(compressedFileChecksum);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public Uri getFileUri() {
        return fileUri;
    }

    public boolean isProcessingFailed() {
        return processingFailed;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void allowProcessing() {
        processingFailed = false;
    }

    public void cancelUpload() {
        status = CANCELLED;
    }

    public Uri getCompressedFileUri() {
        return compressedFileUri;
    }

    public void setCompressedFileUri(Uri compressedFileUri) {
        this.compressedFileUri = compressedFileUri;
    }

    public void setDeleteAfterUpload(boolean deleteAfterUpload) {
        isDeleteAfterUpload = deleteAfterUpload;
    }

    public void setStatusUploading() {
        status = UPLOADING;
    }

    public void setStatusCompressed() {
        status = COMPRESSED;
    }

    public void setStatusCorrupt() {
        status = CORRUPT;
    }

    public void setStatusVerified() {
        status = VERIFIED;
    }

    public void setStatusConfigured() {
        status = CONFIGURED;
    }

    public void setStatusRequiresDelete() {
        status = REQUIRES_DELETE;
    }

    public void setStatusDeleted() {
        status = DELETED;
    }

    public boolean needsUpload() {
        return isStatusIn(NOT_STARTED, COMPRESSED, UPLOADING);
    }

    private boolean isStatusIn(int... options) {
        for (int option : options) {
            if (status == option) {
                return true;
            }
        }
        return false;
    }

    public boolean hasNoActionToTake() {
        return isStatusIn(PENDING_APPROVAL, CONFIGURED, CANCELLED);
    }

    public boolean isVerified() {
        return status == VERIFIED;
    }

    public boolean isSuccessfullyUploaded() {
        return isStatusIn(PENDING_APPROVAL, CONFIGURED);
    }

    public boolean isUploadCancelled() {
        return status == CANCELLED;
    }

    public boolean isCompressionWanted() {
        return compressFileBeforeUpload;
    }

    public boolean isCompressionNeeded() {
        if (!compressFileBeforeUpload) {
            return false;
        } else {
            return isStatusIn(NOT_STARTED, COMPRESSING);
        }
    }

    public int getOverallUploadProgress() {
        int progress;
        if(isSuccessfullyUploaded()) {
            progress = 100;
        } else if(isVerified()) {
            progress = 95;
        } else if(status == UPLOADED) {
            progress = 90;
        } else {
            if (uploadData == null) {
                progress = 0;
            } else {
                progress = Math.round((float) (((double) uploadData.getBytesUploaded()) / uploadData.getTotalBytesToUpload() * 90));
            }
        }
        return progress;
    }

    public boolean isDeleteAfterUpload() {
        return isDeleteAfterUpload;
    }

    public ResourceItem getServerResource() {
        return uploadedResource;
    }

    public Uri getFileToBeUploaded() {
        if (isCompressionWanted() && compressedFileUri != null) {
            return compressedFileUri;
        }
        return fileUri;
    }

    public boolean isChecksumNeeded() {
        return isStatusIn(NOT_STARTED, COMPRESSING, COMPRESSED, UPLOADING, UPLOADED, CORRUPT);
    }

    public void setProcessingFailed() {
        processingFailed = true;
    }

    public boolean hasCompressedFile() {
        return compressedFileUri != null;
    }

    public void resetStatus() {
        status = NOT_STARTED;
        compressedFileUri = null;
        uploadedResource = null;
        uploadData = null;
    }

    public void setChecksum(Uri fileForChecksumCalc, String checksum) {
        if (fileForChecksumCalc.equals(fileUri)) {
            this.checksum = checksum;
        } else {
            this.compressedFileChecksum = checksum;
        }
    }

    public boolean isUploadNeeded() {
        return isStatusIn(NOT_STARTED, COMPRESSING, COMPRESSED, UPLOADING);
    }

    public @NonNull
    String getChecksumOfFileToUpload() {
        if (hasCompressedFile()) {
            return Objects.requireNonNull(compressedFileChecksum);
        }
        return Objects.requireNonNull(checksum);
    }

    public @NonNull
    String getFilename(@NonNull Context context) {
        if (fileUri != null) {
            return Objects.requireNonNull(IOUtils.getFilename(context, fileUri));
        } else if (compressedFileUri != null) {
            return Objects.requireNonNull(IOUtils.getFilename(context, compressedFileUri));
        }
        Logging.log(Log.ERROR, TAG, "No file found in file upload details");
        throw new IllegalStateException("No file found");
    }

    public boolean needsVerification() {
        return isUploadNeeded() || isStatusIn(UPLOADED, CORRUPT);
    }

    public boolean isReadyForConfiguration() {
        return isStatusIn(VERIFIED);
    }

    public boolean isAlreadyUploadedAndConfigured() {
        return isStatusIn(CONFIGURED, PENDING_APPROVAL);
    }

    public boolean isUploadProcessStarted() {
        return status != NOT_STARTED;
    }

    public void setPendingCommunityApproval() {
        status = PENDING_APPROVAL;
    }

    public synchronized void setServerResource(@NonNull ResourceItem itemOnServer) {
        uploadedResource = itemOnServer;
    }

    public FileUploadDataTxInfo getChunksAlreadyUploadedData() {
        return uploadData;
    }

    public void deleteChunksAlreadyUploadedData() {
        uploadData = null;
    }

    public boolean isFilePartiallyUploaded() {
        return !(uploadData == null || uploadData.getBytesUploaded() == 0);
    }

    public boolean needsConfiguration() {
        return status == VERIFIED;
    }

    public boolean needsDeleteFromServer() {
        return status == REQUIRES_DELETE;
    }

    public boolean isFileUploadCorrupt() {
        return status == CORRUPT;
    }

    public @Nullable
    ProcessErrors getErrors() {
        return errors;
    }

    public void addError(String error) {
        if(errors == null) {
            errors = new ProcessErrors();
        }
        errors.addError(new Date(), error);
    }

    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    public void setStatusUserCancelled() {
        status = CANCELLED;
    }

    public boolean isDoneWithLocalFiles() {
        return isStatusIn(VERIFIED) || hasNoActionToTake();
    }

    /**
     * Checks that a file exists to upload
     *
     * @param context context in which to load file
     * @return true if any file is available or if not compressed the original user selected file
     */
    public boolean isPossibleToUpload(@NonNull Context context) {
        Uri ftu = getFileToBeUploaded();
        if (isFileAvailable(context, ftu)) {
            return true;
        }
        if (compressedFileUri == ftu) {
            return isFileAvailable(context, getFileUri());
        }
        return false;
    }

    public void setStatusUnavailable() {
        status = UNAVAILABLE;
    }

    public void recordChunkUploaded(String fileChecksum, int chunkSizeBytes, long chunkId) {
        uploadData.addUploadedChunk(fileChecksum, chunkSizeBytes, chunkId);
    }

    public void recordChunkUploaded(String filenameOnServer, String fileChecksum, long fileSizeBytes, int chunkSizeBytes, long chunkId, int maxChunkSize) {
        if (uploadData != null) {
            Logging.log(Log.WARN, TAG, "Overwriting previous upload progress for file %1$s", getFileUri().getPath());
        }
        uploadData = new FileUploadDataTxInfo(filenameOnServer, fileChecksum, fileSizeBytes, chunkSizeBytes, chunkId, maxChunkSize);
    }

    public String getChecksumOfSelectedFile() {
        return checksum;
    }

    public int getStatus() {
        return status;
    }

    public void setCompressionNeeded(boolean needed) {
        compressFileBeforeUpload = needed;
    }

    public void clearErrors() {
        if(errors != null) {
            errors.clear();
        }
    }

    public void setStatusUploaded() {
        status = UPLOADED;
    }

    public boolean isStatusAllChunksUploaded() {
        return status == UPLOADED;
    }

    public boolean isUploadStarted() {
        return status != NOT_STARTED;
    }
}
