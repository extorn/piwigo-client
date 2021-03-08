package delit.piwigoclient.piwigoApi.upload;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;

import delit.libs.ui.util.ParcelUtils;
import delit.piwigoclient.model.piwigo.ResourceItem;

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

    public int getChunksUploaded() {
        synchronized (chunksUploaded) {
            return chunksUploaded.size();
        }
    }


    /**
     * @param defaultChunkId first possible missing chunkId
     * @return returns the parameter if that's the first missing.
     */
    public long getFirstMissingChunk(long defaultChunkId) {
        synchronized (chunksUploaded) {
            int startIdx = chunksUploaded.indexOf(defaultChunkId);
            if(startIdx < 0) {
                return defaultChunkId;
            }

            long lastChunkId = defaultChunkId;
            for (int i = startIdx; i < chunksUploaded.size(); i++) {
                long chunkId = chunksUploaded.get(i);

                if (chunkId > lastChunkId + 1) {
                    return lastChunkId + 1;
                }
                lastChunkId = chunkId;
            }
            return lastChunkId + 1;
        }
    }

    public String getUploadName() {
        return uploadName;
    }

    public String getFileChecksum() {
        return fileChecksum;
    }

    public void setUploadStatus(String fileChecksum, long bytesUploaded, long chunkUploaded) {
        this.fileChecksum = fileChecksum;
        this.bytesUploaded = bytesUploaded;
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

    public boolean hasUploadedChunk(long chunkId) {
        return chunksUploaded.contains(chunkId);
    }

    public long getChunksToUpload() {
        return (long) Math.ceil(((double) totalBytesToUpload) / maxUploadChunkSizeBytes);
    }
}
