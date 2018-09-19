package delit.piwigoclient.ui.album.view;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.greenrobot.eventbus.EventBus;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.Identifiable;
import delit.piwigoclient.model.piwigo.ResourceContainer;
import delit.piwigoclient.ui.common.recyclerview.AlbumHeadingViewHolder;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapter;
import delit.piwigoclient.ui.common.recyclerview.CustomClickListener;
import delit.piwigoclient.ui.common.recyclerview.IdentifiableListViewAdapter;
import delit.piwigoclient.ui.events.AlbumItemSelectedEvent;

/**
 * {@link RecyclerView.Adapter} that can display a {@link GalleryItem}
 * FIXME This is broken. swap for a new class based upon IdentifiableListViewAdapter
 */
public class AlbumItemRecyclerViewAdapter<T extends Identifiable> extends IdentifiableListViewAdapter<AlbumItemRecyclerViewAdapterPreferences, GalleryItem, ResourceContainer<T, GalleryItem>, AlbumItemViewHolder<T>> {

    public AlbumItemRecyclerViewAdapter(final Context context, final ResourceContainer<T, GalleryItem> gallery, MultiSelectStatusListener multiSelectStatusListener, AlbumItemRecyclerViewAdapterPreferences prefs) {
        super(gallery, multiSelectStatusListener, prefs);
    }

    @NonNull
    protected View inflateView(@NonNull ViewGroup parent, int viewType) {
        View view;
        switch(viewType) {
            case GalleryItem.CATEGORY_TYPE:
                view = inflateNonMasonryAlbumView(parent);
                break;
            case GalleryItem.PICTURE_RESOURCE_TYPE:
            case GalleryItem.VIDEO_RESOURCE_TYPE:
                view = inflateNonMasonryResourceItemView(parent);
                break;
            case GalleryItem.ALBUM_HEADING_TYPE:
                view = inflateAlbumsHeadingView(parent);
                break;
            case GalleryItem.PICTURE_HEADING_TYPE:
                view = inflateResourcesHeadingView(parent);
                break;
            default:
                throw new RuntimeException("viewType not found ("+viewType+")");
        }
        return view;
    }

    private View inflateAlbumsHeadingView(ViewGroup parent) {
        return LayoutInflater.from(getContext())
                .inflate(R.layout.layout_galleryitem_albums_heading, parent, false);
    }

    private View inflateResourcesHeadingView(ViewGroup parent) {
        return LayoutInflater.from(getContext())
                .inflate(R.layout.layout_galleryitem_resources_heading, parent, false);
    }

    private View inflateNonMasonryResourceItemView(ViewGroup parent) {
        return LayoutInflater.from(getContext())
                .inflate(R.layout.layout_galleryitem_resource, parent, false);
    }

    private View inflateNonMasonryAlbumView(ViewGroup parent) {
        View view;
        view = LayoutInflater.from(getContext())
                .inflate(R.layout.layout_galleryitem_album_list, parent, false);
        return view;
    }

    @Override
    public AlbumItemViewHolder buildViewHolder(View view, int viewType) {

        switch (viewType) {
            case GalleryItem.CATEGORY_TYPE:
                return new CategoryItemViewHolder(view, this, viewType);
            case GalleryItem.VIDEO_RESOURCE_TYPE:
            case GalleryItem.PICTURE_RESOURCE_TYPE:
                return new ResourceItemViewHolder(view, this, viewType);
            case GalleryItem.ALBUM_HEADING_TYPE:
            case GalleryItem.PICTURE_HEADING_TYPE:
                return new AlbumHeadingViewHolder(view, this, viewType);
            default:
                return null;
        }
    }

    @Override
    public void onViewRecycled(@NonNull AlbumItemViewHolder holder) {
        if (holder != null) {
            holder.onRecycled();
        }
    }

    @Override
    public int getItemViewType(int position) {
        return getItemByPosition(position).getType();
    }

    @Override
    public void onBindViewHolder(@NonNull AlbumItemViewHolder holder, int position) {
        if (holder == null) {
            // adverts
            return;
        }
        GalleryItem newItem = getItemByPosition(position);
        if (!isHolderOutOfSync(holder, newItem)) {
            // rendering the same item
            switch (newItem.getType()) {
                case GalleryItem.VIDEO_RESOURCE_TYPE:
                case GalleryItem.PICTURE_RESOURCE_TYPE:
                    ((ResourceItemViewHolder) holder).updateCheckableStatus();
                default:
            }

        } else {
            super.onBindViewHolder(holder, position);
        }
    }

    public void redrawItem(AlbumItemViewHolder<T> vh, CategoryItem item) {
        // clone the item into the view holder item (will not be same object if serialization has occurred)
        vh.getItem().copyFrom(item, true);
        // find item index.

        int idx = getItemPosition(vh.getItem());
        notifyItemChanged(idx);
        // clear the item in the view holder (to ensure it is redrawn - will be reloaded from the galleryList).
        vh.setItem(null);
        onBindViewHolder(vh, idx);
    }

    @Override
    protected CustomClickListener<AlbumItemRecyclerViewAdapterPreferences, GalleryItem, AlbumItemViewHolder<T>> buildCustomClickListener(AlbumItemViewHolder<T> viewHolder) {
        return new AlbumItemCustomClickListener(viewHolder, this);
    }

    public abstract static class MultiSelectStatusAdapter extends BaseRecyclerViewAdapter.MultiSelectStatusAdapter {

        protected abstract void onCategoryLongClick(CategoryItem album);

        protected abstract void notifyAlbumThumbnailInfoLoadNeeded(CategoryItem mItem);

        @Override
        public void onItemClick(BaseRecyclerViewAdapter adapter, Object item) {
            super.onItemClick(adapter, item);
        }

        @Override
        public void onItemLongClick(BaseRecyclerViewAdapter adapter, Object item) {
            if (item instanceof CategoryItem) {
                onCategoryLongClick((CategoryItem) item);
            }
        }
    }

    private static class AlbumItemCustomClickListener<T extends Identifiable> extends CustomClickListener<AlbumItemRecyclerViewAdapterPreferences, GalleryItem, AlbumItemViewHolder<T>> {

        private final int maxManualRetries = 2;
        private int manualRetries = 0;

        public AlbumItemCustomClickListener(AlbumItemViewHolder<T> viewHolder, AlbumItemRecyclerViewAdapter<T> adapter) {
            super(viewHolder, adapter);
        }

        @Override
        public AlbumItemRecyclerViewAdapter<T> getParentAdapter() {
            return super.getParentAdapter();
        }

        private void onCategoryClick() {
            if (!getParentAdapter().getAdapterPrefs().isAllowItemSelection()) {
                //If not currently in multiselect mode
                AlbumItemSelectedEvent event = new AlbumItemSelectedEvent(getParentAdapter().getItemStore(), getViewHolder().getItem());
                EventBus.getDefault().post(event);
            }
        }

        private void onNonCategoryClick() {
            if (!getParentAdapter().getAdapterPrefs().isAllowItemSelection()) {
                //If not currently in multiselect mode
                AlbumItemSelectedEvent event = new AlbumItemSelectedEvent(getParentAdapter().getItemStore(), getViewHolder().getItem());
                EventBus.getDefault().post(event);
            } else if (getParentAdapter().getAdapterPrefs().isMultiSelectionEnabled()) {
                // Are allowing access to admin functions within the album

                // multi selection mode is enabled.
                if (getParentAdapter().getSelectedItemIds().contains(getViewHolder().getItemId())) {
                    getViewHolder().setChecked(false);
                } else {
                    getViewHolder().setChecked(true);
                }
                //TODO Not sure why we'd call this?
                getViewHolder().itemView.setPressed(false);
            }
        }

        @Override
        public void onClick(View v) {
            if (v == getViewHolder().mImageView && !getViewHolder().imageLoader.isImageLoaded() && getViewHolder().imageLoader.isImageUnavailable() && manualRetries < maxManualRetries) {
                manualRetries++;
                getViewHolder().imageLoader.loadNoCache();
            } else {
                if (getViewHolder().getItem().getType() == GalleryItem.CATEGORY_TYPE) {
                    onCategoryClick();
                } else {
                    onNonCategoryClick();
                }
            }
        }

        @Override
        public void onFillValues() {
            super.onFillValues();
            manualRetries = 0;
        }

        private void onCategoryLongClick() {
            if (getParentAdapter().getAdapterPrefs().isMultiSelectionEnabled() && getParentAdapter().getMultiSelectStatusListener() != null) {
                MultiSelectStatusAdapter multiSelectListener = getParentAdapter().getMultiSelectStatusListener();
                multiSelectListener.onCategoryLongClick((CategoryItem) getViewHolder().getItem());
            }
        }

        private void onNonCategoryLongClick() {
            if (getParentAdapter().getAdapterPrefs().isMultiSelectionEnabled()) {
                getParentAdapter().toggleItemSelection();
                if (getParentAdapter().getAdapterPrefs().isAllowItemSelection()) {
                    getViewHolder().setChecked(true);
                }
            }
        }

        @Override
        public boolean onLongClick(View v) {
            if (getViewHolder().getItem().getType() == GalleryItem.CATEGORY_TYPE) {
                onCategoryLongClick();
            } else {
                onNonCategoryLongClick();
            }
            return true;
        }
    }
}
