package delit.piwigoclient.ui.upload;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import delit.piwigoclient.ui.upload.list.UploadDataItemModel;

public class FilesForUploadViewModel extends AndroidViewModel {
    private UploadDataItemModel uploadDataItemModel;

    public FilesForUploadViewModel(@NonNull Application application) {
        super(application);
    }

    public void setFilesForUpload(UploadDataItemModel uploadDataItemsModel) {
        this.uploadDataItemModel = uploadDataItemsModel;
    }

    public UploadDataItemModel getUploadDataItemModel() {
        return uploadDataItemModel;
    }
}
