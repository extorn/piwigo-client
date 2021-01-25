package delit.piwigoclient.piwigoApi.handlers;

import java.util.Set;

import delit.piwigoclient.model.piwigo.CategoryItem;

public class AlbumGetImagesResponseHandler extends AlbumGetImagesBasicResponseHandler {

    public AlbumGetImagesResponseHandler(CategoryItem parentAlbum, String sortOrder, int page, int pageSize, Set<String> multimediaExtensionList) {
        super(parentAlbum, sortOrder, page, pageSize, multimediaExtensionList);
    }

    @Override
    protected ResourceParser buildResourceParser(Set<String> multimediaExtensionList, String basePiwigoUrl) {
        return new ResourceParser(multimediaExtensionList, basePiwigoUrl);
    }

    public static class ResourceParser extends BasicCategoryImageResourceParser {

        public ResourceParser(Set<String> multimediaExtensionList, String basePiwigoUrl) {
            super(multimediaExtensionList, basePiwigoUrl);
        }
    }
}