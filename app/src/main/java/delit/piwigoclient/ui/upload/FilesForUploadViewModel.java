package delit.piwigoclient.ui.upload;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

public class FilesForUploadViewModel extends AndroidViewModel {
    private FilesToUploadRecyclerViewAdapter.UploadDataItemModel uploadDataItemModel;

    public FilesForUploadViewModel(@NonNull Application application) {
        super(application);
    }

    public void setFilesForUpload(FilesToUploadRecyclerViewAdapter.UploadDataItemModel uploadDataItemsModel) {
        this.uploadDataItemModel = uploadDataItemsModel;
    }

    public FilesToUploadRecyclerViewAdapter.UploadDataItemModel getUploadDataItemModel() {
        return uploadDataItemModel;
    }
}
