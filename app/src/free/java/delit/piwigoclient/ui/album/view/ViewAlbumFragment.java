package delit.piwigoclient.ui.album.view;

import java.util.HashSet;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.subscription.PermittedActions;

public class ViewAlbumFragment<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  extends AbstractViewAlbumFragment<F,FUIH> {

    @Override
    public boolean isTagSelectionAllowed() {
        PermittedActions permittedActions = obtainActivityViewModel(requireActivity(), PermittedActions.class);
        return permittedActions.hasTags();
    }

    @Override
    protected void showDownloadResourcesDialog(HashSet<ResourceItem> selectedItems) {
        PermittedActions permittedActions = obtainActivityViewModel(requireActivity(), PermittedActions.class);
        if(!permittedActions.hasDownloads()) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_paid_or_subscription_feature_only), R.string.button_close);
            return;
        }
        super.showDownloadResourcesDialog(selectedItems);
    }
}
