package delit.piwigoclient.ui.upload;

import android.net.Uri;

import java.util.List;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItemStub;

public class UploadFragment extends AbstractUploadFragment {
    public static UploadFragment newInstance(CategoryItemStub currentGallery, int actionId) {
        UploadFragment fragment = new UploadFragment();
        fragment.setArguments(fragment.buildArgs(currentGallery, actionId));
        return fragment;
    }

    @Override
    protected void updateFilesForUploadList(List<FilesToUploadRecyclerViewAdapter.UploadDataItem> folderItemsToBeUploaded) {
        FilesToUploadRecyclerViewAdapter adapter = getFilesForUploadViewAdapter();
        super.updateFilesForUploadList(folderItemsToBeUploaded);
        List<Uri> allFiles = adapter.getFiles();
        boolean maxItemCountReached = false;
        while(allFiles.size() > 5) {
            maxItemCountReached = true;
            adapter.remove(allFiles.get(allFiles.size()-1));
        }

        if(maxItemCountReached) {
            getUiHelper().showDetailedMsg(R.string.alert_information, getString(R.string.alert_message_max_upload_file_count_reached));
        }
    }
}
