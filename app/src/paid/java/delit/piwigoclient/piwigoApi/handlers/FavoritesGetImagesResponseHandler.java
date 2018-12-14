package delit.piwigoclient.piwigoApi.handlers;

public class FavoritesGetImagesResponseHandler extends ImagesGetResponseHandler {

    public FavoritesGetImagesResponseHandler(String sortOrder, int page, int pageSize, String multimediaExtensionList) {
        super(null, sortOrder, page, pageSize, multimediaExtensionList);
    }

    @Override
    public String getPiwigoMethod() {
        return getPiwigoMethodOverrideIfPossible("piwigo_client.favorites.getImages");
    }
}
