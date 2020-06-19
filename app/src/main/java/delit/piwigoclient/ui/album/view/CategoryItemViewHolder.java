package delit.piwigoclient.ui.album.view;

import android.view.View;
import android.widget.TextView;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.ResourceContainer;

import static android.view.View.INVISIBLE;

public class CategoryItemViewHolder<Q extends AlbumItemRecyclerViewAdapter.AlbumItemMultiSelectStatusAdapter, M extends ResourceContainer<? extends CategoryItem, GalleryItem>> extends AlbumItemViewHolder<CategoryItem, Q, CategoryItemViewHolder<Q, M>, M> {
    public TextView mPhotoCountView;

    public CategoryItemViewHolder(View view, AlbumItemRecyclerViewAdapter<CategoryItem, Q, CategoryItemViewHolder<Q, M>, M> parentAdapter, int viewType) {
        super(view, parentAdapter, viewType);
    }

    @Override
    public void cacheViewFieldsAndConfigure(AlbumItemRecyclerViewAdapterPreferences adapterPrefs) {
        super.cacheViewFieldsAndConfigure(adapterPrefs);
        mPhotoCountView = itemView.findViewById(R.id.album_photoCount);

        itemView.setOnClickListener(getItemActionListener());
        itemView.setOnLongClickListener(getItemActionListener());
    }

    @Override
    public void fillValues(GalleryItem newItem, boolean allowItemDeletion) {
        super.fillValues(newItem, allowItemDeletion);
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

        if (newItem.getThumbnailUrl() != null) {
            configureLoadingBasicThumbnail(category);
        } else {
            configurePlaceholderThumbnail(category);
        }
    }

    private void configurePlaceholderThumbnail(CategoryItem newItem) {
        imageLoader.setResourceToLoad(R.drawable.ic_photo_library_black_24px);
        if (parentAdapter.getAdapterPrefs().isShowAlbumThumbnailsZoomed()) {
            (imageLoader).setCenterCrop(true);
        } else {
            (imageLoader).setCenterCrop(false);
        }
    }

    private void configureLoadingBasicThumbnail(CategoryItem newItem) {
        imageLoader.setUriToLoad(newItem.getThumbnailUrl());

        if (parentAdapter.getAdapterPrefs().isShowAlbumThumbnailsZoomed()) {
            (imageLoader).setCenterCrop(true);
        } else {
            (imageLoader).setCenterCrop(false);
        }
    }

    @Override
    public void setChecked(boolean checked) {
        throw new UnsupportedOperationException("Shouldn't call this");
    }

}