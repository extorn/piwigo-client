package delit.piwigoclient.model;

import android.net.Uri;

import java.io.InputStream;

/**
 * Created by gareth on 17/05/17.
 */

public class UploadFileChunk {

    private final long jobId;
    private final Uri originalFile;
    private final String mimeType;
    private final long chunkId;
    private final long chunkCount;
    private final long uploadToAlbumId;
    private final InputStream chunkData;
    private final String filenameOnServer;
    private int uploadAttempts;

    public UploadFileChunk(long jobId, Uri originalFile, String filenameOnServer, long uploadToAlbumId, InputStream chunkData, long chunkId, long chunkCount, String mimeType) {
        this.originalFile = originalFile;
        this.filenameOnServer = filenameOnServer;
        this.chunkData = chunkData;
        this.jobId = jobId;
        this.chunkId = chunkId;
        this.chunkCount = chunkCount;
        this.uploadToAlbumId = uploadToAlbumId;
        this.mimeType = mimeType;
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

    public Uri getOriginalFile() {
        return originalFile;
    }

    public InputStream getChunkData() {
        return chunkData;
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
                && this.originalFile == other.originalFile;
    }

    @Override
    public int hashCode() {
        int hashCode = super.hashCode();
        hashCode += (3 * chunkId);
        hashCode += (5 * chunkCount);
        hashCode += (7 * jobId);
        hashCode += originalFile.hashCode();
        return hashCode;
    }


}
