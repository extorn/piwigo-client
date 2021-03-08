package delit.piwigoclient.ui.upload.list;

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.Objects;

import delit.libs.util.IOUtils;
import delit.libs.util.Md5SumUtils;
import delit.libs.util.progress.ProgressListener;

public class UploadDataItem implements Parcelable {

    public static final Creator<UploadDataItem> CREATOR = new Creator<UploadDataItem>() {
        @Override
        public UploadDataItem createFromParcel(Parcel in) {
            return new UploadDataItem(in);
        }

        @Override
        public UploadDataItem[] newArray(int size) {
            return new UploadDataItem[size];
        }
    };
    private static long nextUid;
    protected final UploadProgressInfo uploadProgress;
    private final long uid;
    private final String mimeType;
    private final Uri uri;
    private String dataHashcode = null;
    private long dataLength = -1;
    private String filename;
    public static final Integer STATUS_COMPRESSED = 1;
    public static final Integer STATUS_ERROR = -1;
    public static final Integer STATUS_UPLOADING = 2;
    public static final Integer STATUS_UPLOADED = 3;

    public UploadDataItem(Uri uri, String filename, String mimeType, long dataLength) {
        this.uri = uri;
        this.mimeType = mimeType;
        uploadProgress = new UploadProgressInfo(uri);
        this.filename = filename;
        this.setDataLength(dataLength);
        uid = getNextUid();
    }

    protected UploadDataItem(Parcel in) {
        uid = in.readLong();
        mimeType = in.readString();
        uri = in.readParcelable(Uri.class.getClassLoader());
        setDataHashcode(in.readString());
        setDataLength(in.readLong());
        filename = in.readString();
        uploadProgress = in.readParcelable(UploadProgressInfo.class.getClassLoader());
    }

    private static long getNextUid() {
        nextUid++;
        if (nextUid < 0) {
            nextUid = 0;
        }
        return nextUid;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(uid);
        dest.writeString(mimeType);
        dest.writeParcelable(getUri(), flags);
        dest.writeString(getDataHashcode());
        dest.writeLong(getDataLength());
        dest.writeString(filename);
        dest.writeParcelable(uploadProgress, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public void calculateDataHashCode(Context context, ProgressListener progressListener) throws Md5SumUtils.Md5SumException {
        setDataHashcode(Md5SumUtils.calculateMD5(context.getContentResolver(), getUri(), progressListener));
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getItemUid() {
        return uid;
    }

    protected String getFileSizeStr(@NonNull Context context) {
        if(getDataLength() < 0) {
            Uri uri = this.getUri();
            if (uploadProgress != null && uploadProgress.getFileBeingUploaded() != null) {
                uri = uploadProgress.getFileBeingUploaded();
            }
            setDataLength(IOUtils.getFilesize(context, uri));
        }
        double sizeMb = IOUtils.bytesToMb(getDataLength());
        return String.format(Locale.getDefault(), "%1$.2fMB", sizeMb);
    }

    public String getFilename(Context context) {
        if(filename == null) {
            Uri currentFileUri = null;
            if(uploadProgress != null) {
                currentFileUri = uploadProgress.getFileBeingUploaded();
            }
            if(currentFileUri == null) {
                currentFileUri = getUri();
            }
            filename = IOUtils.getFilename(context, currentFileUri);
        }
        return filename;
    }

    public Uri getUri() {
        return uri;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UploadDataItem that = (UploadDataItem) o;
        return uid == that.uid;
    }

    @Override
    public int hashCode() {
        return Objects.hash(uid);
    }

    public long getDataLength() {
        return dataLength;
    }

    public void setDataLength(long dataLength) {
        this.dataLength = dataLength;
    }

    public String getDataHashcode() {
        return dataHashcode;
    }

    public void setDataHashcode(String dataHashcode) {
        this.dataHashcode = dataHashcode;
    }

    public void resetFilenameAndLength() {
        setDataLength(-1);
        filename = null;
    }

    public boolean isUploadFailed() {
        return uploadProgress != null && uploadProgress.isUploadFailed();
    }

    protected static class UploadProgressInfo implements Parcelable {

        private Uri fileBeingUploaded;
        private int uploadProgress;
        private int compressionProgress;
        private Integer uploadStatus;

        public UploadProgressInfo(Uri fileToUpload) {
            this.setFileBeingUploaded(fileToUpload); // this value will be replaced if we start getting compression progress updates
        }

        protected UploadProgressInfo(Parcel in) {
            fileBeingUploaded = in.readParcelable(Uri.class.getClassLoader());
            uploadProgress = in.readInt();
            compressionProgress = in.readInt();
            if (in.readByte() == 0) {
                uploadStatus = null;
            } else {
                uploadStatus = in.readInt();
            }
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeParcelable(fileBeingUploaded, flags);
            dest.writeInt(uploadProgress);
            dest.writeInt(compressionProgress);
            if (uploadStatus == null) {
                dest.writeByte((byte) 0);
            } else {
                dest.writeByte((byte) 1);
                dest.writeInt(uploadStatus);
            }
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<UploadProgressInfo> CREATOR = new Creator<UploadProgressInfo>() {
            @Override
            public UploadProgressInfo createFromParcel(Parcel in) {
                return new UploadProgressInfo(in);
            }

            @Override
            public UploadProgressInfo[] newArray(int size) {
                return new UploadProgressInfo[size];
            }
        };

        public boolean isUploadFailed() {
            return Objects.equals(uploadStatus, STATUS_ERROR);
        }

        public void setUploadStatus(Integer uploadStatus) {
            this.uploadStatus = uploadStatus;
        }

        public boolean isCompressingOrUploading() {
            return getUploadProgress() + getCompressionProgress() > 0;
        }

        public int getUploadProgress() {
            return uploadProgress;
        }

        public void setUploadProgress(int uploadProgress) {
            this.uploadProgress = uploadProgress;
        }

        private int getUploadStatus() {
            return uploadStatus;
        }

        public int getCompressionProgress() {
            return compressionProgress;
        }

        public void setCompressionProgress(int compressionProgress) {
            this.compressionProgress = compressionProgress;
        }

        public Uri getFileBeingUploaded() {
            return fileBeingUploaded;
        }

        public void setFileBeingUploaded(Uri fileBeingUploaded) {
            this.fileBeingUploaded = fileBeingUploaded;
        }

        public boolean isMidCompression() {
            return getUploadProgress() <= 0 && getCompressionProgress() > 0;
        }

        public boolean isMidUpload() {
            return getUploadProgress() > 0;
        }
    }
}
