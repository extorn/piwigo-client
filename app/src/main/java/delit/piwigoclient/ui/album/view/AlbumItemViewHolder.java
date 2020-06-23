package delit.piwigoclient.ui.album.view;

import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import org.jetbrains.annotations.NotNull;

import delit.libs.ui.view.recycler.CustomViewHolder;
import delit.piwigoclient.R;
import delit.piwigoclient.business.PicassoLoader;
import delit.piwigoclient.business.ResizingPicassoLoader;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.ResourceContainer;
import delit.piwigoclient.ui.common.UIHelper;

import static android.view.View.GONE;

public abstract class AlbumItemViewHolder<S extends GalleryItem, Q extends AlbumItemRecyclerViewAdapter.AlbumItemMultiSelectStatusAdapter, P extends AlbumItemViewHolder<S, Q, P, M>, M extends ResourceContainer<? extends S, GalleryItem>> extends CustomViewHolder<AlbumItemRecyclerViewAdapterPreferences, GalleryItem> implements PicassoLoader.PictureItemImageLoaderListener {
    protected final int viewType;
    public AppCompatImageView mImageView;
    public TextView mNameView;
    public TextView mDescView;
    public ImageView mRecentlyAlteredMarkerView;
    protected ResizingPicassoLoader<ImageView> imageLoader;
    protected AlbumItemRecyclerViewAdapter<S, Q, P, M> parentAdapter;
    protected View mItemContainer;

    public AlbumItemViewHolder(View view, AlbumItemRecyclerViewAdapter<S, Q, P, M> parentAdapter, int viewType) {
        super(view);
        this.parentAdapter = parentAdapter;
        this.viewType = viewType;

    }

    public AlbumItemRecyclerViewAdapter<S, Q, P, M> getParentAdapter() {
        return parentAdapter;
    }

    @Override
    public <T> void redisplayOldValues(T newItem, boolean allowItemDeletion) {
        if (!imageLoader.isImageLoading() && !imageLoader.isImageLoaded()) {
            imageLoader.load();
        }
    }

    @Override
    public void fillValues(GalleryItem newItem, boolean allowItemDeletion) {
        setItem(newItem);
        getItemActionListener().onFillValues();
    }

    protected void updateRecentlyViewedMarker(GalleryItem newItem) {
        if (mRecentlyAlteredMarkerView != null) {
            if (newItem.getLastAltered() != null && newItem.getLastAltered().compareTo(parentAdapter.getAdapterPrefs().getRecentlyAlteredThresholdDate()) > 0) {
                // is null for blank categories (dummmy spacers) and also for categories only visible because this is an admin user (without explicit access)
                mRecentlyAlteredMarkerView.setVisibility(View.VISIBLE);
            } else {
                mRecentlyAlteredMarkerView.setVisibility(GONE);
            }
        }
    }

    @Override
    public void cacheViewFieldsAndConfigure(AlbumItemRecyclerViewAdapterPreferences adapterPrefs) {
        mNameView = itemView.findViewById(R.id.resource_name);
        mDescView = itemView.findViewById(R.id.resource_description);
        mImageView = itemView.findViewById(R.id.resource_thumbnail);
        mRecentlyAlteredMarkerView = itemView.findViewById(R.id.newly_altered_marker_image);
        mItemContainer = itemView.findViewById(R.id.item_container);
        imageLoader = new ResizingPicassoLoader<>(mImageView, this, 0, 0);
        imageLoader.load();
        mImageView.setContentDescription("resource thumb");
        mImageView.setOnClickListener(getItemActionListener());
        mImageView.setOnLongClickListener(getItemActionListener());

    }

    @NotNull
    @Override
    public String toString() {
        return super.toString() + " '" + mNameView.getText() + "'";
    }

    public void onRecycled() {
        UIHelper.recycleImageViewContent(mImageView);
    }

    @Override
    public void onBeforeImageLoad(PicassoLoader loader) {
        mImageView.setBackgroundColor(Color.TRANSPARENT);
    }

    @Override
    public void onImageLoaded(PicassoLoader loader, boolean success) {
        mImageView.setBackgroundColor(Color.TRANSPARENT);
    }

    @Override
    public void onImageUnavailable(PicassoLoader loader, String lastLoadError) {
        mImageView.setBackgroundColor(ContextCompat.getColor(mImageView.getContext(), R.color.color_scrim_heavy));
    }
}