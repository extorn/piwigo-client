package delit.piwigoclient.piwigoApi.handlers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class GroupsGetListResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "GroupsListRspHdlr";
    private final long page;
    private final long pageSize;
    private final Set<Long> groupIds;

    public GroupsGetListResponseHandler(Set<Long> groupIds, long page, long pageSize) {
        super("pwg.groups.getList", TAG);
        this.page = page;
        this.pageSize = pageSize;
        this.groupIds = groupIds;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("page", String.valueOf(page));
        params.put("per_page", String.valueOf(pageSize));
        if(groupIds != null) {
            for (Long groupId : groupIds) {
                params.add("group_id[]", String.valueOf(groupId));
            }
        }
        params.put("order", "name");
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {
        JSONObject result = rsp.getJSONObject("result");
        JSONObject pagingObj = result.getJSONObject("paging");
        int page = pagingObj.getInt("page");
        int pageSize = pagingObj.getInt("per_page");
        int itemsOnPage = pagingObj.getInt("count");
        JSONArray groupsObj = result.getJSONArray("groups");
        HashSet<Group> groups = parseGroupsFromJson(groupsObj);
        PiwigoResponseBufferingHandler.PiwigoGetGroupsListRetrievedResponse r = new PiwigoResponseBufferingHandler.PiwigoGetGroupsListRetrievedResponse(getMessageId(), getPiwigoMethod(), page, pageSize, itemsOnPage, groups);
        storeResponse(r);
    }

    public static HashSet<Group> parseGroupsFromJson(JSONArray groupsObj) throws JSONException {
        HashSet<Group> groups = new HashSet<>(groupsObj.length());
        for (int i = 0; i < groupsObj.length(); i++) {
            JSONObject groupObj = groupsObj.getJSONObject(i);
            Group g = parseGroupFromJson(groupObj);
            groups.add(g);
        }
        return groups;
    }

    public static Group parseGroupFromJson(JSONObject groupObj) throws JSONException {
        long id = groupObj.getLong("id");
        String name = groupObj.getString("name");
        boolean isDefault = groupObj.getBoolean("is_default");
        int memberCount = groupObj.getInt("nb_users");
        return new Group(id, name, isDefault, memberCount);
    }
}