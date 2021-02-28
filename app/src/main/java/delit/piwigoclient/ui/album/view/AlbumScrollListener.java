package delit.piwigoclient.ui.album.view;

import androidx.recyclerview.widget.RecyclerView;

import delit.libs.ui.view.recycler.EndlessRecyclerViewScrollListener;
import delit.piwigoclient.business.AlbumViewPreferences;

final class AlbumScrollListener extends EndlessRecyclerViewScrollListener {

    private final AbstractViewAlbumFragment abstractViewAlbumFragment;

    AlbumScrollListener(AbstractViewAlbumFragment abstractViewAlbumFragment, RecyclerView.LayoutManager viewLayoutMan) {
        super(viewLayoutMan);
        this.abstractViewAlbumFragment = abstractViewAlbumFragment;
    }

    @Override
    public void onLoadMore(int requestedPage, int totalItemsCount, RecyclerView view) {
        int pageToLoad = requestedPage;

        int pageSize = AlbumViewPreferences.getResourceRequestPageSize(abstractViewAlbumFragment.getPrefs(), abstractViewAlbumFragment.requireContext());
        int pageToActuallyLoad = abstractViewAlbumFragment.getPageToActuallyLoad(pageToLoad, pageSize);

        if (abstractViewAlbumFragment.getGalleryModel().isPageLoadedOrBeingLoaded(pageToActuallyLoad) || abstractViewAlbumFragment.getGalleryModel().isFullyLoaded()) {
            Integer missingPage = abstractViewAlbumFragment.getGalleryModel().getAMissingPage();
            if (missingPage != null) {
                pageToLoad = missingPage;
            } else {
                // already load this one by default so lets not double load it (or we've already loaded all items).
                return;
            }
        }
        abstractViewAlbumFragment.loadAlbumResourcesPage(pageToLoad);
    }
}
