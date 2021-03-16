package delit.piwigoclient.ui.upload.list;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.ParcelUtils;

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

    public UploadDataItemModel() {
        this.uploadDataItems = new ArrayList<>();
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
            if (item.uploadProgress != null && item.uploadProgress.getFileBeingUploaded().equals(f)) {
                return item;
            } else if(item.getUri().equals(f)) {
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
            if (item.getUri().equals(uri)) {
                return item;
            }
        }
        return null;
    }

    /**
     * Will not add items with a Uri or Hashcode already present.
     *
     * @param filesForUpload list of files to be uploaded
     * @return count of items actually added
     */
    public int addAll(@NonNull List<UploadDataItem> filesForUpload) {

        Set<Uri> uniqueUris = new HashSet<>(filesForUpload.size());
        Set<String> hashcodesForFilesToAll = new HashSet<>(filesForUpload.size());
        for(UploadDataItem item : filesForUpload) {
            hashcodesForFilesToAll.add(item.getDataHashcode());
            uniqueUris.add(item.getUri());
        }
        hashcodesForFilesToAll.remove(null); // remove null if present

        Set<Uri> urisAlreadyPresent = findUris(uniqueUris);
        Set<String> hashCodesAlreadyPresent = findDataHashCodes(hashcodesForFilesToAll);
        int itemsAdded = 0;
        for(UploadDataItem item : filesForUpload) {
            if(!hashCodesAlreadyPresent.contains(item.getDataHashcode()) && !urisAlreadyPresent.contains(item.getUri())) {
                uploadDataItems.add(item);
                itemsAdded++;
            }
        }
        return itemsAdded;
    }

    private Set<Uri> findUris(Set<Uri> urisToFind) {
        HashSet<Uri> urisFound = new HashSet<>();
        if(urisToFind.size() > 0) {
            for (UploadDataItem item : uploadDataItems) {
                if (urisToFind.contains(item.getUri())) {
                    urisFound.add(item.getUri());
                }
            }
        }
        return urisFound;
    }

    private Set<String> findDataHashCodes(Set<String> dataHashCodesToFind) {
        HashSet<String> foundHashcodes = new HashSet<>();
        if(dataHashCodesToFind.size() > 0) {
            for (UploadDataItem item : uploadDataItems) {
                if (dataHashCodesToFind.contains(item.getDataHashcode())) {
                    foundHashcodes.add(item.getDataHashcode());
                }
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
        return uploadDataItems.get(position).getUri();
    }

    public int size() {
        return uploadDataItems.size();
    }

    public UploadDataItem updateCompressionProgress(Uri fileBeingCompressed, Uri compressedFile, int percentageComplete) {
        UploadDataItem uploadDataItem = getUploadDataItemForFileSelectedForUpload(fileBeingCompressed);
        if (uploadDataItem == null) {
            String filename = fileBeingCompressed == null ? null : fileBeingCompressed.toString();
            Logging.log(Log.ERROR, TAG, "Update Compression Progress : Unable to locate upload progress object for file : " + filename);
        } else {
            UploadDataItem.UploadProgressInfo progress = uploadDataItem.uploadProgress;
            if (progress != null) {
                progress.setCompressionProgress(percentageComplete);
                progress.setFileBeingUploaded(compressedFile);
            }
            if(percentageComplete == 100 && !Objects.equals(fileBeingCompressed,compressedFile)) {
                // reset the data length - this will be re-calculated on next ui update.
                uploadDataItem.resetFilenameAndLength();

            }
        }
        return uploadDataItem;
    }

    public UploadDataItem updateUploadStatus(Uri fileBeingUploaded, Integer processingStatus) {
        UploadDataItem uploadDataItem = getUploadDataItemForFileSelectedForUpload(fileBeingUploaded);
        if (uploadDataItem == null) {
            String filename = fileBeingUploaded == null ? null : fileBeingUploaded.toString();
            Logging.log(Log.ERROR, TAG, "Update Upload Status (no data item) : Unable to locate upload progress object for file : %1$s %2$d%%", filename, processingStatus);
        } else {
            UploadDataItem.UploadProgressInfo progress = uploadDataItem.uploadProgress;
            if (progress != null) {
                if(progress.isUploadFailed()) {
                    progress.setUploadProgress(0);
                }
                progress.setUploadStatus(processingStatus);
            } else {
                Logging.log(Log.ERROR, TAG, "Unable to Update Upload Status : %1$s %2$d%%", fileBeingUploaded, processingStatus);
            }
        }
        return uploadDataItem;
    }

    public UploadDataItem updateUploadProgress(Uri fileUploadItemKey, int percentageComplete) {
        UploadDataItem uploadDataItem = getUploadDataItemForFileSelectedForUpload(fileUploadItemKey);
        if (uploadDataItem == null) {
            String filename = fileUploadItemKey == null ? null : fileUploadItemKey.toString();
            Logging.log(Log.ERROR, TAG, "Update Upload Progress (no data item) : Unable to locate upload progress object for file : %1$s %2$d%%", filename, percentageComplete);
        } else {
            UploadDataItem.UploadProgressInfo progress = uploadDataItem.uploadProgress;
            if (progress != null) {
                progress.setUploadProgress(percentageComplete);
            } else {
                String filename = fileUploadItemKey == null ? null : fileUploadItemKey.toString();
                Logging.log(Log.ERROR, TAG, "Update Upload Progress : Unable to locate upload progress object for file : " + filename);
            }
            if (progress != null) {
                progress.setUploadStatus(percentageComplete < 100 ? UploadDataItem.STATUS_UPLOADING : UploadDataItem.STATUS_UPLOADED);
            }
        }
        return uploadDataItem;
    }

    public void clear() {
        uploadDataItems.clear();
    }

    public Map<Uri,Long> getFilesSelectedForUpload() {
        Map<Uri,Long> filesSelectedForUpload = new HashMap<>(uploadDataItems.size());
        for (UploadDataItem item : uploadDataItems) {
            filesSelectedForUpload.put(item.getUri(), item.getDataLength());
        }
        return filesSelectedForUpload;
    }

    public int getItemPosition(UploadDataItem item) {
        return uploadDataItems.indexOf(item);
    }

    public void remove(int idxToRemove) {
        uploadDataItems.remove(idxToRemove);
    }


}
