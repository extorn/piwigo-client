package delit.piwigoclient.ui.album.view;

import android.view.View;

import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.appcompat.widget.AppCompatImageView;

import com.google.android.exoplayer2.util.MimeTypes;

import delit.piwigoclient.R;
import delit.piwigoclient.business.PicassoLoader;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PiwigoAlbum;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.ui.common.UIHelper;

import static android.view.View.GONE;

public class ResourceItemViewHolder<VH extends ResourceItemViewHolder<VH,LVA,MSL,T, RC>, LVA extends AlbumItemRecyclerViewAdapter<LVA, T, MSL, VH, RC>, MSL extends AlbumItemRecyclerViewAdapter.AlbumItemMultiSelectStatusAdapter<T>, T extends ResourceItem, RC extends PiwigoAlbum<T>> extends AlbumItemViewHolder<VH, LVA, T, MSL, RC> {
    public AppCompatImageView mTypeIndicatorImg;
    public AppCompatCheckBox checkBox;

    public ResourceItemViewHolder(View view, LVA parentAdapter, int viewType) {
        super(view, parentAdapter, viewType);
    }

    @Override
    public void fillValues(T newItem, boolean allowItemDeletion) {
        super.fillValues(newItem, allowItemDeletion);
        updateCheckableStatus();
        checkBox.setOnCheckedChangeListener(parentAdapter.buildItemSelectionListener((VH)this));
        updateRecentlyViewedMarker(newItem);

        if (!(newItem.getName() == null || newItem.getName().isEmpty()) && parentAdapter.getAdapterPrefs().isShowResourceNames()) {
            mNameView.setVisibility(View.VISIBLE);
            mNameView.setText(newItem.getName());
        } else {
            mNameView.setVisibility(GONE);
        }
        fillResourceItemThumbnailValue(newItem);
        setTypeIndicatorStatus(newItem);
    }

    public void updateCheckableStatus() {
        checkBox.setVisibility(parentAdapter.getAdapterPrefs().isAllowItemSelection() ? View.VISIBLE : GONE);
        checkBox.setChecked(parentAdapter.isItemSelected(getItem().getId()));
    }

    private void setTypeIndicatorStatus(GalleryItem newItem) {
        if (newItem.getType() == GalleryItem.VIDEO_RESOURCE_TYPE) {
            String itemMime = ((ResourceItem)newItem).guessMimeTypeFromUri();
            PicassoLoader picasso = new PicassoLoader<>(mTypeIndicatorImg);
            if(itemMime == null || MimeTypes.isVideo(itemMime)) {
                picasso.setResourceToLoad(R.drawable.ic_movie_filter_black_24px);
            } else if(MimeTypes.isAudio(itemMime)) {
                picasso.setResourceToLoad(R.drawable.ic_audiotrack_black_24dp);
            }
            picasso.load();
            mTypeIndicatorImg.setVisibility(View.VISIBLE);
        } else {
            mTypeIndicatorImg.setVisibility(View.GONE);
        }
    }

    private void fillResourceItemThumbnailValue(GalleryItem newItem) {
        if (imageLoader.isImageLoading()) {
            imageLoader.cancelImageLoadIfRunning();
        }
        ResourceItem resItem = (ResourceItem) newItem;
        String rf = resItem.getFileUrl(parentAdapter.getAdapterPrefs().getPreferredThumbnailSize());
        if (rf != null) {
            imageLoader.setUriToLoad(rf);
        } else {
            imageLoader.setUriToLoad(resItem.getFirstSuitableUrl());
        }
        imageLoader.setCenterCrop(true);
    }

    @Override
    public void cacheViewFieldsAndConfigure(AlbumItemRecyclerViewAdapterPreferences adapterPrefs) {
        super.cacheViewFieldsAndConfigure(adapterPrefs);
        checkBox = itemView.findViewById(R.id.list_item_checked);
        mTypeIndicatorImg = itemView.findViewById(R.id.type_indicator);
    }

    @Override
    public void onRecycled() {
        UIHelper.recycleImageViewContent(mImageView);
        UIHelper.recycleImageViewContent(mRecentlyAlteredMarkerView);
        UIHelper.recycleImageViewContent(mTypeIndicatorImg);
    }


    @Override
    public void setChecked(boolean checked) {
        checkBox.setChecked(checked);
    }
}