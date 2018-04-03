package delit.piwigoclient.ui.album.view;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.RecyclerView;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;

import org.greenrobot.eventbus.EventBus;

import java.util.Date;
import java.util.HashSet;

import delit.piwigoclient.R;
import delit.piwigoclient.business.PicassoLoader;
import delit.piwigoclient.business.ResizingPicassoLoader;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PiwigoAlbum;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.ui.common.SquareLinearLayout;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.AlbumItemSelectedEvent;

import static android.view.View.GONE;

/**
 * {@link RecyclerView.Adapter} that can display a {@link GalleryItem}
 */
public class AlbumItemRecyclerViewAdapter extends RecyclerView.Adapter<AlbumItemRecyclerViewAdapter.ViewHolder> {

    private final PiwigoAlbum gallery;
    private Resources resources;
    private Date recentlyAlteredThresholdDate;
    private String preferredThumbnailSize;
    private boolean allowItemSelection;
    private HashSet<Long> selectedResourceIds = new HashSet<>();
    private MultiSelectStatusListener multiSelectStatusListener;
    private boolean captureActionClicks;
    private boolean useDarkMode;
    private boolean showAlbumThumbnailsZoomed;
    private boolean showLargeAlbumThumbnails;
    private float albumWidth;
    public static final int SCALING_QUALITY_PERFECT = Integer.MAX_VALUE;
    public static final int SCALING_QUALITY_VHIGH = 960;
    public static final int SCALING_QUALITY_HIGH = 480;
    public static final int SCALING_QUALITY_MEDIUM = 240;
    public static final int SCALING_QUALITY_LOW = 120;
    public static final int SCALING_QUALITY_VLOW = 60;
    private int scalingQuality = SCALING_QUALITY_MEDIUM;
    private boolean showResourceNames;
    private boolean useMasonryStyle;

    public AlbumItemRecyclerViewAdapter(final PiwigoAlbum gallery, Date recentlyAlteredThresholdDate, MultiSelectStatusListener multiSelectStatusListener, boolean captureActionClicks) {
        this.gallery = gallery;
        this.recentlyAlteredThresholdDate = recentlyAlteredThresholdDate;
        this.setHasStableIds(true);
        this.multiSelectStatusListener = multiSelectStatusListener;
        this.captureActionClicks = captureActionClicks;
    }

    public void setUseDarkMode(boolean useDarkMode) {
        this.useDarkMode = useDarkMode;
    }

    public void setShowAlbumThumbnailsZoomed(boolean showAlbumThumbnailsZoomed) {
        this.showAlbumThumbnailsZoomed = showAlbumThumbnailsZoomed;
    }

    public void setMasonryStyle(boolean useMasonryStyle) {
        this.useMasonryStyle = useMasonryStyle;
    }

    public void setCaptureActionClicks(boolean captureActionClicks) {

        this.captureActionClicks = captureActionClicks;
        if(!captureActionClicks) {
            if (allowItemSelection) {
                toggleItemSelection();
            }
        }
    }

    public int getItemPosition(GalleryItem item) {
        return gallery.getItems().indexOf(item);
    }

    @Override
    public long getItemId(int position) {
        return gallery.getItems().get(position).getId();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        if(useMasonryStyle) {
            if (viewType == GalleryItem.CATEGORY_TYPE) {
                view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.fragment_galleryitem_album_masonry, parent, false);
            } else {
                view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.fragment_galleryitem_resource_masonry, parent, false);
            }
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                view.setClipToOutline(true);
            }
        } else if (viewType == GalleryItem.CATEGORY_TYPE) {
            if(showLargeAlbumThumbnails) {
                view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.fragment_galleryitem_album_grid, parent, false);
            } else {
                view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.fragment_galleryitem_album_list, parent, false);
            }
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                view.setClipToOutline(true);
            }

        } else if (viewType == GalleryItem.PICTURE_RESOURCE_TYPE || viewType == GalleryItem.VIDEO_RESOURCE_TYPE) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.fragment_galleryitem_resource, parent, false);
        } else {// if (viewType == GalleryItem.CATEGORY_ADVERT_TYPE || viewType == GalleryItem.RESOURCE_ADVERT_TYPE) {
            // Is an advert
            AdView adView = new AdView(parent.getContext());
            adView.setAdSize(AdSize.BANNER);
            adView.setAdUnitId(parent.getContext().getString(R.string.ad_id_album_banner));
            view = adView;
        }

        resources = parent.getResources();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(parent.getContext().getApplicationContext());
        preferredThumbnailSize = prefs.getString(parent.getContext().getString(R.string.preference_gallery_item_thumbnail_size_key), parent.getContext().getString(R.string.preference_gallery_item_thumbnail_size_default));

        final ViewHolder viewHolder = new ViewHolder(view);

        if(viewType == GalleryItem.PICTURE_RESOURCE_TYPE || viewType == GalleryItem.VIDEO_RESOURCE_TYPE) {
            // Albums are not checkable.
            viewHolder.checkBox.setOnCheckedChangeListener(new ItemSelectionListener(viewHolder));
        }
        if(viewType == GalleryItem.VIDEO_RESOURCE_TYPE || viewType  == GalleryItem.PICTURE_RESOURCE_TYPE || viewType == GalleryItem.CATEGORY_TYPE) {
            final ViewTreeObserver.OnPreDrawListener predrawListener;
            if(!useMasonryStyle) {
                viewHolder.imageLoader = new ResizingPicassoLoader(viewHolder.mImageView, 0, 0);
                predrawListener = new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        try {
                            if (!viewHolder.imageLoader.isImageLoaded() && !viewHolder.imageLoader.isImageLoading()) {

                                int imgSize = scalingQuality;
                                if (imgSize == Integer.MAX_VALUE) {
                                    imgSize = viewHolder.mImageView.getMeasuredWidth();
                                } else {
                                    // need that math.max to ensure that the image size remains positive
                                    //FIXME How can this ever be called before the ImageView object has a size?
                                    imgSize = Math.max(SCALING_QUALITY_VLOW,Math.min(scalingQuality, viewHolder.mImageView.getMeasuredWidth()));
                                }
                                ((ResizingPicassoLoader) viewHolder.imageLoader).setResizeTo(imgSize, imgSize);
                                viewHolder.imageLoader.load();
                            }
                        } catch (IllegalStateException e) {
                            // image loader not configured yet...
                        }
                        return true;
                    }
                };
            } else {
                viewHolder.imageLoader = new PicassoLoader(viewHolder.mImageView);
                predrawListener = new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        try {
                            if (!viewHolder.imageLoader.isImageLoaded() && !viewHolder.imageLoader.isImageLoading()) {
                                viewHolder.imageLoader.load();
                            }
                        } catch(IllegalStateException e) {
                            // image loader not configured yet...
                        }
                        return true;
                    }
                };
            }
            viewHolder.mImageView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    viewHolder.mImageView.getViewTreeObserver().addOnPreDrawListener(predrawListener);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    viewHolder.mImageView.getViewTreeObserver().removeOnPreDrawListener(predrawListener);
                }
            });
            viewHolder.itemActionListener = new CustomClickListener(viewHolder);
            viewHolder.mImageView.setOnClickListener(viewHolder.itemActionListener);
            viewHolder.mImageView.setOnLongClickListener(viewHolder.itemActionListener);
        }

        if(viewType == GalleryItem.CATEGORY_TYPE && !showLargeAlbumThumbnails) {
            viewHolder.itemView.setOnClickListener(viewHolder.itemActionListener);
            viewHolder.itemView.setOnLongClickListener(viewHolder.itemActionListener);
        }

        setItemBackground(viewType, viewHolder);

        return viewHolder;
    }

    private void setItemBackground(int viewType, ViewHolder viewHolder) {
        if(viewType == GalleryItem.CATEGORY_TYPE) {
            if (useDarkMode) {
                if (useMasonryStyle) {
                    // needed for the background behind the title text
                    viewHolder.itemView.setBackgroundResource(R.drawable.curved_corners_layout_bg_dark);
                    // needed for images that don't load correctly.
                    viewHolder.mImageView.setBackgroundColor(Color.WHITE);
                } else {
                    viewHolder.itemView.setBackgroundResource(R.drawable.curved_corners_layout_bg_white);
                }
                if (viewHolder.mImageContainer != null) {
                    // will be null for categories in list view.
                    viewHolder.mImageContainer.setBackgroundResource(R.drawable.curved_corners_layout_bg_dark);
                }
            }
        } else {
            if(useDarkMode) {
                if(useMasonryStyle) {
                    // needed for the background behind the title text
                    viewHolder.itemView.setBackgroundResource(R.color.black_overlay);
                    // needed for images that don't load correctly.
                    viewHolder.mImageView.setBackgroundColor(Color.WHITE);
                } else {
                    viewHolder.itemView.setBackgroundColor(Color.WHITE);
                }
                if(viewHolder.mImageContainer != null) {
                    // will be null for categories in list view.
//                viewHolder.mImageContainer.setBackgroundColor(resources.getColor(R.color.black_overlay_dark));
                    viewHolder.mImageContainer.setBackgroundResource(R.color.black_overlay);
                }
            }
        }
    }

    @Override
    public void onViewRecycled(ViewHolder holder) {
        UIHelper.recycleImageViewContent(holder.mImageView);
        UIHelper.recycleImageViewContent(holder.mRecentlyAlteredMarkerView);
        UIHelper.recycleImageViewContent(holder.mTypeIndicatorImg);
    }



    @Override
    public int getItemViewType(int position) {
        return gallery.getItems().get(position).getType();
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        GalleryItem newItem = gallery.getItems().get(position);
        if (holder.getOldPosition() < 0 && holder.mItem != null && holder.mItem.getId() == newItem.getId()) {
            // rendering the same item.
            updateCheckableStatus(holder);
            return;
        }

        // store the item in this recyclable holder.
        holder.mItem = newItem;

        if(holder.mItem.getType() == GalleryItem.CATEGORY_ADVERT_TYPE || holder.mItem.getType() == GalleryItem.RESOURCE_ADVERT_TYPE) {
            // no need to configure this view.
            return;
        }

        if(CategoryItem.BLANK.equals(holder.mItem)) {
            holder.itemView.setVisibility(View.INVISIBLE);
            return;
        }

        if(holder.itemActionListener != null) {
            holder.itemActionListener.resetStatus();
        }

        holder.itemView.setVisibility(View.VISIBLE);

        updateCheckableStatus(holder);

        if(holder.mRecentlyAlteredMarkerView != null) {
            if (holder.mItem.getLastAltered() != null && holder.mItem.getLastAltered().compareTo(recentlyAlteredThresholdDate) > 0) {
                // is null for blank categories (dummmy spacers) and also for categories only visible because this is an admin user (without explicit access)
                PicassoLoader picasso = new PicassoLoader(holder.mRecentlyAlteredMarkerView);
                picasso.setResourceToLoad(R.drawable.ic_star_yellow_24dp);
                picasso.load();
                holder.mRecentlyAlteredMarkerView.setVisibility(View.VISIBLE);
            } else {
                holder.mRecentlyAlteredMarkerView.setVisibility(GONE);
            }
        }




        if (holder.mItem.getType() == GalleryItem.CATEGORY_TYPE) {
            CategoryItem category = (CategoryItem) holder.mItem;

            if (category.getPhotoCount() > 0) {
                holder.mPhotoCountView.setText(resources.getString(R.string.gallery_photos_summary_text_pattern, category.getPhotoCount()));
                holder.mPhotoCountView.setVisibility(View.VISIBLE);
            } else {
                holder.mPhotoCountView.setVisibility(View.GONE);
            }
            if (category.getSubCategories() > 0) {
                holder.mSubCategoriesView.setVisibility(View.VISIBLE);
                long subAlbumPhotos = category.getTotalPhotos() - category.getPhotoCount();
                holder.mSubCategoriesView.setText(resources.getString(R.string.gallery_subcategory_summary_text_pattern, category.getSubCategories(), subAlbumPhotos));
            } else {
                holder.mSubCategoriesView.setVisibility(View.GONE);
            }
        }


        if (!(holder.mItem.getName() == null || holder.mItem.getName().isEmpty())) {
            if (showResourceNames || holder.mItem.getType() == GalleryItem.CATEGORY_TYPE) {
                holder.mNameView.setVisibility(View.VISIBLE);
                holder.mNameView.setText(holder.mItem.getName());
            } else {
                holder.mNameView.setVisibility(GONE);
            }

            if (holder.mItem.getType() == GalleryItem.CATEGORY_TYPE) {
                if (showLargeAlbumThumbnails) {
                    if (albumWidth > 3) {
                        holder.mNameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
                    } else if (albumWidth > 2.4) {
                        holder.mNameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
                    } else if (albumWidth > 1.8) {
                        holder.mNameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                    } else {
                        holder.mNameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    }
                } else {
                    holder.mNameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                }
            }

        } else {
            holder.mNameView.setVisibility(GONE);
        }

        if (holder.mItem.getThumbnailUrl() != null) {

            if (holder.mItem instanceof CategoryItem) {
                holder.imageLoader.setUriToLoad(holder.mItem.getThumbnailUrl());
            } else {
                ResourceItem resItem = (ResourceItem) holder.mItem;
                ResourceItem.ResourceFile rf = resItem.getFile(preferredThumbnailSize);
                if (rf != null) {
                    holder.imageLoader.setUriToLoad(rf.getUrl());
                } else {
                    // this is really bizarre - but show something for now.
                    holder.imageLoader.setUriToLoad(holder.mItem.getThumbnailUrl());
                }
            }
            if(!useMasonryStyle) {
                if (showAlbumThumbnailsZoomed) {
                    ((ResizingPicassoLoader) holder.imageLoader).setCenterCrop(true);
                } else {
                    ((ResizingPicassoLoader) holder.imageLoader).setCenterCrop(false);
                }
            }
        } else if(holder.mItem instanceof CategoryItem && ((CategoryItem)holder.mItem).getRepresentativePictureId() != null) {
            holder.imageLoader.setResourceToLoad(R.drawable.ic_photo_library_black_24px);
            if(!useMasonryStyle) {
                ((ResizingPicassoLoader) holder.imageLoader).setCenterCrop(false);
            }
            //Now trigger a load of the real data.
            multiSelectStatusListener.notifyAlbumThumbnailInfoLoadNeeded((CategoryItem)holder.mItem);
        } else {
            holder.imageLoader.setResourceToLoad(R.drawable.ic_photo_library_black_24px);
            if(!useMasonryStyle) {
                ((ResizingPicassoLoader) holder.imageLoader).setCenterCrop(false);
            }
        }

        if (holder.mItem.getType() == GalleryItem.PICTURE_RESOURCE_TYPE) {
            AppCompatImageView imgView = holder.mTypeIndicatorImg;
            imgView.setVisibility(GONE);
        }
        if (holder.mItem.getType() == GalleryItem.VIDEO_RESOURCE_TYPE) {
            AppCompatImageView imgView = holder.mTypeIndicatorImg;
            PicassoLoader picasso = new PicassoLoader(imgView);
            picasso.setResourceToLoad(R.drawable.ic_movie_filter_black_24px);
            picasso.load();
            imgView.setVisibility(View.VISIBLE);
        }
    }

    private void updateCheckableStatus(ViewHolder holder) {
        if(holder.mItem.getType() == GalleryItem.PICTURE_RESOURCE_TYPE || holder.mItem.getType() == GalleryItem.VIDEO_RESOURCE_TYPE) {
            holder.checkBox.setVisibility(allowItemSelection ? View.VISIBLE : GONE);
            holder.checkBox.setChecked(selectedResourceIds.contains(holder.mItem.getId()));
        } else if(holder.checkBox != null) {
            // only in masonry view mode at the moment.
            holder.checkBox.setVisibility(GONE);
        }
    }

    @Override
    public int getItemCount() {
        return gallery.getItems().size();
    }

    public void toggleItemSelection() {
        this.allowItemSelection = !allowItemSelection;
        if(!allowItemSelection) {
            selectedResourceIds.clear();
        }
//        notifyItemRangeChanged(0, getItemCount());
        notifyDataSetChanged();
        multiSelectStatusListener.onMultiSelectStatusChanged(allowItemSelection);
        multiSelectStatusListener.onItemSelectionCountChanged(selectedResourceIds.size());
    }

    public boolean isItemSelectionAllowed() {
        return allowItemSelection;
    }

    public HashSet<Long> getSelectedItemIds() {
        return selectedResourceIds;
    }

    public HashSet<ResourceItem> getSelectedItems() {
        HashSet<ResourceItem> selectedItems = new HashSet<>();
        for(Long selectedItemId : selectedResourceIds) {
            selectedItems.add(gallery.getResourceItemById(selectedItemId));
        }
        return selectedItems;
    }

    public void clearSelectedItemIds() {
        selectedResourceIds.clear();
        notifyItemRangeChanged(0, getItemCount());
    }

    public void selectAllItemIds() {

        notifyItemRangeChanged(0, getItemCount());
    }

    public void setShowResourceNames(boolean showResourceNames) {
        this.showResourceNames = showResourceNames;
    }

    public void setShowLargeAlbumThumbnails(boolean showLargeAlbumThumbnails) {
        this.showLargeAlbumThumbnails = showLargeAlbumThumbnails;
    }

    public void setAlbumWidth(float albumWidth) {
        this.albumWidth = albumWidth;
    }

    public void redrawItem(ViewHolder vh, CategoryItem item) {
        // clone the item into the view holder item (will not be same object if serialization has occured)
        vh.mItem.copyFrom(item, true);
        // find item index.

        int idx = getItemPosition(vh.mItem);
        notifyItemChanged(idx);
        // clear the item in the view holder (to ensure it is redrawn - will be reloaded from the galleryList).
        vh.mItem = null;
        onBindViewHolder(vh, gallery.getItems().indexOf(vh.mItem));
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final AppCompatCheckBox checkBox;
        public final AppCompatImageView mImageView;
        public final TextView mNameView;
        public final TextView mPhotoCountView;
        public final TextView mSubCategoriesView;
//        public final TextView mDescriptionView;
        public final ImageView mRecentlyAlteredMarkerView;
        private final SquareLinearLayout mImageContainer;
        public final AppCompatImageView mTypeIndicatorImg;
        private PicassoLoader imageLoader;
        public GalleryItem mItem;
        public CustomClickListener itemActionListener;

        public ViewHolder(View view) {
            super(view);
            checkBox = view.findViewById(R.id.checked);
            mNameView = view.findViewById(R.id.resource_name);
            mPhotoCountView = view.findViewById(R.id.album_photoCount);
            mSubCategoriesView = view.findViewById(R.id.album_subCategories);
            mImageView = view.findViewById(R.id.resource_thumbnail);
            mRecentlyAlteredMarkerView = view.findViewById(R.id.newly_altered_marker_image);
            mImageContainer = view.findViewById(R.id.thumbnail_container);
            mTypeIndicatorImg = view.findViewById(R.id.type_indicator);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mNameView.getText() + "'";
        }
    }

    public interface MultiSelectStatusListener {
        void onMultiSelectStatusChanged(boolean multiSelectEnabled);

        void onItemSelectionCountChanged(int size);

        void onCategoryLongClick(CategoryItem album);

        void notifyAlbumThumbnailInfoLoadNeeded(CategoryItem mItem);
    }

    private class ItemSelectionListener implements CompoundButton.OnCheckedChangeListener {

        private ViewHolder holder;

        public ItemSelectionListener(ViewHolder holder) {
            this.holder = holder;
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            boolean changed = false;
            if(isChecked) {
                changed = selectedResourceIds.add(holder.getItemId());
            } else {
                changed = selectedResourceIds.remove(holder.getItemId());
            }
            if(changed) {
                multiSelectStatusListener.onItemSelectionCountChanged(selectedResourceIds.size());
            }
        }
    }

    private class CustomClickListener implements View.OnClickListener, View.OnLongClickListener {

        private final ViewHolder viewHolder;
        private int manualRetries = 0;
        private int maxManualRetries = 2;

        public CustomClickListener(ViewHolder viewHolder) {
            this.viewHolder = viewHolder;
        }

    private void onCategoryClick() {
        if (!allowItemSelection) {
            //If not currently in multiselect mode
            AlbumItemSelectedEvent event = new AlbumItemSelectedEvent(gallery, viewHolder.mItem);
            EventBus.getDefault().post(event);
        }
    }

    private void onNonCategoryClick() {
        if (!allowItemSelection) {
            //If not currently in multiselect mode
            AlbumItemSelectedEvent event = new AlbumItemSelectedEvent(gallery, viewHolder.mItem);
            EventBus.getDefault().post(event);
        } else if (captureActionClicks) {
            // Are allowing access to admin functions within the album

            // multi selection mode is enabled.
            if (selectedResourceIds.contains(viewHolder.getItemId())) {
                viewHolder.checkBox.setChecked(false);
            } else {
                viewHolder.checkBox.setChecked(true);
            }
            //TODO Not sure why we'd call this?
            viewHolder.itemView.setPressed(false);
        }
    }

    @Override
    public void onClick(View v) {
        if(v == viewHolder.mImageView && !viewHolder.imageLoader.isImageLoaded() && viewHolder.imageLoader.isImageUnavailable() && manualRetries < maxManualRetries) {
            manualRetries++;
            viewHolder.imageLoader.load();
        } else {
            if(viewHolder.mItem.getType() == GalleryItem.CATEGORY_TYPE) {
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
            if(captureActionClicks) {
                multiSelectStatusListener.onCategoryLongClick((CategoryItem)viewHolder.mItem);
            }
        }

        private void onNonCategoryLongClick() {
            if(captureActionClicks) {
                toggleItemSelection();
                if (allowItemSelection) {
                    viewHolder.checkBox.setChecked(true);
                }
            }
        }

        @Override
        public boolean onLongClick(View v) {
            if(viewHolder.mItem.getType() == GalleryItem.CATEGORY_TYPE) {
                onCategoryLongClick();
            } else {
                onNonCategoryLongClick();
            }
            return true;
        }
    }
}
