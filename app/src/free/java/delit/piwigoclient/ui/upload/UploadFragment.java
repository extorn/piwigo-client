package delit.piwigoclient.ui.upload;

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
        boolean maxItemCountReached = false;
        while (folderItemsToBeUploaded.size() > 0 && folderItemsToBeUploaded.size() > 5 - adapter.getItemCount()) {
            folderItemsToBeUploaded.remove(folderItemsToBeUploaded.size() - 1);
            maxItemCountReached = true;
        }
        super.updateFilesForUploadList(folderItemsToBeUploaded);
        if(maxItemCountReached) {
            getUiHelper().showDetailedMsg(R.string.alert_information, getString(R.string.alert_message_max_upload_file_count_reached));
        }
    }
}
