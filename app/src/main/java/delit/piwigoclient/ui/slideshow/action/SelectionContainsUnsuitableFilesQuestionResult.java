package delit.piwigoclient.ui.slideshow.action;

import androidx.appcompat.app.AlertDialog;

import java.util.Set;

import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.slideshow.item.AbstractSlideshowItemFragment;

public class SelectionContainsUnsuitableFilesQuestionResult<F extends MyFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>, T extends ResourceItem> extends AbstractSlideshowItemFragment.BaseDownloadQuestionResult<F,FUIH,T> {

    private final Set<ResourceItem> items;
    private final String selectedPiwigoFilesizeName;

    public SelectionContainsUnsuitableFilesQuestionResult(FUIH uiHelper, Set<ResourceItem> items, String selectedPiwigoFilesizeName) {
        super(uiHelper);
        this.items = items;
        this.selectedPiwigoFilesizeName = selectedPiwigoFilesizeName;
    }

    @Override
    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
        doDownloadAction(items, selectedPiwigoFilesizeName, false);
    }
}
