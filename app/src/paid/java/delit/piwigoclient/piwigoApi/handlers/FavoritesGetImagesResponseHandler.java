package delit.piwigoclient.piwigoApi.handlers;

import java.util.Set;

public class FavoritesGetImagesResponseHandler extends ImagesGetResponseHandler {

    public FavoritesGetImagesResponseHandler(String sortOrder, int page, int pageSize, Set<String> multimediaExtensionList) {
        super(null, sortOrder, page, pageSize, multimediaExtensionList);
    }

    @Override
    public String getPiwigoMethod() {
        return getPiwigoMethodOverrideIfPossible("piwigo_client.favorites.getImages");
    }
}
