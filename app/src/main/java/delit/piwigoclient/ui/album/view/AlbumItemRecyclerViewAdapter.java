package delit.piwigoclient.ui.album.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.greenrobot.eventbus.EventBus;

import delit.libs.ui.view.recycler.BaseRecyclerViewAdapter;
import delit.libs.ui.view.recycler.CustomClickListener;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ResizingPicassoLoader;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PiwigoAlbum;
import delit.piwigoclient.model.piwigo.ResourceContainer;
import delit.piwigoclient.ui.common.recyclerview.IdentifiableListViewAdapter;
import delit.piwigoclient.ui.events.AlbumItemSelectedEvent;
import delit.piwigoclient.ui.model.ViewModelContainer;

/**
 * {@link RecyclerView.Adapter} that can display a {@link GalleryItem}
 */
public class AlbumItemRecyclerViewAdapter<LVA extends AlbumItemRecyclerViewAdapter<LVA,T,MSL,VH, RC>, T extends GalleryItem, MSL extends AlbumItemRecyclerViewAdapter.AlbumItemMultiSelectStatusAdapter<MSL,LVA, VH,RC,T>, VH extends AlbumItemViewHolder<VH, LVA, T, MSL, RC>, RC extends ResourceContainer<?, T>> extends IdentifiableListViewAdapter<LVA, AlbumItemRecyclerViewAdapterPreferences, T, RC, VH, MSL> {

    public AlbumItemRecyclerViewAdapter(@NonNull final Context context, final Class<? extends ViewModelContainer> modelType, final RC gallery, MSL multiSelectStatusListener, AlbumItemRecyclerViewAdapterPreferences prefs) {
        super(context, modelType, gallery, multiSelectStatusListener, prefs);
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
        return LayoutInflater.from(parent.getContext())
                .inflate(R.layout.layout_list_item_heading_album, parent, false);
    }

    private View inflateResourcesHeadingView(ViewGroup parent) {
        return LayoutInflater.from(parent.getContext())
                .inflate(R.layout.layout_list_item_heading_resources, parent, false);
    }

    private View inflateNonMasonryResourceItemView(ViewGroup parent) {
        return LayoutInflater.from(parent.getContext())
                .inflate(R.layout.layout_list_item_album_resource, parent, false);
    }

    private View inflateNonMasonryAlbumView(ViewGroup parent) {
        View view;
        view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.layout_list_item_album, parent, false);
        return view;
    }

    @NonNull
    @Override
    public VH buildViewHolder(View view, int viewType) {

        switch (viewType) {
            case GalleryItem.CATEGORY_TYPE:
                return (VH) new CategoryItemViewHolder(view, this, viewType);
            case GalleryItem.VIDEO_RESOURCE_TYPE:
            case GalleryItem.PICTURE_RESOURCE_TYPE:
                return (VH) new ResourceItemViewHolder(view, this, viewType);
            case GalleryItem.ALBUM_HEADING_TYPE:
            case GalleryItem.PICTURE_HEADING_TYPE:
                return (VH) new AlbumHeadingViewHolder(view, this, viewType);
            default:
                throw new IllegalStateException("No matching viewholder could be found");
        }
    }

    @Override
    public void onViewRecycled(@NonNull AlbumItemViewHolder holder) {
        holder.onRecycled();
    }

    @Override
    public int getItemViewType(int position) {
        return getItemByPosition(position).getType();
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        T newItem = getItemByPosition(position);
        if (!isHolderOutOfSync(holder, newItem)) {
            // rendering the same item
            switch (newItem.getType()) {
                case GalleryItem.VIDEO_RESOURCE_TYPE:
                case GalleryItem.PICTURE_RESOURCE_TYPE:
                    ((ResourceItemViewHolder) holder).updateCheckableStatus();
                    break;
                case GalleryItem.ALBUM_HEADING_TYPE:
                    break;
                default:
            }

        } else {
            super.onBindViewHolder(holder, position);
        }
    }

    @Override
    protected boolean isDirtyItemViewHolder(VH holder, T newItem) {
        if(!super.isDirtyItemViewHolder(holder, newItem)) {
            boolean catDirty = (holder.getItem() instanceof CategoryItem
            && newItem instanceof CategoryItem
            && ((CategoryItem) holder.getItem()).isAdminCopy() != ((CategoryItem) newItem).isAdminCopy());
            return catDirty;
        }
        return true;
    }

    public void redrawItem(VH vh, CategoryItem item) {
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
    protected CustomClickListener<MSL, LVA, AlbumItemRecyclerViewAdapterPreferences, T, VH> buildCustomClickListener(VH viewHolder) {
        return new AlbumItemCustomClickListener<>(getModelType(), viewHolder, (LVA) this);
    }

    public abstract static class AlbumItemMultiSelectStatusAdapter<MSL extends AlbumItemMultiSelectStatusAdapter<MSL,LVA,VH,RC,T>,LVA extends AlbumItemRecyclerViewAdapter<LVA, T, MSL, VH, RC> , VH extends AlbumItemViewHolder<VH, LVA, T, MSL, RC>, RC extends ResourceContainer<?, T>, T extends GalleryItem> extends BaseRecyclerViewAdapter.MultiSelectStatusAdapter<MSL,LVA,AlbumItemRecyclerViewAdapterPreferences,T,VH> {

        protected abstract void onCategoryLongClick(CategoryItem album);

        protected abstract void notifyAlbumThumbnailInfoLoadNeeded(CategoryItem mItem);

        @Override
        public void onItemClick(LVA adapter, T item) {
            super.onItemClick(adapter, item);
        }

        @Override
        public void onItemLongClick(LVA adapter, T item) {
            if (item instanceof CategoryItem) {
                onCategoryLongClick((CategoryItem) item);
            }
        }

        protected abstract void onCategoryClick(CategoryItem item);

        public void onAlbumHeadingClick(RC itemStore) {};
    }

    private static class AlbumItemCustomClickListener<T extends GalleryItem, LVA extends AlbumItemRecyclerViewAdapter<LVA,T,MSL,VH,RC>, MSL extends AlbumItemMultiSelectStatusAdapter<MSL,LVA,VH,RC,T>, VH extends AlbumItemViewHolder<VH, LVA, T, MSL, RC>, RC extends ResourceContainer<?, T>> extends CustomClickListener<MSL,LVA, AlbumItemRecyclerViewAdapterPreferences, T, VH> {

        private final Class<ViewModelContainer> modelType;
        private int manualRetries = 0;

        public AlbumItemCustomClickListener(Class<ViewModelContainer> modelType, VH viewHolder, LVA adapter) {
            super(viewHolder, adapter);
            this.modelType = modelType;
        }

        @Override
        public LVA getParentAdapter() {
            return super.getParentAdapter();
        }

        private void onCategoryClick() {
            if (getParentAdapter().getAdapterPrefs().isMultiSelectionEnabled() && getParentAdapter().getMultiSelectStatusListener() != null) {
                AlbumItemMultiSelectStatusAdapter multiSelectListener = getParentAdapter().getMultiSelectStatusListener();
                multiSelectListener.onCategoryClick((CategoryItem) getViewHolder().getItem());
            }
            AlbumItemSelectedEvent event = new AlbumItemSelectedEvent(modelType, (ResourceContainer<?, GalleryItem>) getParentAdapter().getItemStore(), getViewHolder().getItem());
            EventBus.getDefault().post(event);
        }

        private void onNonCategoryClick() {
            if (!getParentAdapter().getAdapterPrefs().isAllowItemSelection()) {
                //If not currently in multiselect mode
                AlbumItemSelectedEvent event = new AlbumItemSelectedEvent(modelType, (ResourceContainer<?, GalleryItem>) getParentAdapter().getItemStore(), getViewHolder().getItem());
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
            int maxManualRetries = 1;
            ResizingPicassoLoader<ImageView> imageLoader = getViewHolder().imageLoader;
            if (v == getViewHolder().mImageView && !imageLoader.isImageLoaded() && !imageLoader.isImageUnavailable() && manualRetries < maxManualRetries) {
                manualRetries++;
                imageLoader.cancelImageLoadIfRunning();
                imageLoader.loadFromServer();
            } else {
                if(getViewHolder().getItem() != null) {
                    if (getViewHolder().getItem().getType() == GalleryItem.CATEGORY_TYPE) {
                        onCategoryClick();
                    } else {
                        onNonCategoryClick();
                    }
                } else {
                    switch (getViewHolder().getItemViewType()) {
                        case GalleryItem.ALBUM_HEADING_TYPE:
                            onAlbumsHeadingClick();
                            break;
                        case GalleryItem.PICTURE_HEADING_TYPE:
                            onPicturesHeadingClick();
                            break;
                        default:
                            // do nothing.
                    }
                }
            }
        }

        private void onPicturesHeadingClick() {
            // do nothing for now.
        }

        private void onAlbumsHeadingClick() {
            RC itemStore = getParentAdapter().getItemStore();
            if (itemStore instanceof PiwigoAlbum) {
                if (getParentAdapter().getMultiSelectStatusListener() != null) {
                    MSL multiSelectListener = getParentAdapter().getMultiSelectStatusListener();
                    multiSelectListener.onAlbumHeadingClick(itemStore);
                }
                PiwigoAlbum album = (PiwigoAlbum)itemStore;
                boolean hideAlbums = !album.isHideAlbums();
                album.setHideAlbums(hideAlbums);
                AlbumHeadingViewHolder<?, ?, ?, ?, ?> viewHolder = (AlbumHeadingViewHolder<?, ?, ?, ?, ?>) getViewHolder();
                viewHolder.setSubAlbumCount(album.getSubAlbumCount());
                getParentAdapter().notifyDataSetChanged();
            }
        }

        @Override
        public void onFillValues() {
            super.onFillValues();
            manualRetries = 0;
        }

        private void onCategoryLongClick() {
            if (getParentAdapter().getAdapterPrefs().isMultiSelectionEnabled() && getParentAdapter().getMultiSelectStatusListener() != null) {
                MSL multiSelectListener = getParentAdapter().getMultiSelectStatusListener();
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
            GalleryItem item = getViewHolder().getItem();
            if(item != null) {
                if (getViewHolder().getItem().getType() == GalleryItem.CATEGORY_TYPE) {
                    onCategoryLongClick();
                } else {
                    onNonCategoryLongClick();
                }
                return true;
            }
            return false;
        }
    }
}
