package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

import delit.libs.http.RequestParams;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.Tag;

public class TagGetImagesResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "TagGetResRspHdlr";
    private final Tag tag;
    private final String sortOrder;
    private final int pageSize;
    private final int page;

    public TagGetImagesResponseHandler(Tag tag, String sortOrder, int page, int pageSize) {
        super("pwg.tags.getImages", TAG);
        this.tag = tag;
        this.sortOrder = sortOrder;
        this.page = page;
        this.pageSize = pageSize;
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
        if(!"server".equals(sortOrder)) {
            params.put("order", sortOrder);
        }
        params.put("page", String.valueOf(page));
        params.put("per_page", String.valueOf(pageSize));
        params.put("pwg_token", getPwgSessionToken());
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {

        ArrayList<GalleryItem> resources = new ArrayList<>();

        JsonObject result = rsp.getAsJsonObject();

        JsonObject pagingObj = result.get("paging").getAsJsonObject();
        int page = pagingObj.get("page").getAsInt();
        int pageSize = pagingObj.get("per_page").getAsInt();
        int totalResourceCount = pagingObj.get("total_count").getAsInt();

        JsonArray images = result.get("images").getAsJsonArray();

        AlbumGetImagesResponseHandler.ResourceParser resourceParser = buildResourceParser();

        if(images != null) {
            for (int i = 0; i < images.size(); i++) {
                JsonObject image = (JsonObject) images.get(i);
                ResourceItem item = resourceParser.parseAndProcessResourceData(image);
                resources.add(item);

            }
        }

        AlbumGetImagesBasicResponseHandler.PiwigoGetResourcesResponse r = new AlbumGetImagesBasicResponseHandler.PiwigoGetResourcesResponse(getMessageId(), getPiwigoMethod(), page, pageSize, totalResourceCount, resources, isCached);
        storeResponse(r);
    }

    private AlbumGetImagesResponseHandler.ResourceParser buildResourceParser() {
        boolean defaultVal = Boolean.TRUE.equals(getPiwigoSessionDetails().isUsingPiwigoPrivacyPlugin());
        boolean isApplyPrivacyPluginUriFix = getConnectionPrefs().isFixPiwigoPrivacyPluginMediaUris(getSharedPrefs(), getContext(), defaultVal);
        PiwigoSessionDetails sessionDetails = getPiwigoSessionDetails();
        List<String> piwigoSites = null;
        if(sessionDetails.getServerConfig() != null) {
            piwigoSites = sessionDetails.getServerConfig().getSites();
        }
        return new AlbumGetImagesResponseHandler.ResourceParser(getPiwigoServerUrl(), piwigoSites, isApplyPrivacyPluginUriFix);
    }

    public boolean isUseHttpGet() {
        return true;
    }
}