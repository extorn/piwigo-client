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

    @Override
    protected ResourceItem parseResourceItemFromJson(JsonObject resourceItemElem) throws JSONException {
        ResourceItem loadedResourceItem = super.parseResourceItemFromJson(resourceItemElem);

        JsonArray tagsElem = resourceItemElem.get("tags").getAsJsonArray();

        HashSet<Tag> tags = TagsGetListResponseHandler.parseTagsFromJson(tagsElem);

        loadedResourceItem.setTags(tags);

        return loadedResourceItem;
    }
}
