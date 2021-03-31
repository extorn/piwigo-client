package delit.piwigoclient.ui.upload;

import android.net.Uri;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.subscription.PermittedActions;
import delit.piwigoclient.ui.upload.list.UploadDataItem;

public class UploadFragment<F extends AbstractUploadFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends AbstractUploadFragment<F,FUIH> {
    public static UploadFragment<?,?> newInstance(CategoryItemStub currentGallery, int actionId) {
        UploadFragment<?,?> fragment = new UploadFragment<>();
        fragment.setArguments(fragment.buildArgs(currentGallery, actionId));
        return fragment;
    }

    @Override
    protected void updateFilesForUploadList(List<UploadDataItem> folderItemsToBeUploaded) {

        super.updateFilesForUploadList(folderItemsToBeUploaded);

        if(obtainActivityViewModel(requireActivity(), PermittedActions.class).hasLargeUploads()) {
            return;
        }

        boolean maxItemCountReached = false;
        FilesToUploadRecyclerViewAdapter adapter = getFilesForUploadViewAdapter();
        Set<Uri> allFiles = adapter.getFilesAndSizes().keySet();
        Iterator<Uri> iter = allFiles.iterator();
        while (iter.hasNext() && adapter.getFilesAndSizes().size() > 5) {
            maxItemCountReached = true;
            adapter.remove(iter.next());
        }
        if(maxItemCountReached) {
            getUiHelper().showDetailedMsg(R.string.alert_information, getString(R.string.alert_message_max_upload_file_count_reached));
        }
    }
}
