package delit.piwigoclient.piwigoApi.handlers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;

import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class GroupAddMembersResponseHandler<T extends ResourceItem> extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "AddGrpMembersRspHdlr";
    private final long groupId;
    private final ArrayList<Long> newGroupMemberIds;

    public GroupAddMembersResponseHandler(long groupId, ArrayList<Long> newGroupMemberIds) {
        super("pwg.groups.addUser", TAG);
        this.groupId = groupId;
        this.newGroupMemberIds = newGroupMemberIds;
        if(newGroupMemberIds == null || newGroupMemberIds.size() == 0) {
            throw new IllegalArgumentException("Group must be being given at least one new member");
        }
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
        params.put("group_id", String.valueOf(groupId));
        for (Long albumId : newGroupMemberIds) {
            params.add("user_id[]", String.valueOf(albumId));
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
        //Ensure we add the group to the current logged in user's session details (so we don't need to retrieve them again).
        long currentUsersId = PiwigoSessionDetails.getInstance().getUserId();
        if(newGroupMemberIds.contains(currentUsersId)) {
            HashSet<Long> currentUsersGroupMemberships = PiwigoSessionDetails.getInstance().getGroupMemberships();
            currentUsersGroupMemberships.add(groupId);
        }
        PiwigoResponseBufferingHandler.PiwigoGroupAddMembersResponse r = new PiwigoResponseBufferingHandler.PiwigoGroupAddMembersResponse(getMessageId(), getPiwigoMethod(), groups.iterator().next());
        storeResponse(r);
    }

}