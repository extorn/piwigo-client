package delit.piwigoclient.piwigoApi.handlers;

import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

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
            JsonElement nameElem = category.get("name");
            String name = null;
            if (nameElem != null && !nameElem.isJsonNull()) {
                name = category.get("name").getAsString();
            }

            int photos = category.get("nb_images").getAsInt();

            JsonElement commentElem = category.get("comment");
            String description = null;
            if (commentElem != null && !commentElem.isJsonNull()) {
                description = commentElem.getAsString();
            }

            boolean isPublic = false;
            JsonElement statusElem = category.get("status");
            if(statusElem != null) {
                isPublic = "public".equals(statusElem.getAsString());
            }

            CategoryItem item = new CategoryItem(id, name, description, !isPublic, null, photos, photos, 0, null);

            String[] parentage = category.get("uppercats").getAsString().split(",");
            ArrayList<Long> parentageChain = new ArrayList<>(parentage.length);
            // Add root category first (all are children of root)
            parentageChain.add(0L);
            for (String parentId : parentage) {
                try {
                    parentageChain.add(Long.valueOf(parentId));
                } catch(NumberFormatException e) {
                    Crashlytics.log(Log.ERROR, "getAdminAlbums", "parentId is invalid in response : " + rsp.toString());
                }
            }
            // remove this album from parentage list
            parentageChain.remove(parentageChain.size() - 1);
            item.setParentageChain(parentageChain);
            adminList.addItem(item);
        }
        // Now lets update the total photo count for all albums so it includes that of the children.
        adminList.updateTotalPhotosAndSubAlbumCount();

        PiwigoResponseBufferingHandler.PiwigoGetSubAlbumsAdminResponse r = new PiwigoResponseBufferingHandler.PiwigoGetSubAlbumsAdminResponse(getMessageId(), getPiwigoMethod(), adminList);
        storeResponse(r);
    }

}
