package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonObject;

import org.json.JSONException;

import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;

public class AlbumGetImagesResponseHandler extends AlbumGetImagesBasicResponseHandler {

    public AlbumGetImagesResponseHandler(CategoryItem parentAlbum, String sortOrder, int page, int pageSize) {
        super(parentAlbum, sortOrder, page, pageSize);
    }

    @Override
    protected ResourceParser buildResourceParser(String basePiwigoUrl) {
        boolean defaultVal = Boolean.TRUE.equals(PiwigoSessionDetails.getInstance(getConnectionPrefs()).isUsingPiwigoPrivacyPlugin());
        boolean isApplyPrivacyPluginUriFix = getConnectionPrefs().isFixPiwigoPrivacyPluginMediaUris(getSharedPrefs(), getContext(), defaultVal);
        return new ResourceParser(basePiwigoUrl, isApplyPrivacyPluginUriFix);
    }

    public static class ResourceParser extends BasicCategoryImageResourceParser {

        public ResourceParser(String basePiwigoUrl, boolean usePrivacyPluginFix) {
            super(basePiwigoUrl, usePrivacyPluginFix);
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