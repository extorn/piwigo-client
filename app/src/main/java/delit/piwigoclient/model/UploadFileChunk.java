package delit.piwigoclient.model;

import android.net.Uri;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Created by gareth on 17/05/17.
 */

public class UploadFileChunk {

    private final long jobId;
    private final Uri uploadJobItemKey;
    private final Uri fileBeingUploaded;
    private final String mimeType;
    private long chunkId;
    private final long chunkCount;
    private final long uploadToAlbumId;
    private final String filenameOnServer;
    private byte[] chunkData;
    private int chunkSize;
    private int uploadAttempts;
    private long totalFileBytesToUpload;
    private int maxChunkSize;

    public UploadFileChunk(long jobId, Uri uploadJobItemKey, Uri fileBeingUploaded, long totalFileBytesToUpload, String filenameOnServer, long uploadToAlbumId, byte[] chunkData, int bytesOfData, long chunkId, long chunkCount, String mimeType, int maxChunkSize) {
        this.uploadJobItemKey = uploadJobItemKey;
        this.fileBeingUploaded = fileBeingUploaded;
        this.filenameOnServer = filenameOnServer;
        this.chunkSize = bytesOfData;
        this.chunkData = chunkData;
        this.jobId = jobId;
        this.chunkId = chunkId;
        this.chunkCount = chunkCount;
        this.uploadToAlbumId = uploadToAlbumId;
        this.mimeType = mimeType;
        this.totalFileBytesToUpload = totalFileBytesToUpload;
        this.maxChunkSize = maxChunkSize;
    }

    public Uri getUploadJobItemKey() {
        return uploadJobItemKey;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getJobId() {
        return jobId;
    }

    public long getChunkId() {
        return chunkId;
    }

    public long getChunkCount() {
        return chunkCount;
    }

    public long getUploadToAlbumId() {
        return uploadToAlbumId;
    }

    public Uri getFileBeingUploaded() {
        return fileBeingUploaded;
    }

    public InputStream getChunkDataStream() {
        if(chunkData == null) {
            return null;
        }
        return new ByteArrayInputStream(chunkData, 0, chunkSize);
    }

    public String getFilenameOnServer() {
        return filenameOnServer;
    }

    public void decrementUploadAttempts() {
        uploadAttempts = Math.max(0, --uploadAttempts);
    }

    public void incrementUploadAttempts() {
        uploadAttempts++;
    }

    public int getUploadAttempts() {
        return uploadAttempts;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof UploadFileChunk)) {
            return false;
        }
        UploadFileChunk other = (UploadFileChunk) obj;
        return this.chunkId == other.chunkId
                && this.chunkCount == other.chunkCount
                && this.jobId == other.jobId
                && this.fileBeingUploaded == other.fileBeingUploaded;
    }

    @Override
    public int hashCode() {
        int hashCode = super.hashCode();
        hashCode += (3 * chunkId);
        hashCode += (5 * chunkCount);
        hashCode += (7 * jobId);
        hashCode += fileBeingUploaded.hashCode();
        return hashCode;
    }


    public int getChunkSizeBytes() {
        return chunkSize;
    }

    public void withData(byte[] chunkData, int bytesOfDataInChunk, long chunkId) {
        this.chunkData = chunkData;
        this.chunkSize = bytesOfDataInChunk;
        this.chunkId = chunkId;
    }

    public long getFileSizeBytes() {
        return totalFileBytesToUpload;
    }

    public int getMaxChunkSize() {
        return maxChunkSize;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("UploadFileChunk{");
        sb.append("jobId=").append(jobId);
        sb.append(", chunkId=").append(chunkId);
        sb.append(", chunkCount=").append(chunkCount);
        sb.append(", uploadJobItemKey=").append(uploadJobItemKey);
        sb.append(", fileBeingUploaded=").append(fileBeingUploaded);
        sb.append(", filenameOnServer='").append(filenameOnServer).append('\'');
        sb.append(", chunkSize=").append(chunkSize);
        sb.append(", totalFileBytesToUpload=").append(totalFileBytesToUpload);
        sb.append(", maxChunkSize=").append(maxChunkSize);
        sb.append('}');
        return sb.toString();
    }

    public long getServerChunkId() {
        // index from 0.
        return chunkId;
    }
}
