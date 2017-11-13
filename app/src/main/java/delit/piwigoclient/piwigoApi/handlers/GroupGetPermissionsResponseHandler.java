package delit.piwigoclient.piwigoApi.handlers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class GroupGetPermissionsResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "GetGrpPermRspHdlr";
    private HashSet<Long> groupIds;

    public GroupGetPermissionsResponseHandler(HashSet<Long> groupIds) {
        super("pwg.permissions.getList", TAG);
        this.groupIds = groupIds;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        if(groupIds.size() > 0) {
            for(Long groupId : groupIds) {
                params.add("group_id[]", groupId.toString());
            }
        } else {
            throw new IllegalArgumentException("At least one groupId must be provided");
        }
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {
        JSONObject result = rsp.getJSONObject("result");
        JSONArray cats = result.getJSONArray("categories");
        HashSet<Long> allowedAlbums = new HashSet<>(cats.length());
        for (int i = 0; i < cats.length(); i++) {
            JSONObject cat = (JSONObject) cats.get(i);

            boolean found = false;
            JSONArray allowedGroups = cat.getJSONArray("groups");
            for (int j = 0; j < allowedGroups.length(); j++) {
                if (groupIds.contains(allowedGroups.getLong(j))) {
                    found = true;
                    break;
                }
            }
            if (found) {
                long category = (long) cat.getLong("id");
                allowedAlbums.add(category);
            }
        }

        PiwigoResponseBufferingHandler.PiwigoGroupPermissionsRetrievedResponse r = new PiwigoResponseBufferingHandler.PiwigoGroupPermissionsRetrievedResponse(getMessageId(), getPiwigoMethod(), groupIds, allowedAlbums);
        storeResponse(r);
    }


}