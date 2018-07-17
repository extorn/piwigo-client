package delit.piwigoclient.ui.slideshow;

import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TabHost;

import java.util.ArrayList;
import java.util.List;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.ExifDataItem;
import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;

public class AlbumPictureItemFragment extends AbstractAlbumPictureItemFragment {

    public static AlbumPictureItemFragment newInstance(PictureResourceItem galleryItem, long albumResourceItemIdx, long albumResourceItemCount, long totalResourceItemCount) {
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
    protected void setupImageDetailPopup(View v, LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.setupImageDetailPopup(v, inflater, container, savedInstanceState);
        TabHost tabPanels = v.findViewById(R.id.slideshow_image_tab_panels);
        tabPanels.setup();
        TabHost.TabSpec basicInfoTab = tabPanels.newTabSpec("BasicInfoTab");
        TabHost.TabSpec exifInfoTab = tabPanels.newTabSpec("EXIFdataTab");
        basicInfoTab.setIndicator(getString(R.string.slideshow_image_tab_basic_info));
        basicInfoTab.setContent(R.id.gallery_details_edit_fields);
        exifInfoTab.setIndicator(getString(R.string.slideshow_image_tab_exif_data));
        exifInfoTab.setContent(R.id.picture_resource_exif_data);
        setupExifDataTab(v.findViewById(R.id.picture_resource_exif_data));

        tabPanels.addTab(basicInfoTab);
        tabPanels.addTab(exifInfoTab);

    }

    private void setupExifDataTab(View exifTabContentView) {
        RecyclerView exifDataList = exifTabContentView.findViewById(R.id.exifDataList);
        BaseRecyclerViewAdapterPreferences prefs = new BaseRecyclerViewAdapterPreferences();
        prefs.readonly();
        ExifDataListAdapter exifDataListAdapter = new ExifDataListAdapter(null, prefs);
        List<ExifDataItem> data = new ArrayList<>();
        data.add(new ExifDataItem("Exif Data : ", getString(R.string.picture_resource_exif_data_unavailable)));
        data.add(new ExifDataItem("Exif Data : ", getString(R.string.picture_resource_exif_data_unavailable_no_plugins)));
        exifDataListAdapter.setData(data);
        exifDataList.setLayoutManager(new GridLayoutManager(getContext(), 2, RecyclerView.VERTICAL, false));
        exifDataList.setAdapter(exifDataListAdapter);
    }
}