package delit.piwigoclient.ui.album.view;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PiwigoAlbum;

public class AlbumHeadingViewHolder<VH extends AlbumHeadingViewHolder<VH,LVA, T,MSL, RC>, LVA extends AlbumItemRecyclerViewAdapter<LVA, T, MSL, VH, RC>, T extends GalleryItem, MSL extends AlbumItemRecyclerViewAdapter.AlbumItemMultiSelectStatusAdapter<MSL,LVA,VH,RC,T>, RC extends PiwigoAlbum<CategoryItem, T>> extends AlbumItemViewHolder<VH, LVA, T, MSL, RC> {

    private TextView headingView;
    private int subAlbumCount;
    private boolean showAlbumCount;

    public AlbumHeadingViewHolder(View view, LVA parentAdapter, int viewType) {
        super(view, parentAdapter, viewType);
    }

    @Override
    public void setChecked(boolean checked) {
    }

    @Override
    public void cacheViewFieldsAndConfigure(AlbumItemRecyclerViewAdapterPreferences adapterPrefs) {
        headingView = itemView.findViewById(R.id.heading_text);
    }

    public void setSubAlbumCount(int subAlbumCount) {
        this.subAlbumCount = subAlbumCount;
    }

    public void setShowAlbumCount(boolean showAlbumCount) {
        this.showAlbumCount = showAlbumCount;
    }

    @Override
    public void fillValues(GalleryItem newItem, boolean allowItemDeletion) {
        RC album = getParentAdapter().getItemStore();
        setSubAlbumCount(album.getSubAlbumCount());
        showAlbumCount = album.isHideAlbums();
        switch(viewType) {
            case GalleryItem.ALBUM_HEADING_TYPE:
                if (showAlbumCount) {
                    headingView.setText(itemView.getContext().getString(R.string.album_section_heading_albums_pattern, subAlbumCount));
                } else {
                    headingView.setText(R.string.album_section_heading_albums);
                }
                break;
            case GalleryItem.PICTURE_HEADING_TYPE:
                headingView.setText(R.string.album_section_heading_resources);
                break;
            default:
        }
    }

    @Override
    public void onRecycled() {
    }

    @NonNull
    @Override
    public String toString() {
        return "Header Item";
    }
}
