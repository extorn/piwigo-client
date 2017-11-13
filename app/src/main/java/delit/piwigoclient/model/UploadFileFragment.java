package delit.piwigoclient.model;

import java.io.File;

/**
 * Created by gareth on 17/05/17.
 */

public class UploadFileFragment {

    private final long jobId;
    private String data;
    private String fileChecksum;
    private int chunkId;
    private String name;
    private File originalFile;
    private int uploadAttempts;

    public UploadFileFragment(String name, File originalFile, String data, String checksum, long jobId, int chunkId) {
        this.name = name;
        this.originalFile = originalFile;
        this.data = data;
        this.fileChecksum = checksum;
        this.jobId = jobId;
        this.chunkId = chunkId;
    }

    public String getData() {
        return data;
    }

    public long getJobId() {
        return jobId;
    }

    public int getChunkId() {
        return chunkId;
    }

    public String getFileChecksum() {
        return fileChecksum;
    }

    public String getName() {
        return name;
    }

    public File getOriginalFile() {
        return originalFile;
    }

    public void incrementUploadAttempts() {
        uploadAttempts++;
    }

    public int getUploadAttempts() {
        return uploadAttempts;
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof UploadFileFragment)) {
            return false;
        }
        UploadFileFragment other = (UploadFileFragment)obj;
        return this.chunkId == other.chunkId
                && this.jobId == other.jobId
                && this.originalFile == other.originalFile;
    }

    @Override
    public int hashCode() {
        int hashCode = super.hashCode();
        hashCode += (3 * chunkId);
        hashCode += (5 * jobId);
        hashCode += originalFile.hashCode();
        return hashCode;
    }

    public void decrementUploadAttempts() {
        uploadAttempts = Math.max(0, --uploadAttempts);
    }
}
