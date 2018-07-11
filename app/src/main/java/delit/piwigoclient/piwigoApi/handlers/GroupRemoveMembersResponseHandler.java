package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashSet;

import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class GroupRemoveMembersResponseHandler<T extends ResourceItem> extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "RemGrpMembersRspHdlr";
    private final long groupId;
    private final ArrayList<Long> groupMemberIdsToRemove;

    public GroupRemoveMembersResponseHandler(long groupId, ArrayList<Long> groupMemberIdsToRemove) {
        super("pwg.groups.deleteUser", TAG);
        this.groupId = groupId;
        this.groupMemberIdsToRemove = groupMemberIdsToRemove;
        if (groupMemberIdsToRemove == null || groupMemberIdsToRemove.size() == 0) {
            throw new IllegalArgumentException("Group must be having at least one member removed");
        }
    }

    @Override
    public RequestParams buildRequestParameters() {
        String sessionToken = "";
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(getConnectionPrefs());
        if (sessionDetails != null && sessionDetails.isLoggedInWithFullSessionDetails()) {
            sessionToken = sessionDetails.getSessionToken();
        }
        //TODO this will give an unusual error if the user is not logged in.... better way?

        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("group_id", String.valueOf(groupId));
        for (Long albumId : groupMemberIdsToRemove) {
            params.add("user_id[]", String.valueOf(albumId));
        }
        params.put("pwg_token", sessionToken);
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();
        JsonArray groupsObj = result.get("groups").getAsJsonArray();
        HashSet<Group> groups = GroupsGetListResponseHandler.parseGroupsFromJson(groupsObj);
        if (groups.size() != 1) {
            throw new JSONException("Expected one group to be returned, but there were " + groups.size());
        }
        //Ensure we remove the group from the current logged in user's session details (so we don't need to retrieve them again).
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(getConnectionPrefs());
        long currentUsersId = sessionDetails.getUserId();
        if (groupMemberIdsToRemove.contains(currentUsersId)) {
            HashSet<Long> currentUsersGroupMemberships = sessionDetails.getGroupMemberships();
            currentUsersGroupMemberships.remove(groupId);
        }
        PiwigoResponseBufferingHandler.PiwigoGroupRemoveMembersResponse r = new PiwigoResponseBufferingHandler.PiwigoGroupRemoveMembersResponse(getMessageId(), getPiwigoMethod(), groups.iterator().next());
        storeResponse(r);
    }

}