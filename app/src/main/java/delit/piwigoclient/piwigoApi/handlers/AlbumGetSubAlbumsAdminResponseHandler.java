package delit.piwigoclient.piwigoApi.handlers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.StringTokenizer;

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
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {
            JSONArray categories = rsp.getJSONObject("result").getJSONArray("categories");

        PiwigoAlbumAdminList adminList = new PiwigoAlbumAdminList();

        for (int i = 0; i < categories.length(); i++) {
            JSONObject category = (JSONObject) categories.get(i);
            long id = category.getLong("id");
            String name = category.getString("name");
            long photos = category.getLong("nb_images");
            String description = category.getString("comment");
            boolean isPublic = "public".equals(category.getString("status"));

            CategoryItem item = new CategoryItem(id, name, description, !isPublic, null, photos, photos, 0, null);

            String[] parentage = category.getString("uppercats").split(",");
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
