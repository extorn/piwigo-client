package delit.piwigoclient.ui.album.view;

import android.view.View;
import android.widget.TextView;

import java.util.Objects;

import delit.libs.core.util.Logging;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PiwigoAlbum;
import delit.piwigoclient.model.piwigo.PiwigoUtils;
import delit.piwigoclient.model.piwigo.StaticCategoryItem;

import static android.view.View.INVISIBLE;

public class CategoryItemViewHolder<VH extends CategoryItemViewHolder<VH, LVA, MSL, RC,T>, LVA extends AlbumItemRecyclerViewAdapter<LVA,T,MSL,VH, RC>, MSL extends AlbumItemRecyclerViewAdapter.AlbumItemMultiSelectStatusAdapter<MSL,LVA,VH,RC,T>, RC extends PiwigoAlbum<CategoryItem,T>, T extends CategoryItem> extends AlbumItemViewHolder<VH, LVA, T, MSL, RC> {
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
    public void onRebindOldData(T newItem) {
    }

    private void updateThumbnailImage(T item) {
        if (StaticCategoryItem.BLANK.equals(item)) {
            itemView.setVisibility(View.INVISIBLE);
            imageLoader.resetAll();
            return;
        } else {
            itemView.setVisibility(View.VISIBLE);
        }
        if (item.getThumbnailUrl() != null) {
            configureLoadingBasicThumbnail(item);
        } else {
            configurePlaceholderThumbnail(item);
        }
        imageLoader.load();
    }

    @Override
    public void fillValues(T newItem, boolean allowItemDeletion) {
        try {
            super.fillValues(newItem, allowItemDeletion);
            updateRecentlyViewedMarker(newItem);
            updateTextFields(newItem);
            updateThumbnailImage(newItem);
        } catch(RuntimeException e) {
            Logging.recordException(e);
        }
    }

    private void updateTextFields(T newItem) {
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
    }

    @Override
    public void redisplayOldValues(T newItem, boolean allowItemDeletion) {
        if (newItem.getThumbnailUrl() != null) {
            /* this will occur if we were previously showing an admin version of the category
               and now we're showing a full-fat user version. */
            configureLoadingBasicThumbnail(newItem);
        }
        super.redisplayOldValues(newItem, allowItemDeletion);
    }

    private void configurePlaceholderThumbnail(CategoryItem newItem) {
        imageLoader.setResourceToLoad(R.drawable.ic_photo_library_black_24px);
        imageLoader.setCenterCrop(parentAdapter.getAdapterPrefs().isShowAlbumThumbnailsZoomed());
    }

    private void configureLoadingBasicThumbnail(CategoryItem newItem) {
        imageLoader.setUriToLoad(newItem.getThumbnailUrl());
        imageLoader.setCenterCrop(parentAdapter.getAdapterPrefs().isShowAlbumThumbnailsZoomed());
    }

    @Override
    public void setChecked(boolean checked) {
        throw new UnsupportedOperationException("Shouldn't call this");
    }

    @Override
    public boolean isDirty(T newItem) {
        if(!super.isDirty(newItem)) {
            //return getItem() != null && newItem != null && getItem().isAdminCopy() != newItem.isAdminCopy();
            return getItem() != newItem; //Deliberate reference equals
        }
        return true;
    }
}