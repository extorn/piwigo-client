package delit.piwigoclient.piwigoApi.handlers;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.ArrayList;

import delit.libs.core.util.Logging;
import delit.libs.http.RequestParams;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.PiwigoAlbumAdminList;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class AlbumGetChildAlbumsAdminResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "GetSubAlbumsAdminRspHdlr";

    public AlbumGetChildAlbumsAdminResponseHandler() {
        super("pwg.categories.getAdminList", TAG);
    }

    @Override
    public String getPiwigoMethod() {
        return getPiwigoMethodOverrideIfPossible("piwigo_client.categories.getAdminList");
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
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
            item.markAsAdminCopy();

            String[] parentage = category.get("uppercats").getAsString().split(",");
            ArrayList<Long> parentageChain = new ArrayList<>(parentage.length);
            // Add root category first (all are children of root)
            parentageChain.add(0L);
            for (String parentId : parentage) {
                try {
                    parentageChain.add(Long.valueOf(parentId));
                } catch(NumberFormatException e) {
                    Logging.log(Log.ERROR, "getAdminAlbums", "parentId is invalid in response : " + rsp.toString());
                }
            }
            // remove this album from parentage list
            parentageChain.remove(parentageChain.size() - 1);
            item.setParentageChain(parentageChain);
            adminList.addItem(item);
        }
        // Now lets update the total photo count for all albums so it includes that of the children.
        adminList.updateTotalPhotosAndSubAlbumCount();

        PiwigoGetSubAlbumsAdminResponse r = new PiwigoGetSubAlbumsAdminResponse(getMessageId(), getPiwigoMethod(), adminList, isCached);
        storeResponse(r);
    }

    public static class PiwigoGetSubAlbumsAdminResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        final PiwigoAlbumAdminList adminList;

        public PiwigoGetSubAlbumsAdminResponse(long messageId, String piwigoMethod, PiwigoAlbumAdminList adminList, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.adminList = adminList;
        }

        public PiwigoAlbumAdminList getAdminList() {
            return adminList;
        }
    }

    public boolean isUseHttpGet() {
        return true;
    }
}
