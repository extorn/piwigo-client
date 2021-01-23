package delit.piwigoclient.ui.album.view;

import android.view.View;
import android.widget.TextView;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.PiwigoAlbum;
import delit.piwigoclient.model.piwigo.PiwigoUtils;

import static android.view.View.INVISIBLE;

public class CategoryItemViewHolder<VH extends CategoryItemViewHolder<VH, LVA, MSL, RC,T>, LVA extends AlbumItemRecyclerViewAdapter<LVA,T,MSL,VH, RC>, MSL extends AlbumItemRecyclerViewAdapter.AlbumItemMultiSelectStatusAdapter<MSL,LVA,VH,RC,T>, RC extends PiwigoAlbum<T>, T extends CategoryItem> extends AlbumItemViewHolder<VH, LVA, T, MSL, RC> {
    public TextView mPhotoCountView;

    public CategoryItemViewHolder(View view, LVA parentAdapter, int viewType) {
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
    public void fillValues(T newItem, boolean allowItemDeletion) {
        super.fillValues(newItem, allowItemDeletion);
        updateRecentlyViewedMarker(newItem);

        if (CategoryItem.BLANK.equals(newItem)) {
            itemView.setVisibility(View.INVISIBLE);
            imageLoader.resetAll();
            return;
        } else {
            itemView.setVisibility(View.VISIBLE);
        }

        if (newItem.getSubCategories() > 0) {
            long totalPhotos = newItem.getTotalPhotos();
            mPhotoCountView.setText(itemView.getResources().getString(R.string.gallery_subcategory_summary_text_pattern, newItem.getSubCategories(), totalPhotos));
        } else {
            mPhotoCountView.setText(itemView.getResources().getString(R.string.gallery_photos_summary_text_pattern, newItem.getPhotoCount()));
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
            // support for the extended description plugin.
            String desc = PiwigoUtils.getResourceDescriptionOutsideAlbum(newItem.getDescription());
            mDescView.setText(PiwigoUtils.getSpannedHtmlText(desc));
        } else {
            mDescView.setVisibility(INVISIBLE);
        }

        if (newItem.getThumbnailUrl() != null) {
            configureLoadingBasicThumbnail(newItem);
        } else {
            configurePlaceholderThumbnail(newItem);
        }
        imageLoader.load();
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