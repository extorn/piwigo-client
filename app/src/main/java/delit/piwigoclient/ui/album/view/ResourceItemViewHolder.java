package delit.piwigoclient.ui.album.view;

import android.content.Context;
import android.os.Parcelable;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.AppCompatImageView;
import android.view.View;

import delit.piwigoclient.R;
import delit.piwigoclient.business.PicassoLoader;
import delit.piwigoclient.business.ResizingPicassoLoader;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.Identifiable;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.ui.common.UIHelper;

import static android.view.View.GONE;

public class ResourceItemViewHolder<S extends Identifiable&Parcelable> extends AlbumItemViewHolder<S> {
    public AppCompatImageView mTypeIndicatorImg;
    public AppCompatCheckBox checkBox;

    public ResourceItemViewHolder(View view, AlbumItemRecyclerViewAdapter<S> parentAdapter, int viewType) {
        super(view, parentAdapter, viewType);
    }

    @Override
    public void fillValues(Context context, GalleryItem newItem, boolean allowItemDeletion) {
        super.fillValues(context, newItem, allowItemDeletion);
        updateCheckableStatus();
        checkBox.setOnCheckedChangeListener(parentAdapter.buildItemSelectionListener(this));
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
            PicassoLoader picasso = new PicassoLoader(mTypeIndicatorImg);
            picasso.setResourceToLoad(R.drawable.ic_movie_filter_black_24px);
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
        ResourceItem.ResourceFile rf = resItem.getFile(parentAdapter.getAdapterPrefs().getPreferredThumbnailSize());
        if (rf != null) {
            imageLoader.setUriToLoad(rf.getUrl());
        } else {
            if (newItem.getThumbnailUrl() != null) {
                // this is really bizarre - but show something for now.
                imageLoader.setUriToLoad(newItem.getThumbnailUrl());
            }
        }
        ((ResizingPicassoLoader) imageLoader).setCenterCrop(true);
    }

    @Override
    public void cacheViewFieldsAndConfigure() {
        super.cacheViewFieldsAndConfigure();
        checkBox = itemView.findViewById(R.id.checked);
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