package delit.piwigoclient.ui.slideshow;

import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.TabHost;

import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.ExifDataItem;
import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.events.ExifDataRetrievedEvent;

import static delit.piwigoclient.business.CustomImageDownloader.EXIF_WANTED_URI_FLAG;

public class AlbumPictureItemFragment extends AbstractAlbumPictureItemFragment {

    private View exifDataView;

    public static AlbumPictureItemFragment newInstance(PictureResourceItem galleryItem, int albumResourceItemIdx, int albumResourceItemCount, long totalResourceItemCount) {
        AlbumPictureItemFragment fragment = new AlbumPictureItemFragment();
        fragment.setArguments(buildArgs(galleryItem, albumResourceItemIdx, albumResourceItemCount, totalResourceItemCount));
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
        TabHost tabPanels = v.findViewById(R.id.slideshow_image_tab_panels);
        tabPanels.setup();
        TabHost.TabSpec basicInfoTab = tabPanels.newTabSpec("BasicInfoTab");
        TabHost.TabSpec exifInfoTab = tabPanels.newTabSpec("EXIFdataTab");
        basicInfoTab.setIndicator(getString(R.string.slideshow_image_tab_basic_info));
        basicInfoTab.setContent(R.id.picture_resource_basic_fields);
        exifInfoTab.setIndicator(getString(R.string.slideshow_image_tab_exif_data));
        exifInfoTab.setContent(R.id.picture_resource_exif_data);
        exifDataView = v.findViewById(R.id.picture_resource_exif_data);
        setupExifDataTab(exifDataView, null);
        tabPanels.addTab(basicInfoTab);
        tabPanels.addTab(exifInfoTab);

    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ExifDataRetrievedEvent event) {
        if(event.getUri().toString().startsWith(getCurrentImageUrlDisplayed())) {
            setupExifDataTab(exifDataView, event.getMetadata());
        }
    }

    private void setupExifDataTab(View exifTabContentView, Metadata metadata) {
        ExpandableListView exifDataList = exifTabContentView.findViewById(R.id.exifDataList);
        BaseRecyclerViewAdapterPreferences prefs = new BaseRecyclerViewAdapterPreferences();
        prefs.readonly();
        ExifDataListAdapter exifDataListAdapter = ExifDataListAdapter.newAdapter(getContext(), metadata);
        exifDataList.setAdapter(exifDataListAdapter);
    }
}