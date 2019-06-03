package delit.piwigoclient.ui.common.recyclerview;

import android.content.Context;
import android.os.Parcelable;
import android.view.View;
import android.widget.TextView;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.Identifiable;
import delit.piwigoclient.model.piwigo.PiwigoAlbum;
import delit.piwigoclient.ui.album.view.AlbumItemRecyclerViewAdapter;
import delit.piwigoclient.ui.album.view.AlbumItemViewHolder;

public class AlbumHeadingViewHolder<S extends Identifiable&Parcelable> extends AlbumItemViewHolder<S> {

    private TextView headingView;
    private int subAlbumCount;
    private boolean showAlbumCount;

    public AlbumHeadingViewHolder(View view, AlbumItemRecyclerViewAdapter<S> parentAdapter, int viewType) {
        super(view, parentAdapter, viewType);
    }

    @Override
    public void setChecked(boolean checked) {
    }

    @Override
    public void cacheViewFieldsAndConfigure() {
        headingView = itemView.findViewById(R.id.heading_text);
    }

    public void setSubAlbumCount(int subAlbumCount) {
        this.subAlbumCount = subAlbumCount;
    }

    public void setShowAlbumCount(boolean showAlbumCount) {
        this.showAlbumCount = showAlbumCount;
    }

    @Override
    public void fillValues(Context context, GalleryItem newItem, boolean allowItemDeletion) {
        setSubAlbumCount(((PiwigoAlbum) getParentAdapter().getItemStore()).getSubAlbumCount());
        showAlbumCount = ((PiwigoAlbum) getParentAdapter().getItemStore()).isHideAlbums();
        switch(viewType) {
            case GalleryItem.ALBUM_HEADING_TYPE:
                if (showAlbumCount) {
                    headingView.setText(context.getString(R.string.album_section_heading_albums_pattern, subAlbumCount));
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

    @Override
    public String toString() {
        return "Header Item";
    }
}
