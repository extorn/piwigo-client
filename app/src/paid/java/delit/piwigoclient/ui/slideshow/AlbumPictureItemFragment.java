package delit.piwigoclient.ui.slideshow;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ExpandableListView;

import androidx.annotation.LayoutRes;
import androidx.viewpager.widget.ViewPager;

import com.drew.metadata.Metadata;
import com.ortiz.touchview.TouchImageView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.ref.WeakReference;

import delit.libs.core.util.Logging;
import delit.libs.ui.OwnedSafeAsyncTask;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.CustomViewPager;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AlbumViewPreferences;
import delit.piwigoclient.business.PicassoLoader;
import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.events.ExifDataRetrievedEvent;
import delit.piwigoclient.ui.events.SlideshowItemPageFinished;
import delit.piwigoclient.ui.model.ViewModelContainer;
import pl.droidsonroids.gif.GifDrawable;

public class AlbumPictureItemFragment<F extends AlbumPictureItemFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH, F>, T extends PictureResourceItem> extends AbstractAlbumPictureItemFragment<F,FUIH, T> {

    private static final String TAG = "AlbumPicItemFr";
    private ViewPager resourceDetailsViewPager;

    public static AlbumPictureItemFragment<?,?,?> newInstance(Class<? extends ViewModelContainer> modelType, long albumId, long albumItemId, int albumResourceItemIdx, int albumResourceItemCount, long totalResourceItemCount) {
        AlbumPictureItemFragment<?,?,?> fragment = new AlbumPictureItemFragment<>();
        fragment.setArguments(AbstractSlideshowItemFragment.buildArgs(modelType, albumId, albumItemId, albumResourceItemIdx, albumResourceItemCount, totalResourceItemCount));
        return fragment;
    }

    @Override
    protected @LayoutRes
    int getLayoutId() {
        return R.layout.fragment_picture_slideshow_item;
    }

    @Override
    protected void setupImageDetailPopup(View v, Bundle savedInstanceState) {
        super.setupImageDetailPopup(v, savedInstanceState);
        resourceDetailsViewPager = v.findViewById(R.id.slideshow_resource_details_tabs_content);
        resourceDetailsViewPager.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                v.getLayoutParams().height = ((CustomViewPager)v).getMinimumDesiredHeight();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {

            }
        });
        setupExifDataTab(resourceDetailsViewPager, null);
    }

    private void notifyPagerItemFinished(boolean immediate) {
        if(getImageView().getDrawable() instanceof GifDrawable) {
            GifDrawable gifDrawable = (GifDrawable)getImageView().getDrawable();
            int duration = gifDrawable.getDuration();
            DisplayUtils.runOnUiThread(() -> {
                // then this image is showing to the user and we should notify the pager that we're done.
                EventBus.getDefault().post(new SlideshowItemPageFinished(getPagerIndex(), immediate));
            }, duration + AlbumViewPreferences.getAutoDriveVideoDelayMillis(prefs, requireContext()));
        } else {
            // then this image is showing to the user and we should notify the pager that we're done.
            EventBus.getDefault().post(new SlideshowItemPageFinished(getPagerIndex(), immediate));
        }
    }

    @Override
    protected void doOnPageSelectedAndAdded() {
        super.doOnPageSelectedAndAdded();
        if (getPicassoImageLoader().hasPlaceholder() && getPicassoImageLoader().isImageLoaded()) {
            // then the actual image has been loaded.
            notifyPagerItemFinished(true);
        }
    }

    @Override
    public void onImageLoaded(PicassoLoader<TouchImageView> loader, boolean success) {
        if (success && loader.hasPlaceholder() && loader.isImageLoaded() && isPrimarySlideshowItem()) {
            notifyPagerItemFinished(false);
        }
        super.onImageLoaded(loader, success);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(ExifDataRetrievedEvent event) {
        if(event.getUri() == null) {
            Logging.log(Log.ERROR, TAG, "invalid event received");
        } else if(getCurrentImageUrlDisplayed() != null && event.getUri().startsWith(getCurrentImageUrlDisplayed())) {
            setupExifDataTab(resourceDetailsViewPager, event.getMetadata());
        }
    }

    private void setupExifDataTab(ViewPager viewPager, Metadata metadata) {
        new ExifDataLoadTask(this, viewPager, metadata).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);


    }

    private static class ExifDataLoadTask<F extends AlbumPictureItemFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH, F>, T extends PictureResourceItem> extends OwnedSafeAsyncTask<F, Void,Void, ExifDataListAdapter> {
        private final WeakReference<ViewPager> viewPagerRef;
        private final Metadata metadata;

        public ExifDataLoadTask(F owner, ViewPager viewPager, Metadata metadata) {
            super(owner);
            this.viewPagerRef = new WeakReference<>(viewPager);
            this.metadata = metadata;
        }

        @Override
        protected ExifDataListAdapter doInBackgroundSafely(Void... voids) {
//            BaseRecyclerViewAdapterPreferences prefs = new BaseRecyclerViewAdapterPreferences();
//            prefs.readonly();
            ViewPager viewPager = viewPagerRef.get();
            if(viewPager != null) {
                return ExifDataListAdapter.newAdapter(viewPager.getContext(), metadata);
            }
            return null;
        }

        @Override
        protected void onPostExecuteSafely(ExifDataListAdapter exifDataListAdapter) {
            super.onPostExecuteSafely(exifDataListAdapter);
            ViewPager viewPager = viewPagerRef.get();
            if(viewPager != null) {
                ExpandableListView exifDataList = viewPager.findViewById(R.id.exifDataList);
                exifDataList.setAdapter(exifDataListAdapter);
            }
        }
    }
}