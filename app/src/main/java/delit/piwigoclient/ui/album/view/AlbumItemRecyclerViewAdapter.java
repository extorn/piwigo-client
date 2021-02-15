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
import delit.piwigoclient.ui.album.AlbumItemBaseRecyclerViewAdapter;
import delit.piwigoclient.ui.events.AlbumItemSelectedEvent;
import delit.piwigoclient.ui.model.ViewModelContainer;

/**
 * {@link RecyclerView.Adapter} that can display a {@link GalleryItem}
 */
public class AlbumItemRecyclerViewAdapter<LVA extends AlbumItemRecyclerViewAdapter<LVA,T,MSL,VH, RC>, T extends GalleryItem, MSL extends AlbumItemRecyclerViewAdapter.AlbumItemMultiSelectStatusAdapter<MSL,LVA, VH,RC,T>, VH extends AlbumItemViewHolder<VH, LVA, T, MSL, RC>, RC extends ResourceContainer<?, T>> extends AlbumItemBaseRecyclerViewAdapter<LVA, T, MSL, VH, RC> {

    public AlbumItemRecyclerViewAdapter(@NonNull final Context context, final Class<? extends ViewModelContainer> modelType, final RC gallery, MSL multiSelectStatusListener, AlbumItemRecyclerViewAdapterPreferences prefs) {
        super(context, modelType, gallery, multiSelectStatusListener, prefs);
    }

    @Override
    protected View inflateChildAlbumsHeader(ViewGroup parent) {
        return LayoutInflater.from(parent.getContext())
                .inflate(R.layout.layout_list_item_heading_album, parent, false);
    }

    @Override
    protected View inflateChildResourcesHeader(ViewGroup parent) {
        return LayoutInflater.from(parent.getContext())
                .inflate(R.layout.layout_list_item_heading_resources, parent, false);
    }

    @Override
    protected View inflateVideoResourceView(ViewGroup parent) {
        return LayoutInflater.from(parent.getContext())
                .inflate(R.layout.layout_list_item_album_resource, parent, false);
    }

    @Override
    protected View inflatePictureResourceView(ViewGroup parent) {
        return LayoutInflater.from(parent.getContext())
                .inflate(R.layout.layout_list_item_album_resource, parent, false);
    }

    @Override
    protected View inflateChildAlbumView(ViewGroup parent) {
        View view;
        view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.layout_list_item_album, parent, false);
        return view;
    }

    @Override
    protected VH buildViewHolderChildAlbum(View view, int viewType) {
        return (VH) new CategoryItemViewHolder(view, this, viewType);
    }

    @Override
    protected VH buildViewHolderChildAlbumsHeader(View view, int viewType) {
        return (VH) new AlbumHeadingViewHolder(view, this, viewType);
    }

    @Override
    protected VH buildViewHolderChildPictureResource(View view, AlbumItemBaseRecyclerViewAdapter<LVA, T, MSL, VH, RC> lvatmslvhrcAlbumItemBaseRecyclerViewAdapter, int viewType) {
        return (VH) new ResourceItemViewHolder(view, this, viewType);
    }

    @Override
    protected VH buildViewHolderChildResourcesHeader(View view, int viewType) {
        return (VH) new AlbumHeadingViewHolder(view, this, viewType);
    }

    @Override
    protected VH buildViewHolderChildVideoResources(View view, int viewType) {
        return (VH) new ResourceItemViewHolder(view, this, viewType);
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
    protected void onRebindViewHolderWithSameData(@NonNull VH holder, int position, T newItem) {
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

        private void onClickCategory() {
            if (getParentAdapter().getAdapterPrefs().isMultiSelectionEnabled() && getParentAdapter().getMultiSelectStatusListener() != null) {
                AlbumItemMultiSelectStatusAdapter multiSelectListener = getParentAdapter().getMultiSelectStatusListener();
                multiSelectListener.onCategoryClick((CategoryItem) getViewHolder().getItem());
            }
            AlbumItemSelectedEvent event = new AlbumItemSelectedEvent(modelType, (ResourceContainer<?, GalleryItem>) getParentAdapter().getItemStore(), getViewHolder().getItem());
            EventBus.getDefault().post(event);
        }

        private void onClickNonCategory() {
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
                        onClickCategory();
                    } else {
                        onClickNonCategory();
                    }
                } else {
                    switch (getViewHolder().getItemViewType()) {
                        case GalleryItem.ALBUM_HEADING_TYPE:
                            onClickAlbumsHeading();
                            break;
                        case GalleryItem.PICTURE_HEADING_TYPE:
                            onClickPicturesHeading();
                            break;
                        default:
                            // do nothing.
                    }
                }
            }
        }

        private void onClickPicturesHeading() {
            // do nothing for now.
        }

        private void onClickAlbumsHeading() {
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
                viewHolder.setSubAlbumCount(album.getChildAlbumCount());
                getParentAdapter().notifyDataSetChanged();
            }
        }

        @Override
        public void onFillValues() {
            super.onFillValues();
            manualRetries = 0;
        }

        private void onLongClickCategory() {
            if (getParentAdapter().getAdapterPrefs().isMultiSelectionEnabled() && getParentAdapter().getMultiSelectStatusListener() != null) {
                MSL multiSelectListener = getParentAdapter().getMultiSelectStatusListener();
                multiSelectListener.onCategoryLongClick((CategoryItem) getViewHolder().getItem());
            }
        }

        private void onLongClickNonCategory() {
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
                    onLongClickCategory();
                } else {
                    onLongClickNonCategory();
                }
                return true;
            }
            return false;
        }
    }
}
