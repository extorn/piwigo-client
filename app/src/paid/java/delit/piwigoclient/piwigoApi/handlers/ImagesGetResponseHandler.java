package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonObject;

import org.json.JSONException;

import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.ResourceItem;

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