package delit.piwigoclient.ui.upload;

import java.io.File;
import java.util.ArrayList;

import delit.piwigoclient.R;

public class UploadFragment extends AbstractUploadFragment {
    public static UploadFragment newInstance(long currentGalleryId, int actionId) {
        UploadFragment fragment = new UploadFragment();
        fragment.setArguments(fragment.buildArgs(currentGalleryId, actionId));
        return fragment;
    }

    @Override
    protected void updateFilesForUploadList(ArrayList<File> filesToBeUploaded) {
        FilesToUploadRecyclerViewAdapter adapter = (FilesToUploadRecyclerViewAdapter) getFilesForUploadView().getAdapter();
        boolean maxItemCountReached = false;
        while(filesToBeUploaded.size() > 0 && filesToBeUploaded.size() > 5 - adapter.getItemCount()) {
            filesToBeUploaded.remove(filesToBeUploaded.size() - 1);
            maxItemCountReached = true;
        }
        super.updateFilesForUploadList(filesToBeUploaded);
        if(maxItemCountReached) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_message_max_upload_file_count_reached));
        }
    }
}
