package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.Tag;

/**
 * Created by gareth on 07/04/18.
 */

public class ImageGetInfoResponseHandler<T extends ResourceItem> extends BaseImageGetInfoResponseHandler<T> {
    public ImageGetInfoResponseHandler(T piwigoResource) {
        super(piwigoResource);
    }

    protected AlbumGetImagesBasicResponseHandler.BasicCategoryImageResourceParser buildResourceParser(boolean usingPiwigoClientOveride) {
        boolean defaultVal = Boolean.TRUE.equals(getPiwigoSessionDetails().isUsingPiwigoPrivacyPlugin());
        boolean isApplyPrivacyPluginUriFix = getConnectionPrefs().isFixPiwigoPrivacyPluginMediaUris(getSharedPrefs(), getContext(), defaultVal);
        PiwigoSessionDetails sessionDetails = getPiwigoSessionDetails();
        List<String> piwigoSites = null;
        if(sessionDetails.getServerConfig() != null) {
            piwigoSites = sessionDetails.getServerConfig().getSites();
        }
        return new ImageGetInfoResourceParser(getPiwigoServerUrl(), piwigoSites, isApplyPrivacyPluginUriFix, usingPiwigoClientOveride);
    }

    public static class ImageGetInfoResourceParser extends BaseImageGetInfoResourceParser {

        public ImageGetInfoResourceParser(String basePiwigoUrl, List<String> piwigoSites, Boolean usingPiwigoPrivacyPlugin, boolean usingPiwigoClientOveride) {
            super(basePiwigoUrl, piwigoSites, usingPiwigoPrivacyPlugin, usingPiwigoClientOveride);
        }

        @Override
        public ResourceItem parseAndProcessResourceData(JsonObject image) throws JSONException {
            ResourceItem resourceItem = super.parseAndProcessResourceData(image);

            JsonArray tagsElem = image.get("tags").getAsJsonArray();
            ArrayList<Tag> tags = TagsGetListResponseHandler.parseTagsFromJson(tagsElem);
            resourceItem.setTags(new LinkedHashSet<>(tags));

            Boolean isFavorite = null;
            JsonElement favoriteJsonElem = image.get("isFavorite");
            if (favoriteJsonElem != null && !favoriteJsonElem.isJsonNull()) {
                isFavorite = favoriteJsonElem.getAsBoolean();
            }

            if(isFavorite != null) {
                resourceItem.setFavorite(isFavorite);
            }

            return resourceItem;
        }
    }

    public boolean isUseHttpGet() {
        return true;
    }
}
