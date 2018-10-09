package delit.piwigoclient.ui.slideshow;

import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.View;
import android.widget.ExpandableListView;

import com.crashlytics.android.Crashlytics;
import com.drew.metadata.Metadata;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.ui.common.InlineViewPagerAdapter;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.events.ExifDataRetrievedEvent;

public class AlbumPictureItemFragment extends AbstractAlbumPictureItemFragment {

    private ViewPager resourceDetailsViewPager;

    public static AlbumPictureItemFragment newInstance(PictureResourceItem galleryItem, int albumResourceItemIdx, int albumResourceItemCount, long totalResourceItemCount) {
        AlbumPictureItemFragment fragment = new AlbumPictureItemFragment();
        fragment.setArguments(AbstractSlideshowItemFragment.buildArgs(galleryItem, albumResourceItemIdx, albumResourceItemCount, totalResourceItemCount));
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
                InlineViewPagerAdapter viewPagerAdapter = ((InlineViewPagerAdapter)((ViewPager)v).getAdapter());
                if(viewPagerAdapter != null) {
                    v.getLayoutParams().height = viewPagerAdapter.getLargestDesiredChildHeight();
                }
            }

            @Override
            public void onViewDetachedFromWindow(View v) {

            }
        });
        setupExifDataTab(resourceDetailsViewPager, null);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ExifDataRetrievedEvent event) {
        if(event.getUri() == null) {
            Crashlytics.log(Log.ERROR, getTag(), "invalid event received");
        } else if(getCurrentImageUrlDisplayed() != null && event.getUri().toString().startsWith(getCurrentImageUrlDisplayed())) {
            setupExifDataTab(resourceDetailsViewPager, event.getMetadata());
        }
    }

    private void setupExifDataTab(ViewPager viewPager, Metadata metadata) {
        ExpandableListView exifDataList = viewPager.findViewById(R.id.exifDataList);
        BaseRecyclerViewAdapterPreferences prefs = new BaseRecyclerViewAdapterPreferences();
        prefs.readonly();
        ExifDataListAdapter exifDataListAdapter = ExifDataListAdapter.newAdapter(getContext(), metadata);
        exifDataList.setAdapter(exifDataListAdapter);

        InlineViewPagerAdapter viewPagerAdapter = ((InlineViewPagerAdapter)viewPager.getAdapter());
        if(viewPagerAdapter != null) {
            viewPager.getLayoutParams().height = viewPagerAdapter.getLargestDesiredChildHeight();
            viewPager.getLayoutParams().height = Math.min(viewPager.getLayoutParams().height, viewPager.getRootView().getHeight() / 3 * 2);
        }


    }
}