package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class AlbumGetPermissionsResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "GetGalPermRspHdlr";
    private CategoryItem album;

    public AlbumGetPermissionsResponseHandler(CategoryItem album) {
        super("pwg.permissions.getList", TAG);
        this.album = album;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("cat_id", String.valueOf(album.getId()));
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();
        JsonArray cats = result.get("categories").getAsJsonArray();
        if (cats.size() > 1) {
            throw new JSONException("Expected details for exactly one category to be returned, but got " + cats.size());
        }

        long[] groups;
        long[] users;
        if(cats.size() == 0) {
            // no privacy settings exist yet for this album.
            groups = new long[0];
            users = new long[0];
        } else {
            JsonObject cat = (JsonObject) cats.get(0);
            if (cat.get("id").getAsLong() != album.getId()) {
                throw new JSONException("Expected details for category requested, but details for a different category were returned by the server");
            }
            JsonArray usersArr = cat.get("users").getAsJsonArray();
            users = new long[usersArr.size()];
            for (int i = 0; i < usersArr.size(); i++) {
                long user = usersArr.get(i).getAsLong();
                users[i] = user;
            }

            JsonArray groupsArr = cat.get("groups").getAsJsonArray();
            groups = new long[groupsArr.size()];
            for (int i = 0; i < groupsArr.size(); i++) {
                long group = groupsArr.get(i).getAsLong();
                groups[i] = group;
            }
        }
        album.setUsers(users);
        album.setGroups(groups);

        PiwigoResponseBufferingHandler.PiwigoAlbumPermissionsRetrievedResponse r = new PiwigoResponseBufferingHandler.PiwigoAlbumPermissionsRetrievedResponse(getMessageId(), getPiwigoMethod(), album);
        storeResponse(r);
    }
}