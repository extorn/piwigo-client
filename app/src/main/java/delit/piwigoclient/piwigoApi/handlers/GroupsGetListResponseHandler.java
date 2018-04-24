package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class GroupsGetListResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "GroupsListRspHdlr";
    private final long page;
    private final long pageSize;
    private final Set<Long> groupIds;

    public GroupsGetListResponseHandler(long page, long pageSize) {
        this(null, page, pageSize);
    }

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
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();
        JsonObject pagingObj = result.get("paging").getAsJsonObject();
        int page = pagingObj.get("page").getAsInt();
        int pageSize = pagingObj.get("per_page").getAsInt();
        int itemsOnPage = pagingObj.get("count").getAsInt();
        JsonArray groupsObj = result.get("groups").getAsJsonArray();
        HashSet<Group> groups = parseGroupsFromJson(groupsObj);
        PiwigoResponseBufferingHandler.PiwigoGetGroupsListRetrievedResponse r = new PiwigoResponseBufferingHandler.PiwigoGetGroupsListRetrievedResponse(getMessageId(), getPiwigoMethod(), page, pageSize, itemsOnPage, groups);
        storeResponse(r);
    }

    public static HashSet<Group> parseGroupsFromJson(JsonArray groupsObj) throws JSONException {
        HashSet<Group> groups = new LinkedHashSet<>(groupsObj.size());
        for (int i = 0; i < groupsObj.size(); i++) {
            JsonObject groupObj = groupsObj.get(i).getAsJsonObject();
            Group g = parseGroupFromJson(groupObj);
            groups.add(g);
        }
        return groups;
    }

    private static Group parseGroupFromJson(JsonObject groupObj) throws JSONException {
        long id = groupObj.get("id").getAsLong();
        String name = groupObj.get("name").getAsString();
        boolean isDefault = groupObj.get("is_default").getAsBoolean();
        int memberCount = groupObj.get("nb_users").getAsInt();
        return new Group(id, name, isDefault, memberCount);
    }
}