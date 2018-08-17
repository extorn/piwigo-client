package delit.piwigoclient.ui.album.view;

import android.content.Context;
import android.support.v7.widget.AppCompatImageView;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;

import delit.piwigoclient.R;
import delit.piwigoclient.business.PicassoLoader;
import delit.piwigoclient.business.ResizingPicassoLoader;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.Identifiable;
import delit.piwigoclient.ui.common.SquareLinearLayout;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.recyclerview.CustomViewHolder;

import static android.view.View.GONE;
import static delit.piwigoclient.ui.album.view.AlbumItemRecyclerViewAdapterPreferences.SCALING_QUALITY_VLOW;

public abstract class AlbumItemViewHolder<S extends Identifiable> extends CustomViewHolder<AlbumItemRecyclerViewAdapterPreferences, GalleryItem> {
    protected final int viewType;
    public AppCompatImageView mImageView;
    public TextView mNameView;
    //        public final TextView mDescriptionView;
    public ImageView mRecentlyAlteredMarkerView;
    protected SquareLinearLayout mImageContainer;
    protected PicassoLoader imageLoader;
    protected AlbumItemRecyclerViewAdapter<S> parentAdapter;
    protected View mItemContainer;

    public AlbumItemViewHolder(View view, AlbumItemRecyclerViewAdapter<S> parentAdapter, int viewType) {
        super(view);
        this.parentAdapter = parentAdapter;
        this.viewType = viewType;

    }

    public AlbumItemRecyclerViewAdapter<S> getParentAdapter() {
        return parentAdapter;
    }

    @Override
    public <T> void redisplayOldValues(Context context, T newItem, boolean allowItemDeletion) {
        if (!imageLoader.isImageLoading() && !imageLoader.isImageLoaded()) {
            imageLoader.load();
        }
    }

    @Override
    public void fillValues(Context context, GalleryItem newItem, boolean allowItemDeletion) {
        setItem(newItem);
        getItemActionListener().onFillValues();
    }

    protected void updateRecentlyViewedMarker(GalleryItem newItem) {
        if (mRecentlyAlteredMarkerView != null) {
            if (newItem.getLastAltered() != null && newItem.getLastAltered().compareTo(parentAdapter.getAdapterPrefs().getRecentlyAlteredThresholdDate()) > 0) {
                // is null for blank categories (dummmy spacers) and also for categories only visible because this is an admin user (without explicit access)
                PicassoLoader picasso = new PicassoLoader(mRecentlyAlteredMarkerView);
                picasso.setResourceToLoad(R.drawable.ic_star_yellow_24dp);
                picasso.load();
                mRecentlyAlteredMarkerView.setVisibility(View.VISIBLE);
            } else {
                mRecentlyAlteredMarkerView.setVisibility(GONE);
            }
        }
    }

    @Override
    public void cacheViewFieldsAndConfigure() {
        mNameView = itemView.findViewById(R.id.resource_name);
        mImageView = itemView.findViewById(R.id.resource_thumbnail);
        mRecentlyAlteredMarkerView = itemView.findViewById(R.id.newly_altered_marker_image);
        mImageContainer = itemView.findViewById(R.id.thumbnail_container);
        mItemContainer = itemView.findViewById(R.id.item_container);

        final ViewTreeObserver.OnPreDrawListener predrawListener;
        if (!parentAdapter.getAdapterPrefs().isUseMasonryStyle()) {
            predrawListener = configureNonMasonryThumbnailLoader(mImageView);
        } else {
            predrawListener = configureMasonryThumbnailLoader(mImageView);
        }
        mImageView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                mImageView.getViewTreeObserver().addOnPreDrawListener(predrawListener);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                mImageView.getViewTreeObserver().removeOnPreDrawListener(predrawListener);
            }
        });
        mImageView.setOnClickListener(getItemActionListener());
        mImageView.setOnLongClickListener(getItemActionListener());

    }

    protected ViewTreeObserver.OnPreDrawListener configureMasonryThumbnailLoader(AppCompatImageView target) {
        imageLoader = new PicassoLoader(target);
        return new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                try {
                    if (!imageLoader.isImageLoaded() && !imageLoader.isImageLoading()) {
                        imageLoader.load();
                    }
                } catch (IllegalStateException e) {
                    Crashlytics.logException(e);
                    // image loader not configured yet...
                }
                return true;
            }
        };
    }

    protected ViewTreeObserver.OnPreDrawListener configureNonMasonryThumbnailLoader(final AppCompatImageView target) {
        imageLoader = new ResizingPicassoLoader(target, 0, 0);
        return new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                try {
                    if(!imageLoader.hasResourceToLoad()) {
                        return true;
                    }
                    if (!imageLoader.isImageLoaded() && !imageLoader.isImageLoading() && !imageLoader.isImageUnavailable()) {

                        int desiredScalingQuality = parentAdapter.getAdapterPrefs().getScalingQuality();
                        int imgSize = desiredScalingQuality;
                        if (imgSize == Integer.MAX_VALUE) {
                            imgSize = target.getMeasuredWidth();
                        } else {
                            // need that math.max to ensure that the image size remains positive
                            imgSize = Math.max(SCALING_QUALITY_VLOW, Math.min(desiredScalingQuality, target.getMeasuredWidth()));
                        }
                        ((ResizingPicassoLoader) imageLoader).setResizeTo(imgSize, imgSize);
                        imageLoader.load();
                    }
                } catch (IllegalStateException e) {
                    Crashlytics.logException(e);
                    // image loader not configured yet...
                }
                return true;
            }
        };
    }

    @Override
    public String toString() {
        return super.toString() + " '" + mNameView.getText() + "'";
    }

    public void onRecycled() {
        UIHelper.recycleImageViewContent(mImageView);
        UIHelper.recycleImageViewContent(mRecentlyAlteredMarkerView);
    }
}