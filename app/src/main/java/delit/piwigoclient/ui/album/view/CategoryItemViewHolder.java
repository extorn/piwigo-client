package delit.piwigoclient.ui.album.view;

import android.content.Context;
import android.support.v7.widget.AppCompatImageView;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ResizingPicassoLoader;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.Identifiable;
import delit.piwigoclient.ui.common.RoundableImageView;

import static android.view.View.INVISIBLE;

public class CategoryItemViewHolder<S extends Identifiable> extends AlbumItemViewHolder<S> {
    public TextView mPhotoCountView;

    public CategoryItemViewHolder(View view, AlbumItemRecyclerViewAdapter<S> parentAdapter, int viewType) {
        super(view, parentAdapter, viewType);
    }

    @Override
    public void cacheViewFieldsAndConfigure() {
        super.cacheViewFieldsAndConfigure();
        mPhotoCountView = itemView.findViewById(R.id.album_photoCount);

        itemView.setOnClickListener(getItemActionListener());
        itemView.setOnLongClickListener(getItemActionListener());
    }

    @Override
    public void fillValues(Context context, GalleryItem newItem, boolean allowItemDeletion) {
        super.fillValues(context, newItem, allowItemDeletion);
        updateRecentlyViewedMarker(newItem);

        if (CategoryItem.BLANK.equals(newItem)) {
            itemView.setVisibility(View.INVISIBLE);
            imageLoader.resetAll();
            return;
        } else {
            itemView.setVisibility(View.VISIBLE);
        }

        CategoryItem category = (CategoryItem) newItem;

        if (category.getSubCategories() > 0) {
            long totalPhotos = category.getTotalPhotos();
            mPhotoCountView.setText(itemView.getResources().getString(R.string.gallery_subcategory_summary_text_pattern, category.getSubCategories(), totalPhotos));
        } else {
            mPhotoCountView.setText(itemView.getResources().getString(R.string.gallery_photos_summary_text_pattern, category.getPhotoCount()));
            mPhotoCountView.setSingleLine();
        }

        if (!(newItem.getName() == null || newItem.getName().isEmpty())) {
            mNameView.setVisibility(View.VISIBLE);
            mNameView.setText(newItem.getName());
        } else {
            mNameView.setVisibility(INVISIBLE);
        }

        if (!(newItem.getDescription() == null || newItem.getDescription().isEmpty())) {
            mDescView.setVisibility(View.VISIBLE);
            mDescView.setText(newItem.getDescription());
        } else {
            mDescView.setVisibility(INVISIBLE);
        }


        float albumWidth = parentAdapter.getAdapterPrefs().getAlbumWidth();
//        if (albumWidth > 3) {
//            mNameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
//        } else if (albumWidth > 2.4) {
//            mNameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
//        } else if (albumWidth > 1.8) {
//            mNameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
//        } else {
//            mNameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
//        }

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
        if (parentAdapter.getAdapterPrefs().isShowAlbumThumbnailsZoomed()) {
            ((ResizingPicassoLoader) imageLoader).setCenterCrop(true);
        } else {
            ((ResizingPicassoLoader) imageLoader).setCenterCrop(false);
        }
    }

    private void triggerLoadingSpecificThumbnail(CategoryItem newItem) {
        imageLoader.setResourceToLoad(R.drawable.ic_photo_library_black_24px);
        if (parentAdapter.getAdapterPrefs().isShowAlbumThumbnailsZoomed()) {
            ((ResizingPicassoLoader) imageLoader).setCenterCrop(true);
        } else {
            ((ResizingPicassoLoader) imageLoader).setCenterCrop(false);
        }
        if (parentAdapter.getMultiSelectStatusListener() != null) {
            //Now trigger a load of the real data.
            AlbumItemRecyclerViewAdapter.MultiSelectStatusAdapter listener = parentAdapter.getMultiSelectStatusListener();
            listener.notifyAlbumThumbnailInfoLoadNeeded((CategoryItem) newItem); //see AbstractViewAlbumFragment for implementation
        }
    }

    private void configureLoadingBasicThumbnail(CategoryItem newItem) {
        imageLoader.setUriToLoad(newItem.getThumbnailUrl());

        if (parentAdapter.getAdapterPrefs().isShowAlbumThumbnailsZoomed()) {
            ((ResizingPicassoLoader) imageLoader).setCenterCrop(true);
        } else {
            ((ResizingPicassoLoader) imageLoader).setCenterCrop(false);
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
//        if (parentAdapter.getAdapterPrefs().isShowLargeAlbumThumbnails()) {
//            if (parentAdapter.getAdapterPrefs().isShowAlbumThumbnailsZoomed() || "square".equals(parentAdapter.getAdapterPrefs().getPreferredAlbumThumbnailSize())) {
//                roundableImageView.setCornerRadius(14);
//                roundableImageView.setEnableRoundedCorners(true);
//            }
//        } else {
//            if (parentAdapter.getAdapterPrefs().isShowAlbumThumbnailsZoomed() || "square".equals(parentAdapter.getAdapterPrefs().getPreferredAlbumThumbnailSize())) {
//                roundableImageView.setCornerRadius(24);
//                roundableImageView.setEnableRoundedCorners(true);
//            }
//        }
        return super.configureNonMasonryThumbnailLoader(roundableImageView);
    }

    @Override
    public void setChecked(boolean checked) {
        throw new UnsupportedOperationException("Shouldn't call this");
    }
}