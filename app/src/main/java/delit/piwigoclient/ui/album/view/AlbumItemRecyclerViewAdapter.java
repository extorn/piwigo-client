package delit.piwigoclient.ui.album.view;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.RecyclerView;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;

import org.greenrobot.eventbus.EventBus;

import delit.piwigoclient.R;
import delit.piwigoclient.business.PicassoLoader;
import delit.piwigoclient.business.ResizingPicassoLoader;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.Identifiable;
import delit.piwigoclient.model.piwigo.ResourceContainer;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.ui.common.SquareLinearLayout;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapter;
import delit.piwigoclient.ui.common.recyclerview.CustomClickListener;
import delit.piwigoclient.ui.common.recyclerview.CustomViewHolder;
import delit.piwigoclient.ui.common.recyclerview.IdentifiableListViewAdapter;
import delit.piwigoclient.ui.events.AlbumItemSelectedEvent;

import static android.view.View.GONE;
import static delit.piwigoclient.ui.album.view.AlbumItemRecyclerViewAdapterPreferences.SCALING_QUALITY_VLOW;

/**
 * {@link RecyclerView.Adapter} that can display a {@link GalleryItem}
 * FIXME This is broken. swap for a new class based upon IdentifiableListViewAdapter
 */
public class AlbumItemRecyclerViewAdapter<T extends Identifiable> extends IdentifiableListViewAdapter<AlbumItemRecyclerViewAdapterPreferences, GalleryItem, ResourceContainer<T, GalleryItem>, AlbumItemRecyclerViewAdapter.AlbumItemViewHolder<T>> {



    public AlbumItemRecyclerViewAdapter(final Context context, final ResourceContainer<T, GalleryItem> gallery, MultiSelectStatusListener multiSelectStatusListener, AlbumItemRecyclerViewAdapterPreferences prefs) {
        super(gallery, multiSelectStatusListener, prefs);
    }

    @NonNull
    protected View inflateView(@NonNull ViewGroup parent, int viewType) {
        View view;
        if(getAdapterPrefs().isUseMasonryStyle()) {
            view = inflateMasonryView(parent, viewType);
        } else if (viewType == GalleryItem.CATEGORY_TYPE) {
            view = inflateNonMasonryAlbumView(parent);
        } else if (viewType == GalleryItem.PICTURE_RESOURCE_TYPE || viewType == GalleryItem.VIDEO_RESOURCE_TYPE) {
            view = inflateNonMasonryResourceItemView(parent);
        } else {
            view = inflateAdvertView(parent, viewType);
        }
        return view;
    }

    private View inflateAdvertView(ViewGroup parent, int viewType) {
        // if (viewType == GalleryItem.CATEGORY_ADVERT_TYPE || viewType == GalleryItem.RESOURCE_ADVERT_TYPE) {
        AdView adView = new AdView(getContext());
        adView.setAdSize(AdSize.SMART_BANNER);
        adView.setAdUnitId(getContext().getString(R.string.ad_id_album_banner));
        return adView;
    }

    private View inflateNonMasonryResourceItemView(ViewGroup parent) {
        return LayoutInflater.from(getContext())
                .inflate(R.layout.fragment_galleryitem_resource, parent, false);
    }

    private View inflateNonMasonryAlbumView(ViewGroup parent) {
        View view;
        if(getAdapterPrefs().isShowLargeAlbumThumbnails()) {
            view = LayoutInflater.from(getContext())
                    .inflate(R.layout.fragment_galleryitem_album_grid, parent, false);
        } else {
            view = LayoutInflater.from(getContext())
                    .inflate(R.layout.fragment_galleryitem_album_list, parent, false);
        }
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setClipToOutline(true);
        }
        return view;
    }

    private View inflateMasonryView(ViewGroup parent, int viewType) {
        View view;
        if (viewType == GalleryItem.CATEGORY_TYPE) {
            view = LayoutInflater.from(getContext())
                    .inflate(R.layout.fragment_galleryitem_album_masonry, parent, false);
        } else {
            view = LayoutInflater.from(getContext())
                    .inflate(R.layout.fragment_galleryitem_resource_masonry, parent, false);
        }
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setClipToOutline(true);
        }
        return view;
    }

    @Override
    public AlbumItemViewHolder buildViewHolder(View view, int viewType) {

        final AlbumItemViewHolder viewHolder = new AlbumItemViewHolder(view, this, viewType);
        return viewHolder;
    }

    @Override
    public void onViewRecycled(@NonNull AlbumItemViewHolder holder) {
        UIHelper.recycleImageViewContent(holder.mImageView);
        UIHelper.recycleImageViewContent(holder.mRecentlyAlteredMarkerView);
        UIHelper.recycleImageViewContent(holder.mTypeIndicatorImg);
    }

    @Override
    public int getItemViewType(int position) {
        return getItemByPosition(position).getType();
    }

    @Override
    public void onBindViewHolder(@NonNull AlbumItemViewHolder holder, int position) {
        GalleryItem newItem = getItemByPosition(position);
        if(!isHolderOutOfSync(holder, newItem)) {
            // rendering the same item
            holder.updateCheckableStatus();
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

    public static class AlbumItemViewHolder<S extends Identifiable> extends CustomViewHolder<AlbumItemRecyclerViewAdapterPreferences, GalleryItem> {
        private final int viewType;
        public AppCompatCheckBox checkBox;
        public AppCompatImageView mImageView;
        public TextView mNameView;
        public TextView mPhotoCountView;
        public TextView mSubCategoriesView;
//        public final TextView mDescriptionView;
        public ImageView mRecentlyAlteredMarkerView;
        private SquareLinearLayout mImageContainer;
        public AppCompatImageView mTypeIndicatorImg;
        private PicassoLoader imageLoader;
        public AlbumItemCustomClickListener itemActionListener;
        private AlbumItemRecyclerViewAdapter<S> parentAdapter;

        public AlbumItemViewHolder(View view, AlbumItemRecyclerViewAdapter<S> parentAdapter, int viewType) {
            super(view);
            this.parentAdapter = parentAdapter;
            this.viewType = viewType;

        }

        private void updateCheckableStatus() {
            if(getItem().getType() == GalleryItem.PICTURE_RESOURCE_TYPE || getItem().getType() == GalleryItem.VIDEO_RESOURCE_TYPE) {
                checkBox.setVisibility(parentAdapter.getAdapterPrefs().isAllowItemSelection() ? View.VISIBLE : GONE);
                checkBox.setChecked(parentAdapter.isItemSelected(getItem().getId()));
            } else if(checkBox != null) {
                // only in masonry view mode at the moment.
                checkBox.setVisibility(GONE);
            }
        }

        @Override
        public void fillValues(Context context, GalleryItem newItem, boolean allowItemDeletion) {

            setItem(newItem);

            if(newItem.getType() == GalleryItem.CATEGORY_ADVERT_TYPE || newItem.getType() == GalleryItem.RESOURCE_ADVERT_TYPE) {
                // no need to configure this view.
                return;
            }

            if(CategoryItem.BLANK.equals(newItem)) {
                itemView.setVisibility(View.INVISIBLE);
                return;
            }

            if(itemActionListener != null) {
                itemActionListener.resetStatus();
            }

            itemView.setVisibility(View.VISIBLE);

            updateCheckableStatus();

            if(mRecentlyAlteredMarkerView != null) {
                if (newItem.getLastAltered() != null && newItem.getLastAltered().compareTo(parentAdapter.getAdapterPrefs().getRecentlyAlteredThresholdDate()) > 0) {
                    // is null for blank categories (dummmy spacers) and also for categories only visible because this is an admin user (without explicit access)
                    PicassoLoader picasso = new PicassoLoader(mRecentlyAlteredMarkerView);
                    picasso.setResourceToLoad(R.drawable.ic_star_yellow_24dp);
                    picasso.load();
                    mRecentlyAlteredMarkerView.setVisibility(View.VISIBLE);
                } else {
                    mRecentlyAlteredMarkerView.setVisibility(GONE);
                }
            }


            if (newItem.getType() == GalleryItem.CATEGORY_TYPE) {
                CategoryItem category = (CategoryItem) newItem;

                if (category.getPhotoCount() > 0) {
                    mPhotoCountView.setText(itemView.getResources().getString(R.string.gallery_photos_summary_text_pattern, category.getPhotoCount()));
                    mPhotoCountView.setVisibility(View.VISIBLE);
                } else {
                    mPhotoCountView.setVisibility(View.GONE);
                }
                if (category.getSubCategories() > 0) {
                    mSubCategoriesView.setVisibility(View.VISIBLE);
                    long subAlbumPhotos = category.getTotalPhotos() - category.getPhotoCount();
                    mSubCategoriesView.setText(itemView.getResources().getString(R.string.gallery_subcategory_summary_text_pattern, category.getSubCategories(), subAlbumPhotos));
                } else {
                    mSubCategoriesView.setVisibility(View.GONE);
                }
            }


            if (!(newItem.getName() == null || newItem.getName().isEmpty())) {
                if (parentAdapter.getAdapterPrefs().isShowResourceNames() || newItem.getType() == GalleryItem.CATEGORY_TYPE) {
                    mNameView.setVisibility(View.VISIBLE);
                    mNameView.setText(newItem.getName());
                } else {
                    mNameView.setVisibility(GONE);
                }

                if (newItem.getType() == GalleryItem.CATEGORY_TYPE) {
                    if (parentAdapter.getAdapterPrefs().isShowLargeAlbumThumbnails()) {
                        float albumWidth = parentAdapter.getAdapterPrefs().getAlbumWidth();
                        if (albumWidth > 3) {
                            mNameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
                        } else if (albumWidth > 2.4) {
                            mNameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
                        } else if (albumWidth > 1.8) {
                            mNameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                        } else {
                            mNameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                        }
                    } else {
                        mNameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    }
                }

            } else {
                mNameView.setVisibility(GONE);
            }

            if (newItem.getThumbnailUrl() != null) {

                if (newItem instanceof CategoryItem) {
                    imageLoader.setUriToLoad(newItem.getThumbnailUrl());
                } else {
                    ResourceItem resItem = (ResourceItem) newItem;
                    ResourceItem.ResourceFile rf = resItem.getFile(parentAdapter.getAdapterPrefs().getPreferredThumbnailSize());
                    if (rf != null) {
                        imageLoader.setUriToLoad(rf.getUrl());
                    } else {
                        // this is really bizarre - but show something for now.
                        imageLoader.setUriToLoad(newItem.getThumbnailUrl());
                    }
                }
                if(!parentAdapter.getAdapterPrefs().isUseMasonryStyle()) {
                    if (parentAdapter.getAdapterPrefs().isShowAlbumThumbnailsZoomed()) {
                        ((ResizingPicassoLoader) imageLoader).setCenterCrop(true);
                    } else {
                        ((ResizingPicassoLoader) imageLoader).setCenterCrop(false);
                    }
                }
            } else if(newItem instanceof CategoryItem && ((CategoryItem)newItem).getRepresentativePictureId() != null) {
                imageLoader.setResourceToLoad(R.drawable.ic_photo_library_black_24px);
                if(!parentAdapter.getAdapterPrefs().isUseMasonryStyle()) {
                    ((ResizingPicassoLoader) imageLoader).setCenterCrop(false);
                }
                if(parentAdapter.getMultiSelectStatusListener() != null) {
                    //Now trigger a load of the real data.
                    MultiSelectStatusAdapter listener = parentAdapter.getMultiSelectStatusListener();
                    listener.notifyAlbumThumbnailInfoLoadNeeded((CategoryItem) newItem);
                }
            } else {
                imageLoader.setResourceToLoad(R.drawable.ic_photo_library_black_24px);
                if(!parentAdapter.getAdapterPrefs().isUseMasonryStyle()) {
                    ((ResizingPicassoLoader) imageLoader).setCenterCrop(false);
                }
            }

            if (newItem.getType() == GalleryItem.PICTURE_RESOURCE_TYPE) {
                AppCompatImageView imgView = mTypeIndicatorImg;
                imgView.setVisibility(GONE);
            }
            if (newItem.getType() == GalleryItem.VIDEO_RESOURCE_TYPE) {
                AppCompatImageView imgView = mTypeIndicatorImg;
                PicassoLoader picasso = new PicassoLoader(imgView);
                picasso.setResourceToLoad(R.drawable.ic_movie_filter_black_24px);
                picasso.load();
                imgView.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public void cacheViewFieldsAndConfigure() {
            checkBox = itemView.findViewById(R.id.checked);
            mNameView = itemView.findViewById(R.id.resource_name);
            mPhotoCountView = itemView.findViewById(R.id.album_photoCount);
            mSubCategoriesView = itemView.findViewById(R.id.album_subCategories);
            mImageView = itemView.findViewById(R.id.resource_thumbnail);
            mRecentlyAlteredMarkerView = itemView.findViewById(R.id.newly_altered_marker_image);
            mImageContainer = itemView.findViewById(R.id.thumbnail_container);
            mTypeIndicatorImg = itemView.findViewById(R.id.type_indicator);

            if(viewType == GalleryItem.PICTURE_RESOURCE_TYPE || viewType == GalleryItem.VIDEO_RESOURCE_TYPE) {
                // Albums are not checkable.
                checkBox.setOnCheckedChangeListener(parentAdapter.new ItemSelectionListener<AlbumItemRecyclerViewAdapter<S>>(parentAdapter, this));
            }
            if(viewType == GalleryItem.VIDEO_RESOURCE_TYPE || viewType  == GalleryItem.PICTURE_RESOURCE_TYPE || viewType == GalleryItem.CATEGORY_TYPE) {
                final ViewTreeObserver.OnPreDrawListener predrawListener;
                if(!parentAdapter.getAdapterPrefs().isUseMasonryStyle()) {
                    imageLoader = new ResizingPicassoLoader(mImageView, 0, 0);
                    predrawListener = new ViewTreeObserver.OnPreDrawListener() {
                        @Override
                        public boolean onPreDraw() {
                            try {
                                if (!imageLoader.isImageLoaded() && !imageLoader.isImageLoading() && !imageLoader.isImageUnavailable()) {

                                    int desiredScalingQuality = parentAdapter.getAdapterPrefs().getScalingQuality();
                                    int imgSize = desiredScalingQuality;
                                    if (imgSize == Integer.MAX_VALUE) {
                                        imgSize = mImageView.getMeasuredWidth();
                                    } else {
                                        // need that math.max to ensure that the image size remains positive
                                        //FIXME How can this ever be called before the ImageView object has a size?
                                        imgSize = Math.max(SCALING_QUALITY_VLOW,Math.min(desiredScalingQuality, mImageView.getMeasuredWidth()));
                                    }
                                    ((ResizingPicassoLoader) imageLoader).setResizeTo(imgSize, imgSize);
                                    imageLoader.load();
                                }
                            } catch (IllegalStateException e) {
                                // image loader not configured yet...
                            }
                            return true;
                        }
                    };
                } else {
                    imageLoader = new PicassoLoader(mImageView);
                    predrawListener = new ViewTreeObserver.OnPreDrawListener() {
                        @Override
                        public boolean onPreDraw() {
                            try {
                                if (!imageLoader.isImageLoaded() && !imageLoader.isImageLoading()) {
                                    imageLoader.load();
                                }
                            } catch(IllegalStateException e) {
                                // image loader not configured yet...
                            }
                            return true;
                        }
                    };
                }
                mImageView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {
                        mImageView.getViewTreeObserver().addOnPreDrawListener(predrawListener);
                    }

                    @Override
                    public void onViewDetachedFromWindow(View v) {
                        mImageView.getViewTreeObserver().removeOnPreDrawListener(predrawListener);
                    }
                });
                itemActionListener = new AlbumItemCustomClickListener(this, this.parentAdapter);
                mImageView.setOnClickListener(itemActionListener);
                mImageView.setOnLongClickListener(itemActionListener);
            }

            if(viewType == GalleryItem.CATEGORY_TYPE && !parentAdapter.getAdapterPrefs().isShowLargeAlbumThumbnails()) {
                itemView.setOnClickListener(itemActionListener);
                itemView.setOnLongClickListener(itemActionListener);
            }

            setItemBackground(viewType, this);

        }

        private void setItemBackground(int viewType, AlbumItemViewHolder viewHolder) {
            if(viewType == GalleryItem.CATEGORY_TYPE) {
                if (parentAdapter.getAdapterPrefs().isUseDarkMode()) {
                    if (parentAdapter.getAdapterPrefs().isUseMasonryStyle()) {
                        // needed for the background behind the title text
                        itemView.setBackgroundResource(R.drawable.curved_corners_layout_bg_dark);
                        // needed for images that don't load correctly.
                        mImageView.setBackgroundColor(Color.WHITE);
                    } else {
                        itemView.setBackgroundResource(R.drawable.curved_corners_layout_bg_white);
                    }
                    if (mImageContainer != null) {
                        // will be null for categories in list view.
                        mImageContainer.setBackgroundResource(R.drawable.curved_corners_layout_bg_dark);
                    }
                }
            } else {
                if (parentAdapter.getAdapterPrefs().isUseDarkMode()) {
                    if (parentAdapter.getAdapterPrefs().isUseMasonryStyle()) {
                        // needed for the background behind the title text
                        itemView.setBackgroundResource(R.color.black_overlay);
                        // needed for images that don't load correctly.
                        mImageView.setBackgroundColor(Color.WHITE);
                    } else {
                        itemView.setBackgroundColor(Color.WHITE);
                    }
                    if(mImageContainer != null) {
                        // will be null for categories in list view.
//                mImageContainer.setBackgroundColor(resources.getColor(R.color.black_overlay_dark));
                        mImageContainer.setBackgroundResource(R.color.black_overlay);
                    }
                }
            }
        }

        @Override
        public void setChecked(boolean checked) {
            checkBox.setChecked(checked);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mNameView.getText() + "'";
        }
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
            if(item instanceof CategoryItem) {
                onCategoryLongClick((CategoryItem)item);
            }
        }
    }

    private static class AlbumItemCustomClickListener<T extends Identifiable> extends CustomClickListener<AlbumItemRecyclerViewAdapterPreferences, GalleryItem, AlbumItemViewHolder<T>> {

        private int manualRetries = 0;
        private final int maxManualRetries = 2;

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
            if(v == getViewHolder().mImageView && !getViewHolder().imageLoader.isImageLoaded() && getViewHolder().imageLoader.isImageUnavailable() && manualRetries < maxManualRetries) {
                manualRetries++;
                getViewHolder().imageLoader.load();
            } else {
                if(getViewHolder().getItem().getType() == GalleryItem.CATEGORY_TYPE) {
                    onCategoryClick();
                } else {
                    onNonCategoryClick();
                }
            }
        }

        public void resetStatus() {
            manualRetries = 0;
        }

        private void onCategoryLongClick() {
            if(getParentAdapter().getAdapterPrefs().isMultiSelectionEnabled() && getParentAdapter().getMultiSelectStatusListener() != null) {
                MultiSelectStatusAdapter multiSelectListener = getParentAdapter().getMultiSelectStatusListener();
                multiSelectListener.onCategoryLongClick((CategoryItem)getViewHolder().getItem());
            }
        }

        private void onNonCategoryLongClick() {
            if(getParentAdapter().getAdapterPrefs().isMultiSelectionEnabled()) {
                getParentAdapter().toggleItemSelection();
                if (getParentAdapter().getAdapterPrefs().isAllowItemSelection()) {
                    getViewHolder().setChecked(true);
                }
            }
        }

        @Override
        public boolean onLongClick(View v) {
            if(getViewHolder().getItem().getType() == GalleryItem.CATEGORY_TYPE) {
                onCategoryLongClick();
            } else {
                onNonCategoryLongClick();
            }
            return true;
        }
    }
}
