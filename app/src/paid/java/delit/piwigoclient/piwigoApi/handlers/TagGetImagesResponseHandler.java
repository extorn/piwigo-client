package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.ArrayList;

import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class TagGetImagesResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "TagGetResRspHdlr";
    private final String multimediaExtensionList;
    private final Tag tag;
    private final String sortOrder;
    private final int pageSize;
    private final int page;

    public TagGetImagesResponseHandler(Tag tag, String sortOrder, int page, int pageSize, String multimediaExtensionList) {
        super("pwg.tags.getImages", TAG);
        this.tag = tag;
        this.sortOrder = sortOrder;
        this.page = page;
        this.pageSize = pageSize;
        this.multimediaExtensionList = multimediaExtensionList;
    }

    @Override
    public String getPiwigoMethod() {
        return getPiwigoMethodOverrideIfPossible("piwigo_client.tags.getImages");
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("tag_id", String.valueOf(tag.getId()));
        params.put("order", sortOrder);
        params.put("page", String.valueOf(page));
        params.put("per_page", String.valueOf(pageSize));
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {

        ArrayList<GalleryItem> resources = new ArrayList<>();

        JsonObject result = rsp.getAsJsonObject();
        JsonArray images = result.get("images").getAsJsonArray();

        ImagesGetResponseHandler.ResourceParser resourceParser = new ImagesGetResponseHandler.ResourceParser(multimediaExtensionList);

        if(images != null) {
            for (int i = 0; i < images.size(); i++) {
                JsonObject image = (JsonObject) images.get(i);
                ResourceItem item = resourceParser.parseAndProcessResourceData(image);
                resources.add(item);

            }
        }

        BaseImagesGetResponseHandler.PiwigoGetResourcesResponse r = new BaseImagesGetResponseHandler.PiwigoGetResourcesResponse(getMessageId(), getPiwigoMethod(), page, pageSize, resources);
        storeResponse(r);
    }
}