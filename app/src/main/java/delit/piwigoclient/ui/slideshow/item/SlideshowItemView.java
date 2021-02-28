package delit.piwigoclient.ui.slideshow.item;

import delit.libs.ui.view.recycler.MyFragmentRecyclerPagerAdapter;
import delit.piwigoclient.model.piwigo.ResourceItem;

public interface SlideshowItemView<T extends ResourceItem> extends MyFragmentRecyclerPagerAdapter.PagerItemView {
    T getModel();
}
