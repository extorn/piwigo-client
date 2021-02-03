package delit.piwigoclient.piwigoApi.handlers;

import java.util.Set;

public class FavoritesGetImagesResponseHandler extends AlbumGetImagesResponseHandler {

    public FavoritesGetImagesResponseHandler(String sortOrder, int page, int pageSize) {
        super(null, sortOrder, page, pageSize);
    }

    @Override
    public String getPiwigoMethod() {
        return getPiwigoMethodOverrideIfPossible("piwigo_client.favorites.getImages");
    }
}
