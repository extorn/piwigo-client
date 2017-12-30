package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;

import org.json.JSONException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;

import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.PiwigoAlbumAdminList;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class AlbumGetSubAlbumsAdminResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "GetSubAlbumsAdminRspHdlr";

    public AlbumGetSubAlbumsAdminResponseHandler() {
        super("pwg.categories.getAdminList", TAG);
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();
        JsonArray categories = result.get("categories").getAsJsonArray();

        PiwigoAlbumAdminList adminList = new PiwigoAlbumAdminList();

        for (int i = 0; i < categories.size(); i++) {
            JsonObject category = (JsonObject) categories.get(i);
            long id = category.get("id").getAsLong();
            String name = category.get("name").getAsString();
            long photos = category.get("nb_images").getAsLong();
            String description = category.get("comment").getAsString();
            boolean isPublic = "public".equals(category.get("status").getAsString());

            CategoryItem item = new CategoryItem(id, name, description, !isPublic, null, photos, photos, 0, null);

            String[] parentage = category.get("uppercats").getAsString().split(",");
            ArrayList<Long> parentageChain = new ArrayList<>(parentage.length);
            // Add root category first (all are children of root)
            parentageChain.add(0L);
            for(String parentId : parentage) {
                parentageChain.add(Long.valueOf(parentId));
            }
            // remove this album from parentage list
            parentageChain.remove(parentageChain.size() -1);
            item.setParentageChain(parentageChain);
            adminList.addItem(item);
        }
        // Now lets update the total photo count for all albums so it includes that of the children.
        adminList.updateTotalPhotosAndSubAlbumCount();

        PiwigoResponseBufferingHandler.PiwigoGetSubAlbumsAdminResponse r = new PiwigoResponseBufferingHandler.PiwigoGetSubAlbumsAdminResponse(getMessageId(), getPiwigoMethod(), adminList);
        storeResponse(r);
    }

}
