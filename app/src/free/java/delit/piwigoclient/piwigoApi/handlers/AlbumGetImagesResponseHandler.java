package delit.piwigoclient.piwigoApi.handlers;

import java.util.Set;

import delit.piwigoclient.model.piwigo.CategoryItem;

public class AlbumGetImagesResponseHandler extends AlbumGetImagesBasicResponseHandler {

    public AlbumGetImagesResponseHandler(CategoryItem parentAlbum, String sortOrder, int page, int pageSize) {
        super(parentAlbum, sortOrder, page, pageSize);
    }

    @Override
    protected ResourceParser buildResourceParser(String basePiwigoUrl) {
        return new ResourceParser(basePiwigoUrl);
    }

    public static class ResourceParser extends BasicCategoryImageResourceParser {

        public ResourceParser(String basePiwigoUrl) {
            super(basePiwigoUrl);
        }
    }
}