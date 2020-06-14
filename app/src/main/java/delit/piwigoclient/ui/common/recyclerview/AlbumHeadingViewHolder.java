package delit.piwigoclient.ui.common.recyclerview;

import android.view.View;
import android.widget.TextView;

import org.jetbrains.annotations.NotNull;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PiwigoAlbum;
import delit.piwigoclient.ui.album.view.AlbumItemRecyclerViewAdapter;
import delit.piwigoclient.ui.album.view.AlbumItemRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.album.view.AlbumItemViewHolder;

public class AlbumHeadingViewHolder<Q extends AlbumItemRecyclerViewAdapter.AlbumItemMultiSelectStatusAdapter, M extends PiwigoAlbum> extends AlbumItemViewHolder<GalleryItem, Q, AlbumHeadingViewHolder<Q, M>, M> {
    private TextView headingView;
    private int subAlbumCount;
    private boolean showAlbumCount;

    public AlbumHeadingViewHolder(View view, AlbumItemRecyclerViewAdapter<GalleryItem, Q, AlbumHeadingViewHolder<Q, M>, M> parentAdapter, int viewType) {
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
        M album = getParentAdapter().getItemStore();
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

    @NotNull
    @Override
    public String toString() {
        return "Header Item";
    }
}
