package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.Set;

import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.ResourceItem;

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

        @Override
        public ResourceItem parseAndProcessResourceData(JsonObject image) throws JSONException {
            ResourceItem item = super.parseAndProcessResourceData(image);

//            Boolean isFavorite = null;
//            JsonElement favoriteJsonElem = image.get("isFavorite");
//            if (favoriteJsonElem != null && !favoriteJsonElem.isJsonNull()) {
//                isFavorite = favoriteJsonElem.getAsBoolean();
//            }
//
//            if(isFavorite != null) {
//                item.setFavorite(isFavorite);
//            }

            return item;
        }
    }

    public boolean isUseHttpGet() {
        return true;
    }
}