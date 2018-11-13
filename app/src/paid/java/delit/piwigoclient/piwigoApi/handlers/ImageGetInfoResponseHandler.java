package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.HashSet;

import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.Tag;

/**
 * Created by gareth on 07/04/18.
 */

public class ImageGetInfoResponseHandler<T extends ResourceItem> extends BaseImageGetInfoResponseHandler<T> {
    public ImageGetInfoResponseHandler(T piwigoResource, String multimediaExtensionList) {
        super(piwigoResource, multimediaExtensionList);
    }

    protected BaseImagesGetResponseHandler.BasicCategoryImageResourceParser buildResourceParser(String multimediaExtensionList, boolean usingPiwigoClientOveride) {
        return new ImageGetInfoResourceParser(multimediaExtensionList, usingPiwigoClientOveride);
    }

    public static class ImageGetInfoResourceParser extends BaseImageGetInfoResourceParser {

        public ImageGetInfoResourceParser(String multimediaExtensionList, boolean usingPiwigoClientOveride) {
            super(multimediaExtensionList, usingPiwigoClientOveride);
        }

        @Override
        public ResourceItem parseAndProcessResourceData(JsonObject image) throws JSONException {
            ResourceItem resourceItem = super.parseAndProcessResourceData(image);

            JsonArray tagsElem = image.get("tags").getAsJsonArray();
            HashSet<Tag> tags = TagsGetListResponseHandler.parseTagsFromJson(tagsElem);
            resourceItem.setTags(tags);

            return resourceItem;
        }
    }

    public boolean isUseHttpGet() {
        return true;
    }
}
