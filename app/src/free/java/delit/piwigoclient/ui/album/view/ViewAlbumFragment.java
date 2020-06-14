package delit.piwigoclient.ui.album.view;

import java.util.HashSet;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.ResourceItem;

public class ViewAlbumFragment extends AbstractViewAlbumFragment {

    @Override
    protected void showDownloadResourcesDialog(HashSet<ResourceItem> selectedItems) {
        getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_paid_feature_only), R.string.button_close);
    }
}
