package delit.piwigoclient.piwigoApi.handlers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {
        JSONObject result = rsp.getJSONObject("result");
        JSONArray cats = result.getJSONArray("categories");
        if (cats.length() > 1) {
            throw new JSONException("Expected details for exactly one category to be returned, but got " + cats.length());
        }

        long[] groups;
        long[] users;
        if(cats.length() == 0) {
            // no privacy settings exist yet for this album.
            groups = new long[0];
            users = new long[0];
        } else {
            JSONObject cat = (JSONObject) cats.get(0);
            if (cat.getLong("id") != album.getId()) {
                throw new JSONException("Expected details for category requested, but details for a different category were returned by the server");
            }
            JSONArray usersArr = cat.getJSONArray("users");
            users = new long[usersArr.length()];
            for (int i = 0; i < usersArr.length(); i++) {
                long user = usersArr.getLong(i);
                users[i] = user;
            }

            JSONArray groupsArr = cat.getJSONArray("groups");
            groups = new long[groupsArr.length()];
            for (int i = 0; i < groupsArr.length(); i++) {
                long group = groupsArr.getLong(i);
                groups[i] = group;
            }
        }
        album.setUsers(users);
        album.setGroups(groups);

        PiwigoResponseBufferingHandler.PiwigoAlbumPermissionsRetrievedResponse r = new PiwigoResponseBufferingHandler.PiwigoAlbumPermissionsRetrievedResponse(getMessageId(), getPiwigoMethod(), album);
        storeResponse(r);
    }
}