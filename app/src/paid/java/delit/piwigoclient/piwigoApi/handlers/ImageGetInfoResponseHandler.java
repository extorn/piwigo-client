package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.HashSet;
import java.util.Set;

import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.Tag;

/**
 * Created by gareth on 07/04/18.
 */

public class ImageGetInfoResponseHandler<T extends ResourceItem> extends BaseImageGetInfoResponseHandler<T> {
    public ImageGetInfoResponseHandler(T piwigoResource, Set<String> multimediaExtensionList) {
        super(piwigoResource, multimediaExtensionList);
    }

    protected AlbumGetImagesBasicResponseHandler.BasicCategoryImageResourceParser buildResourceParser(Set<String> multimediaExtensionList, boolean usingPiwigoClientOveride) {
        return new ImageGetInfoResourceParser(multimediaExtensionList, getPiwigoServerUrl(), usingPiwigoClientOveride);
    }

    public static class ImageGetInfoResourceParser extends BaseImageGetInfoResourceParser {

        public ImageGetInfoResourceParser(Set<String> multimediaExtensionList, String basePiwigoUrl, boolean usingPiwigoClientOveride) {
            super(multimediaExtensionList, basePiwigoUrl, usingPiwigoClientOveride);
        }

        @Override
        public ResourceItem parseAndProcessResourceData(JsonObject image) throws JSONException {
            ResourceItem resourceItem = super.parseAndProcessResourceData(image);

            JsonArray tagsElem = image.get("tags").getAsJsonArray();
            HashSet<Tag> tags = TagsGetListResponseHandler.parseTagsFromJson(tagsElem);
            resourceItem.setTags(tags);

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
