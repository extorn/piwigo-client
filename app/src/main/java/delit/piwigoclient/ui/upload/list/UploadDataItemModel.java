package delit.piwigoclient.ui.upload.list;

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.ParcelUtils;
import delit.libs.util.IOUtils;
import delit.libs.util.Md5SumUtils;
import delit.libs.util.progress.ProgressListener;

public class UploadDataItemModel implements Parcelable {

    public static final Creator<UploadDataItemModel> CREATOR
            = new Creator<UploadDataItemModel>() {
        public UploadDataItemModel createFromParcel(Parcel in) {
            return new UploadDataItemModel(in);
        }

        public UploadDataItemModel[] newArray(int size) {
            return new UploadDataItemModel[size];
        }
    };
    private static final String TAG = "UploadDataItemModel";
    private final ArrayList<UploadDataItem> uploadDataItems;

    public UploadDataItemModel(Parcel p) {
        uploadDataItems = ParcelUtils.readArrayList(p, UploadDataItem.class.getClassLoader());
    }

    public UploadDataItemModel(ArrayList<DocumentFile> filesToUpload) {
        this.uploadDataItems = new ArrayList<>();
        for (DocumentFile f : filesToUpload) {
            uploadDataItems.add(new UploadDataItem(f.getUri(), IOUtils.getFilename(f), f.getType(), f.length()));
        }
    }

    public ArrayList<UploadDataItem> getUploadDataItemsReference() {
        return uploadDataItems;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        ParcelUtils.writeArrayList(dest, uploadDataItems);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    private UploadDataItem getUploadDataItemForFileBeingUploaded(Uri f) {
        for (UploadDataItem item : uploadDataItems) {
            if (item.uploadProgress != null && item.uploadProgress.fileBeingUploaded.equals(f)) {
                return item;
            }
        }
        return null;
    }

    public boolean contains(Uri uri) {
        return getUploadDataItemForFileSelectedForUpload(uri) != null;
    }

    private UploadDataItem getUploadDataItemForFileSelectedForUpload(Uri uri) {
        for (UploadDataItem item : uploadDataItems) {
            if (item.uri.equals(uri)) {
                return item;
            }
        }
        return null;
    }

    /**
     * @param filesForUpload list of files to be uploaded
     * @return count of items actually added
     */
    public int addAll(List<UploadDataItem> filesForUpload) {

        Set<String> hashcodesForFilesToAll = new HashSet<>(filesForUpload.size());
        for(UploadDataItem item : filesForUpload) {
            hashcodesForFilesToAll.add(item.dataHashcode);
        }

        Set<String> hashCodesAlreadyPresent = findDataHashCodes(hashcodesForFilesToAll);
        int itemsAdded = 0;
        for(UploadDataItem item : filesForUpload) {
            if(!hashCodesAlreadyPresent.contains(item.dataHashcode)) {
                uploadDataItems.add(item);
                itemsAdded++;
            }
        }
        return itemsAdded;
    }

    private Set<String> findDataHashCodes(Set<String> dataHashCodesToFind) {
        HashSet<String> foundHashcodes = new HashSet<>();
        for(UploadDataItem item : uploadDataItems) {
            if(dataHashCodesToFind.contains(item.dataHashcode)) {
                foundHashcodes.add(item.dataHashcode);
            }
        }
        return foundHashcodes;
    }

    public boolean add(UploadDataItem uploadItem) {
        return addAll(Collections.singletonList(uploadItem)) == 1;
    }

    public long getItemUid(int position) {
        UploadDataItem uploadDataItem = uploadDataItems.get(position);
        return uploadDataItem.getItemUid();
    }

    public UploadDataItem getItemByUid(long itemUid) {
        for (UploadDataItem item : uploadDataItems) {
            if (itemUid == item.getItemUid()) {
                return item;
            }
        }
        return null;
    }

    public void remove(Uri fileSelectedForUpload) {
        UploadDataItem uploadItem = getUploadDataItemForFileSelectedForUpload(fileSelectedForUpload);
        uploadDataItems.remove(uploadItem);
    }

    public UploadDataItem get(int position) {
        return uploadDataItems.get(position);
    }

    public Uri getFileSelectedForUpload(int position) {
        return uploadDataItems.get(position).uri;
    }

    public int size() {
        return uploadDataItems.size();
    }

    public void updateCompressionProgress(Uri fileBeingCompressed, Uri compressedFile, int percentageComplete) {
        UploadDataItem uploadDataItem = getUploadDataItemForFileSelectedForUpload(fileBeingCompressed);
        if (uploadDataItem == null) {
            String filename = fileBeingCompressed == null ? null : fileBeingCompressed.toString();
            Logging.log(Log.ERROR, TAG, "Update Compression Progress : Unable to locate upload progress object for file : " + filename);
        } else {
            UploadProgressInfo progress = uploadDataItem.uploadProgress;
            if (progress != null) {
                progress.compressionProgress = percentageComplete;
                progress.fileBeingUploaded = compressedFile;
            }
        }
    }

    public void updateUploadProgress(Uri fileBeingUploaded, int percentageComplete) {
        UploadDataItem uploadDataItem = getUploadDataItemForFileSelectedForUpload(fileBeingUploaded);
        if (uploadDataItem == null) {
            String filename = fileBeingUploaded == null ? null : fileBeingUploaded.toString();
            Logging.log(Log.ERROR, TAG, "Update Upload Progress (no data item) : Unable to locate upload progress object for file : %1$s %2$d%%", filename, percentageComplete);
        } else {
            UploadProgressInfo progress = uploadDataItem.uploadProgress;
            if (progress != null) {
                progress.uploadProgress = percentageComplete;
            } else {
                // we're uploading a compressed file
                uploadDataItem = Objects.requireNonNull(getUploadDataItemForFileBeingUploaded(fileBeingUploaded));
                progress = uploadDataItem.uploadProgress;
                if (progress != null) {
                    progress.uploadProgress = percentageComplete;
                } else {
                    String filename = fileBeingUploaded == null ? null : fileBeingUploaded.toString();
                    Logging.log(Log.ERROR, TAG, "Update Upload Progress : Unable to locate upload progress object for file : " + filename);
                }
            }
        }
    }

    public void clear() {
        uploadDataItems.clear();
    }

    public Map<Uri,Long> getFilesSelectedForUpload() {
        Map<Uri,Long> filesSelectedForUpload = new HashMap<>(uploadDataItems.size());
        for (UploadDataItem item : uploadDataItems) {
            filesSelectedForUpload.put(item.uri, item.dataLength);
        }
        return filesSelectedForUpload;
    }

    public int getItemPosition(UploadDataItem item) {
        return uploadDataItems.indexOf(item);
    }

    public void remove(int idxToRemove) {
        uploadDataItems.remove(idxToRemove);
    }


    public static class UploadDataItem implements Parcelable {

        private static long nextUid;
        private final long uid;
        private final String mimeType;
        private final Uri uri;
        private String dataHashcode = null;
        private long dataLength = -1;
        private String filename;
        protected final UploadProgressInfo uploadProgress;

        public UploadDataItem(Uri uri, String filename, String mimeType, long dataLength) {
            this.uri = uri;
            this.mimeType = mimeType;
            uploadProgress = new UploadProgressInfo(uri);
            this.filename = filename;
            this.dataLength = dataLength;
            uid = getNextUid();
        }

        protected UploadDataItem(Parcel in) {
            uid = in.readLong();
            mimeType = in.readString();
            uri = in.readParcelable(Uri.class.getClassLoader());
            dataHashcode = in.readString();
            dataLength = in.readLong();
            filename = in.readString();
            uploadProgress = in.readParcelable(UploadProgressInfo.class.getClassLoader());
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(uid);
            dest.writeString(mimeType);
            dest.writeParcelable(uri, flags);
            dest.writeString(dataHashcode);
            dest.writeLong(dataLength);
            dest.writeString(filename);
            dest.writeParcelable(uploadProgress, flags);
        }

        @Override
        public int describeContents() {
            return 0;
        }

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

        public void calculateDataHashCode(Context context, ProgressListener progressListener) throws Md5SumUtils.Md5SumException {
            dataHashcode = Md5SumUtils.calculateMD5(context.getContentResolver(), uri, progressListener);
        }

        private static long getNextUid() {
            nextUid++;
            if (nextUid < 0) {
                nextUid = 0;
            }
            return nextUid;
        }

        public String getMimeType() {
            return mimeType;
        }

        public long getItemUid() {
            return uid;
        }

        protected String getFileSizeStr(@NonNull Context context) {
            if(dataLength < 0) {
                Uri uri = this.uri;
                if (uploadProgress != null && uploadProgress.fileBeingUploaded != null) {
                    uri = uploadProgress.fileBeingUploaded;
                }
                dataLength = IOUtils.getFilesize(context, uri);
            }
            double sizeMb = IOUtils.bytesToMb(dataLength);
            return String.format(Locale.getDefault(), "%1$.2fMB", sizeMb);
        }

        public String getFilename(Context context) {
            if(filename == null) {
                filename = IOUtils.getFilename(context, uri);
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
    }

    protected static class UploadProgressInfo implements Parcelable {

        public static final Creator<UploadProgressInfo> CREATOR
                = new Creator<UploadProgressInfo>() {
            public UploadProgressInfo createFromParcel(Parcel in) {
                return new UploadProgressInfo(in);
            }

            public UploadProgressInfo[] newArray(int size) {
                return new UploadProgressInfo[size];
            }
        };
        private Uri fileBeingUploaded;
        private int uploadProgress;
        private int compressionProgress;

        public boolean inProgress() {
            return uploadProgress + compressionProgress > 0;
        }

        public UploadProgressInfo(Parcel p) {
            fileBeingUploaded = ParcelUtils.readParcelable(p, Uri.class);
            uploadProgress = p.readInt();
            compressionProgress = p.readInt();
        }

        public int getUploadProgress() {
            return uploadProgress;
        }

        public UploadProgressInfo(Uri fileToUpload) {
            this.fileBeingUploaded = fileToUpload; // this value will be replaced if we start getting compression progress updates
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            ParcelUtils.writeParcelable(dest, fileBeingUploaded);
            dest.writeInt(uploadProgress);
            dest.writeInt(compressionProgress);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public int getCompressionProgress() {
            return compressionProgress;
        }
    }
}
