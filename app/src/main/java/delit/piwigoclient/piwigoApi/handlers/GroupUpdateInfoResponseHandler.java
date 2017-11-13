package delit.piwigoclient.piwigoApi.handlers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;

import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

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
        String sessionToken = "";
        if(PiwigoSessionDetails.isLoggedInWithSessionDetails()) {
            sessionToken = PiwigoSessionDetails.getInstance().getSessionToken();
        }
        //TODO this will give an unusual error if the user is not logged in.... better way?

        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("group_id", String.valueOf(newGroup.getId()));
        if(!newGroup.getName().equals(originalGroup.getName())) {
            params.put("name", newGroup.getName());
        }
        if(newGroup.isDefault() != originalGroup.isDefault()) {
            params.put("is_default", String.valueOf(newGroup.isDefault()));
        }
        params.put("pwg_token", sessionToken);
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {
        JSONObject result = rsp.getJSONObject("result");
        JSONArray groupsObj = result.getJSONArray("groups");
        HashSet<Group> groups = GroupsGetListResponseHandler.parseGroupsFromJson(groupsObj);
        if(groups.size() != 1) {
            throw new JSONException("Expected one group to be returned, but there were " + groups.size());
        }
        PiwigoResponseBufferingHandler.PiwigoGroupUpdateInfoResponse r = new PiwigoResponseBufferingHandler.PiwigoGroupUpdateInfoResponse(getMessageId(), getPiwigoMethod(), groups.iterator().next());
        storeResponse(r);
    }

}