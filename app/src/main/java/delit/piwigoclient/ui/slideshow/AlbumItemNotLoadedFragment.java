package delit.piwigoclient.ui.slideshow;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.fragment.MyFragment;

public class AlbumItemNotLoadedFragment<F extends AlbumItemNotLoadedFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>> extends MyFragment<F,FUIH> implements SlideshowItemView<ResourceItem> {

    private static final String ARG_AND_STATE_SLIDESHOW_PAGE_IDX = "slideshowPageIdx";

    private int slideshowPageIdx;

    public AlbumItemNotLoadedFragment(){
        //for the slideshow pager adapter to instantiate it
        super(R.layout.item_not_loaded);
    }

    public static <S extends ResourceItem> Bundle buildArgs(int slideshowPageIdx) {
        Bundle b = new Bundle();
        b.putInt(ARG_AND_STATE_SLIDESHOW_PAGE_IDX, slideshowPageIdx);
        return b;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(ARG_AND_STATE_SLIDESHOW_PAGE_IDX, slideshowPageIdx);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        slideshowPageIdx = getArguments().getInt(ARG_AND_STATE_SLIDESHOW_PAGE_IDX);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public ResourceItem getModel() {
        return null;
    }

    @Override
    public void onPageSelected() {

    }

    @Override
    public void onPageDeselected() {

    }

    @Override
    public int getPagerIndex() {
        return slideshowPageIdx;
    }

    @Override
    public void onPagerIndexChangedTo(int newPagerIndex) {
        slideshowPageIdx = newPagerIndex;
    }
}
