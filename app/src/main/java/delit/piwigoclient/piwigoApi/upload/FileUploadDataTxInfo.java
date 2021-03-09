package delit.piwigoclient.piwigoApi.upload;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Objects;

import delit.libs.ui.util.ParcelUtils;

public class FileUploadDataTxInfo implements Parcelable {

    private final String uploadName;
    private final long totalBytesToUpload;
    private long bytesUploaded;
    private final ArrayList<Long> chunksUploaded;
    private String fileChecksum;
    private final long maxUploadChunkSizeBytes;

    public FileUploadDataTxInfo(String uploadName, String fileChecksum, long totalBytesToUpload, long bytesUploaded, long chunkUploaded, long maxUploadChunkSizeBytes) {
        this.fileChecksum = fileChecksum;
        this.uploadName = uploadName;
        this.bytesUploaded = bytesUploaded;
        this.chunksUploaded = new ArrayList<>();
        synchronized (chunksUploaded) {
            this.chunksUploaded.add(chunkUploaded);
        }
        this.totalBytesToUpload = totalBytesToUpload;
        this.maxUploadChunkSizeBytes = maxUploadChunkSizeBytes;
    }

    protected FileUploadDataTxInfo(Parcel in) {
        uploadName = in.readString();
        totalBytesToUpload = in.readLong();
        bytesUploaded = in.readLong();
        chunksUploaded = ParcelUtils.readLongArrayList(in);
        fileChecksum = in.readString();
        maxUploadChunkSizeBytes = in.readLong();
    }

    public static final Creator<FileUploadDataTxInfo> CREATOR = new Creator<FileUploadDataTxInfo>() {
        @Override
        public FileUploadDataTxInfo createFromParcel(Parcel in) {
            return new FileUploadDataTxInfo(in);
        }

        @Override
        public FileUploadDataTxInfo[] newArray(int size) {
            return new FileUploadDataTxInfo[size];
        }
    };

    public long getTotalBytesToUpload() {
        return totalBytesToUpload;
    }

    public long getBytesUploaded() {
        return bytesUploaded;
    }

    public int getChunksUploadedCount() {
        synchronized (chunksUploaded) {
            return chunksUploaded.size();
        }
    }


    /**
     * NOTE: caveat to this is that the chunks are always iterated through in numerically increasing order.
     *
     * @param defaultChunkId first possible missing chunkId
     * @return returns the parameter if that's the first missing. -1 if none missing
     */
    public long getFirstMissingChunk(long defaultChunkId) {
        synchronized (chunksUploaded) {
            if(chunksUploaded.size() == getTotalChunksToUpload()) {
                // if all chunks uploaded, return -1
                return -1;
            }
            int startIdx = chunksUploaded.indexOf(defaultChunkId);
            if(startIdx < 0) {
                // if the chunk we look for is missing, return that
                return defaultChunkId;
            }

            long lastChunkId = defaultChunkId;
            for (int i = startIdx; i < chunksUploaded.size(); i++) {
                long chunkId = chunksUploaded.get(i);
                // if there is a gap between the last known uploaded chunk and this uploaded chunk, return the first chunk in the gap
                if (chunkId > lastChunkId + 1) {
                    return lastChunkId + 1;
                }
                // otherwise, set the last known uploaded chunk to this one
                lastChunkId = chunkId;
            }
            // finally, return the next chunk
            return lastChunkId + 1;
        }
    }

    public String getUploadName() {
        return uploadName;
    }

    public String getFileChecksum() {
        return fileChecksum;
    }

    public void addUploadedChunk(String fileChecksum, long bytesUploaded, long chunkUploaded) {
        if(!Objects.equals(this.fileChecksum, fileChecksum)) {
            throw new IllegalStateException("Attempting to add chunk with different filechecksum");
        }
        this.bytesUploaded += bytesUploaded;
        synchronized (chunksUploaded) {
            this.chunksUploaded.add(chunkUploaded);
        }
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
        ParcelUtils.writeLongArrayList(dest, chunksUploaded);
        dest.writeString(fileChecksum);
        dest.writeLong(maxUploadChunkSizeBytes);
    }

    public long getMaxUploadChunkSizeBytes() {
        return maxUploadChunkSizeBytes;
    }

    public boolean isUploadFinished() {
        return totalBytesToUpload == bytesUploaded;
    }

    public boolean isHasUploadedChunk(long chunkId) {
        return chunksUploaded.contains(chunkId);
    }

    public long getTotalChunksToUpload() {
        return (long) Math.ceil(((double) totalBytesToUpload) / maxUploadChunkSizeBytes);
    }
}
