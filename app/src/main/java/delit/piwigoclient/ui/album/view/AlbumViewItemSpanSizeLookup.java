package delit.piwigoclient.ui.album.view;

import android.util.Log;

import androidx.recyclerview.widget.GridLayoutManager;

import delit.libs.core.util.Logging;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PiwigoAlbum;
import delit.piwigoclient.model.piwigo.ResourceContainer;

public class AlbumViewItemSpanSizeLookup extends GridLayoutManager.SpanSizeLookup {

    private final int totalSpans;
    private final int spansPerAlbum;
    private final int spansPerImage;
    private ResourceContainer<CategoryItem, GalleryItem> galleryModel;

    AlbumViewItemSpanSizeLookup(ResourceContainer<CategoryItem, GalleryItem> galleryModel, int totalSpans, int spansPerAlbum, int spansPerImage) {
        this.totalSpans = totalSpans;
        this.spansPerAlbum = spansPerAlbum;
        this.spansPerImage = spansPerImage;
        this.galleryModel = galleryModel;
    }

    public void replaceGalleryModel(PiwigoAlbum<CategoryItem, GalleryItem> model) {
        this.galleryModel = model;
    }

    @Override
    public int getSpanSize(int position) {
        int itemType = -1;
        try {
            itemType = galleryModel.getItemByIdx(position).getType();
        } catch(IndexOutOfBoundsException e) {
            Logging.log(Log.WARN, AbstractViewAlbumFragment.TAG, "SpanSizeLookup gallery out of sync with the gallery being shown. Request for item at position %1$d, len=%2$d", position, galleryModel.getItems().size());
            Logging.recordException(e);
        }
        switch (itemType) {
            case GalleryItem.ALBUM_HEADING_TYPE:
                return totalSpans;
            case GalleryItem.CATEGORY_TYPE:
                return spansPerAlbum;
            case GalleryItem.PICTURE_RESOURCE_TYPE:
                return spansPerImage;
            case GalleryItem.PICTURE_HEADING_TYPE:
                return totalSpans;
            case GalleryItem.VIDEO_RESOURCE_TYPE:
                return spansPerImage;
            default:
                return totalSpans;
        }
    }
}
