package delit.piwigoclient.ui.album.view;

import android.content.Context;
import android.graphics.Color;
import android.support.v7.widget.AppCompatImageView;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ResizingPicassoLoader;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.Identifiable;
import delit.piwigoclient.ui.common.RoundableImageView;

import static android.view.View.GONE;

public class CategoryItemViewHolder<S extends Identifiable> extends AlbumItemViewHolder<S> {
        public TextView mPhotoCountView;
        public TextView mSubCategoriesView;

        public CategoryItemViewHolder(View view, AlbumItemRecyclerViewAdapter<S> parentAdapter, int viewType) {
            super(view, parentAdapter, viewType);
        }

        @Override
        public void cacheViewFieldsAndConfigure() {
            super.cacheViewFieldsAndConfigure();
            mPhotoCountView = itemView.findViewById(R.id.album_photoCount);
            mSubCategoriesView = itemView.findViewById(R.id.album_subCategories);

            if(!parentAdapter.getAdapterPrefs().isShowLargeAlbumThumbnails()) {
                itemView.setOnClickListener(getItemActionListener());
                itemView.setOnLongClickListener(getItemActionListener());
            }
            setItemBackground(viewType, this);
        }

        @Override
        public void fillValues(Context context, GalleryItem newItem, boolean allowItemDeletion) {
            super.fillValues(context, newItem, allowItemDeletion);
            updateRecentlyViewedMarker(newItem);

            if(CategoryItem.BLANK.equals(newItem)) {
                itemView.setVisibility(View.INVISIBLE);
                return;
            } else {
                itemView.setVisibility(View.VISIBLE);
            }

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

            if (!(newItem.getName() == null || newItem.getName().isEmpty())) {
                mNameView.setVisibility(View.VISIBLE);
                mNameView.setText(newItem.getName());
            } else {
                mNameView.setVisibility(GONE);
            }
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

            //String preferredThubnailSize = getParentAdapter().getAdapterPrefs().getPreferredAlbumThumbnailSize();
            /*if(!"DEFAULT".equals(preferredThubnailSize) && category.getRepresentativePictureId() != null) {
                // user requested a specific thumbnail
                if(((CategoryItem) newItem).getPreferredThumbnailUrl() == null) {
                    triggerLoadingSpecificThumbnail(category);
                } else {
                    configureLoadingPreferredThumbnail(category);
                }
            } else */
            if (newItem.getThumbnailUrl() != null) {
                configureLoadingBasicThumbnail(category);
            } else {
                configurePlaceholderThumbnail(category);
            }
        }

    private void configurePlaceholderThumbnail(CategoryItem newItem) {
        imageLoader.setResourceToLoad(R.drawable.ic_photo_library_black_24px);
        if(!parentAdapter.getAdapterPrefs().isUseMasonryStyle()) {
            ((ResizingPicassoLoader) imageLoader).setCenterCrop(false);
        }
    }

    private void triggerLoadingSpecificThumbnail(CategoryItem newItem) {
        imageLoader.setResourceToLoad(R.drawable.ic_photo_library_black_24px);
        if(!parentAdapter.getAdapterPrefs().isUseMasonryStyle()) {
            ((ResizingPicassoLoader) imageLoader).setCenterCrop(false);
        }
        if(parentAdapter.getMultiSelectStatusListener() != null) {
            //Now trigger a load of the real data.
            AlbumItemRecyclerViewAdapter.MultiSelectStatusAdapter listener = parentAdapter.getMultiSelectStatusListener();
            listener.notifyAlbumThumbnailInfoLoadNeeded((CategoryItem) newItem); //see AbstractViewAlbumFragment for implementation
        }
    }

    private void configureLoadingBasicThumbnail(CategoryItem newItem) {
        imageLoader.setUriToLoad(newItem.getThumbnailUrl());

        if(!parentAdapter.getAdapterPrefs().isUseMasonryStyle()) {
            if (parentAdapter.getAdapterPrefs().isShowAlbumThumbnailsZoomed()) {
                ((ResizingPicassoLoader) imageLoader).setCenterCrop(true);
            } else {
                ((ResizingPicassoLoader) imageLoader).setCenterCrop(false);
            }
        }
    }

//    @Override
//    protected ViewTreeObserver.OnPreDrawListener configureMasonryThumbnailLoader(AppCompatImageView target) {
//        target = new RoundedImageView(target);
//        return super.configureMasonryThumbnailLoader(target);
//    }

    @Override
    protected ViewTreeObserver.OnPreDrawListener configureNonMasonryThumbnailLoader(AppCompatImageView target) {
        RoundableImageView roundableImageView = (RoundableImageView) target;
        //TODO this radius isn't working very logically - most likely because the image is being scaled afterwards!
        if (parentAdapter.getAdapterPrefs().isShowLargeAlbumThumbnails()) {
            roundableImageView.setCornerRadius(2);
            roundableImageView.setEnableRoundedCorners(true);
        } else {
            roundableImageView.setCornerRadius(8);
            roundableImageView.setEnableRoundedCorners(true);
        }
        return super.configureNonMasonryThumbnailLoader(roundableImageView);
    }

    private void setItemBackground(int viewType, AlbumItemViewHolder viewHolder) {
            if (parentAdapter.getAdapterPrefs().isUseDarkMode()) {
                if (parentAdapter.getAdapterPrefs().isUseMasonryStyle()) {
                    // needed for the background behind the title text
                    itemView.setBackgroundResource(R.drawable.curved_corners_layout_bg_dark);
                    // needed for images that don't load correctly.
                    mImageView.setBackgroundColor(Color.WHITE);
                } else {
                    mImageView.setBackgroundColor(Color.WHITE);
                    itemView.setBackgroundResource(R.drawable.curved_corners_layout_bg_white);
                    if(parentAdapter.getAdapterPrefs().isShowLargeAlbumThumbnails()) {
                        mImageContainer.setBackgroundResource(R.drawable.curved_corners_layout_bg_dark);
                    }
                }
            }
        }

        @Override
        public void setChecked(boolean checked) {
            throw new UnsupportedOperationException("Shouldn't call this");
        }
    }