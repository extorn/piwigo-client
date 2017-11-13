package delit.piwigoclient.ui.album.view;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
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
import delit.piwigoclient.business.ResizingPicassoLoader;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PiwigoAlbum;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.ui.common.SquareLinearLayout;
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

    public void setCaptureActionClicks(boolean captureActionClicks) {

        this.captureActionClicks = captureActionClicks;
        if(!captureActionClicks) {
            if (allowItemSelection) {
                toggleItemSelection();
            }
        }
    }

    @Override
    public long getItemId(int position) {
        return gallery.getItems().get(position).getId();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        if (viewType == GalleryItem.CATEGORY_TYPE) {
            if(showLargeAlbumThumbnails) {
                view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.fragment_galleryitem_category_grid, parent, false);
            } else {
                view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.fragment_galleryitem_category_list, parent, false);
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
            viewHolder.imageLoader = new ResizingPicassoLoader(viewHolder.mImageView, 0, 0);
            final ViewTreeObserver.OnPreDrawListener predrawListener = new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    try {
                        if (!viewHolder.imageLoader.isImageLoaded() && !viewHolder.imageLoader.isImageLoading()) {

                            int imgSize = scalingQuality;
                            if (imgSize == Integer.MAX_VALUE) {
                                imgSize = viewHolder.mImageView.getMeasuredWidth();
                            } else {
                                imgSize = Math.min(scalingQuality, viewHolder.mImageView.getMeasuredWidth());
                            }
                            viewHolder.imageLoader.setResizeTo(imgSize, imgSize);
                            viewHolder.imageLoader.load();
                        }
                    } catch(IllegalStateException e) {
                        // image loader not configured yet...
                    }
                    return true;
                }
            };
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

        if(useDarkMode) {
            viewHolder.itemView.setBackgroundColor(Color.WHITE);
            if(viewHolder.mImageContainer != null) {
                // will be null for categories in list view.
                viewHolder.mImageContainer.setBackgroundColor(resources.getColor(R.color.black_overlay_dark));
            }
        }

        return viewHolder;
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

        if(holder.mItem.getType() == GalleryItem.PICTURE_RESOURCE_TYPE || holder.mItem.getType() == GalleryItem.VIDEO_RESOURCE_TYPE) {
            holder.checkBox.setVisibility(allowItemSelection ? View.VISIBLE : GONE);
            holder.checkBox.setChecked(selectedResourceIds.contains(holder.mItem.getId()));
        } else if (holder.mItem.getType() == GalleryItem.CATEGORY_TYPE) {
            CategoryItem category = (CategoryItem) holder.mItem;

            if (category.getPhotoCount() > 0) {
                holder.mPhotoCountView.setText(String.format(resources.getString(R.string.gallery_photos_summary_text_pattern), category.getPhotoCount()));
                holder.mPhotoCountView.setVisibility(View.VISIBLE);
            } else {
                holder.mPhotoCountView.setVisibility(View.GONE);
            }
            if (category.getSubCategories() > 0) {
                holder.mSubCategoriesView.setVisibility(View.VISIBLE);
                long subAlbumPhotos = category.getTotalPhotos() - category.getPhotoCount();
                holder.mSubCategoriesView.setText(String.format(resources.getString(R.string.gallery_subcategory_summary_text_pattern), category.getSubCategories(), subAlbumPhotos));
            } else {
                holder.mSubCategoriesView.setVisibility(View.GONE);
            }
        }

        if(holder.mRecentlyAlteredMarkerView != null) {
            if (holder.mItem.getLastAltered() != null) {
                // is null for blank categories (dummmy spacers)
                holder.mRecentlyAlteredMarkerView.setVisibility(holder.mItem.getLastAltered().compareTo(recentlyAlteredThresholdDate) > 0 ? View.VISIBLE : GONE);
            } else {
                holder.mRecentlyAlteredMarkerView.setVisibility(GONE);
            }
        }

        if (!(holder.mItem.getName() == null || holder.mItem.getName().isEmpty())) {
            if(showResourceNames || holder.mItem.getType() == GalleryItem.CATEGORY_TYPE) {
                holder.mNameView.setVisibility(View.VISIBLE);
                holder.mNameView.setText(holder.mItem.getName());
            } else {
                holder.mNameView.setVisibility(GONE);
            }

            if(holder.mItem.getType() == GalleryItem.CATEGORY_TYPE) {
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

//        if (!(holder.mItem.getDescription() == null || holder.mItem.getDescription().isEmpty())) {
//            //TODO get the short description from the extended description plugin.
//            //            holder.mDescriptionView.setText(holder.mItem.description);
//            //            holder.mDescriptionView.setVisibility(View.VISIBLE);
//            holder.mDescriptionView.setVisibility(GONE);
//        } else {
//            holder.mDescriptionView.setVisibility(GONE);
//        }
        //holder.mImageView.setImageURI(Uri.parse(mValues.get(position).thumbnailUrl));
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
            if(showAlbumThumbnailsZoomed) {
                holder.imageLoader.setCenterCrop(true);
            } else {
                holder.imageLoader.setCenterCrop(false);
            }
        } else {
            holder.imageLoader.setResourceToLoad(R.drawable.ic_photo_library_black_24px);
            holder.imageLoader.setCenterCrop(false);
        }

        if (holder.mItem.getType() == GalleryItem.PICTURE_RESOURCE_TYPE) {
            ImageView imgView = (ImageView) holder.itemView.findViewById(R.id.movie_indicator);
            imgView.setVisibility(GONE);
        }
        if (holder.mItem.getType() == GalleryItem.VIDEO_RESOURCE_TYPE) {
            ImageView imgView = (ImageView) holder.itemView.findViewById(R.id.movie_indicator);
            imgView.setVisibility(View.VISIBLE);
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
        notifyItemRangeChanged(0, getItemCount());
        multiSelectStatusListener.onMultiSelectStatusChanged(allowItemSelection);
        multiSelectStatusListener.onItemSelectionCountChanged(selectedResourceIds.size());
    }

    public boolean isItemSelectionAllowed() {
        return allowItemSelection;
    }

    public HashSet<Long> getSelectedItemIds() {
        return selectedResourceIds;
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

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final AppCompatCheckBox checkBox;
        public final AppCompatImageView mImageView;
        public final TextView mNameView;
        public final TextView mPhotoCountView;
        public final TextView mSubCategoriesView;
//        public final TextView mDescriptionView;
        public final ImageView mRecentlyAlteredMarkerView;
        private final SquareLinearLayout mImageContainer;
        private ResizingPicassoLoader imageLoader;
        public GalleryItem mItem;
        public CustomClickListener itemActionListener;

        public ViewHolder(View view) {
            super(view);
            checkBox = (AppCompatCheckBox)view.findViewById(R.id.checked);
            mNameView = (TextView) view.findViewById(R.id.gallery_name);
            mPhotoCountView = (TextView) view.findViewById(R.id.gallery_photoCount);
            mSubCategoriesView = (TextView) view.findViewById(R.id.gallery_subCategories);
//            mDescriptionView = (TextView) view.findViewById(R.id.gallery_description);
            mImageView = (AppCompatImageView) view.findViewById(R.id.gallery_thumbnail);
            mRecentlyAlteredMarkerView = (ImageView) view.findViewById(R.id.newly_altered_marker_image);
            mImageContainer = (SquareLinearLayout)view.findViewById(R.id.thumbnail_container);

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
