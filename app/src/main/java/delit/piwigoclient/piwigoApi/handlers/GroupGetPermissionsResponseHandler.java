package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.HashSet;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class GroupGetPermissionsResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "GetGrpPermRspHdlr";
    private final HashSet<Long> groupIds;

    public GroupGetPermissionsResponseHandler(Long groupId) {
        super("pwg.permissions.getList", TAG);
        HashSet<Long> groupIds = new HashSet<>(1);
        groupIds.add(groupId);
        this.groupIds = groupIds;
    }

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
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();
        JsonArray cats = result.get("categories").getAsJsonArray();
        HashSet<Long> allowedAlbums = new HashSet<>(cats.size());
        for (int i = 0; i < cats.size(); i++) {
            JsonObject cat = (JsonObject) cats.get(i);

            boolean found = false;
            JsonArray allowedGroups = cat.get("groups").getAsJsonArray();
            for (int j = 0; j < allowedGroups.size(); j++) {
                if (groupIds.contains(allowedGroups.get(j).getAsLong())) {
                    found = true;
                    break;
                }
            }
            if (found) {
                long category = cat.get("id").getAsLong();
                allowedAlbums.add(category);
            }
        }

        PiwigoResponseBufferingHandler.PiwigoGroupPermissionsRetrievedResponse r = new PiwigoResponseBufferingHandler.PiwigoGroupPermissionsRetrievedResponse(getMessageId(), getPiwigoMethod(), groupIds, allowedAlbums);
        storeResponse(r);
    }


}