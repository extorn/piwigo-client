package delit.piwigoclient.ui.album.view;

import java.util.HashMap;
import java.util.Map;

import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.model.piwigo.ResourceContainer;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.ui.common.FragmentUIHelper;

class AlbumViewAdapterListener<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>, MSL extends AlbumItemRecyclerViewAdapter.AlbumItemMultiSelectStatusAdapter<MSL,LVA,VH,RC,T>,LVA extends AlbumItemRecyclerViewAdapter<LVA, T, MSL, VH, RC> , VH extends AlbumItemViewHolder<VH, LVA, T, MSL, RC>, RC extends ResourceContainer<?, T>, T extends GalleryItem> extends AlbumItemRecyclerViewAdapter.AlbumItemMultiSelectStatusAdapter<MSL,LVA,VH,RC,T> {

    private F parentFragment;
    private Map<Long, CategoryItem> albumThumbnailLoadActions = new HashMap<>();

    public AlbumViewAdapterListener(F parentFragment) {
        this.parentFragment = parentFragment;
    }

    public Map<Long, CategoryItem> getAlbumThumbnailLoadActions() {
        return albumThumbnailLoadActions;
    }

    public void setAlbumThumbnailLoadActions(Map<Long, CategoryItem> albumThumbnailLoadActions) {
        this.albumThumbnailLoadActions = albumThumbnailLoadActions;
    }

    @Override
    public void onMultiSelectStatusChanged(LVA adapter, boolean multiSelectEnabled) {
//            bulkActionsContainer.setVisibility(multiSelectEnabled?VISIBLE:GONE);
    }

    @Override
    public void onItemSelectionCountChanged(LVA adapter, int selectionCount) {
        getParentFragment().onUserActionListSelectionChanged(selectionCount);
    }

    @Override
    public void onCategoryLongClick(CategoryItem album) {
        getParentFragment().onUserActionAlbumDeleteRequest(album);
    }

    public F getParentFragment() {
        return parentFragment;
    }

    @Override
    protected void onCategoryClick(CategoryItem item) {
        getParentFragment().onClickListItemCategory(item);
    }

    @Override
    public void onAlbumHeadingClick(RC itemStore) {
        int firstResourceIdx = itemStore.getItemCount() - itemStore.getResourcesCount();
        // need to do this because otherwise the next page of data won't be triggered to load.
        getParentFragment().scrollToListItem(firstResourceIdx);
    }

    @Override
    public void notifyAlbumThumbnailInfoLoadNeeded(CategoryItem mItem) {
        PictureResourceItem resourceItem = new PictureResourceItem(mItem.getRepresentativePictureId(), null, null, null, null, null);
        albumThumbnailLoadActions.put(getParentFragment().requestThumbnailLoad(resourceItem), mItem);
    }

    public boolean handleAlbumThumbnailInfoLoaded(long messageId, ResourceItem thumbnailResource) {
        CategoryItem item = albumThumbnailLoadActions.remove(messageId);
        if (item == null) {
            return false;
        }
        item.setThumbnailUrl(thumbnailResource.getThumbnailUrl());


        return true;
    }
}
