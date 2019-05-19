package delit.piwigoclient.piwigoApi.handlers;

import delit.piwigoclient.model.piwigo.CategoryItem;

public class ImagesGetResponseHandler extends BaseImagesGetResponseHandler {

    public ImagesGetResponseHandler(CategoryItem parentAlbum, String sortOrder, int page, int pageSize, String multimediaExtensionList) {
        super(parentAlbum, sortOrder, page, pageSize, multimediaExtensionList);
    }

    @Override
    protected ResourceParser buildResourceParser(String multimediaExtensionList, String basePiwigoUrl) {
        return new ResourceParser(multimediaExtensionList, basePiwigoUrl);
    }

    public static class ResourceParser extends BasicCategoryImageResourceParser {

        public ResourceParser(String multimediaExtensionList, String basePiwigoUrl) {
            super(multimediaExtensionList, basePiwigoUrl);
        }
    }
}