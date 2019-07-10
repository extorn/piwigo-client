package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.HashSet;

import delit.libs.http.RequestParams;
import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class GroupUpdateInfoResponseHandler<T extends ResourceItem> extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "UpdateGrpInfoRspHdlr";
    private final Group originalGroup;
    private final Group newGroup;

    public GroupUpdateInfoResponseHandler(Group originalGroup, Group newGroup) {
        super("pwg.groups.setInfo", TAG);
        this.originalGroup = originalGroup;
        this.newGroup = newGroup;
    }

    @Override
    public RequestParams buildRequestParameters() {

        //TODO this will give an unusual error if the user is not logged in.... better way?

        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("group_id", String.valueOf(newGroup.getId()));
        if (!newGroup.getName().equals(originalGroup.getName())) {
            params.put("name", newGroup.getName());
        }
        if (newGroup.isDefault() != originalGroup.isDefault()) {
            params.put("is_default", String.valueOf(newGroup.isDefault()));
        }
        params.put("pwg_token", getPwgSessionToken());
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();
        JsonArray groupsObj = result.get("groups").getAsJsonArray();
        HashSet<Group> groups = GroupsGetListResponseHandler.parseGroupsFromJson(groupsObj);
        if (groups.size() != 1) {
            throw new JSONException("Expected one group to be returned, but there were " + groups.size());
        }
        PiwigoGroupUpdateInfoResponse r = new PiwigoGroupUpdateInfoResponse(getMessageId(), getPiwigoMethod(), groups.iterator().next(), isCached);
        storeResponse(r);
    }

    public static class PiwigoGroupUpdateInfoResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private final Group group;

        public PiwigoGroupUpdateInfoResponse(long messageId, String piwigoMethod, Group group, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.group = group;
        }

        public Group getGroup() {
            return group;
        }
    }
}