package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.HashSet;

import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class GroupAddResponseHandler<T extends ResourceItem> extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "AddGrpRspHdlr";
    private final Group group;

    public GroupAddResponseHandler(Group group) {
        super("pwg.groups.add", TAG);
        this.group = group;
    }

    @Override
    public RequestParams buildRequestParameters() {

        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("name", group.getName());
        params.put("is_default", String.valueOf(group.isDefault()));
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();
        JsonArray groupsObj = result.get("groups").getAsJsonArray();
        HashSet<Group> groups = GroupsGetListResponseHandler.parseGroupsFromJson(groupsObj);
        if(groups.size() != 1) {
            throw new JSONException("Expected one group to be returned, but there were " + groups.size());
        }
        PiwigoResponseBufferingHandler.PiwigoAddGroupResponse r = new PiwigoResponseBufferingHandler.PiwigoAddGroupResponse(getMessageId(), getPiwigoMethod(), groups.iterator().next());
        storeResponse(r);
    }

}