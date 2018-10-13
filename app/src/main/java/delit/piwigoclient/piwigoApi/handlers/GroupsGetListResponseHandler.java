package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.model.piwigo.PagedList;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class GroupsGetListResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "GroupsListRspHdlr";
    private final int page;
    private final int pageSize;
    private final Set<Long> groupIds;

    public GroupsGetListResponseHandler(Collection<Long> groupIds) {
        super("pwg.groups.getList", TAG);
        this.groupIds = null;
        page = PagedList.MISSING_ITEMS_PAGE;
        pageSize = groupIds.size();
    }

    public GroupsGetListResponseHandler(int page, int pageSize) {
        super("pwg.groups.getList", TAG);
        this.groupIds = null;
        this.page = page;
        this.pageSize = pageSize;
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

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("page", page == PagedList.MISSING_ITEMS_PAGE ? "0" : String.valueOf(page));
        params.put("per_page", String.valueOf(pageSize));
        if (groupIds != null) {
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
        if (this.page == PagedList.MISSING_ITEMS_PAGE) {
            page = this.page;
        }
        PiwigoGetGroupsListRetrievedResponse r = new PiwigoGetGroupsListRetrievedResponse(getMessageId(), getPiwigoMethod(), page, pageSize, itemsOnPage, groups);
        storeResponse(r);
    }

    public static class PiwigoGetGroupsListRetrievedResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private final HashSet<Group> groups;
        private final int itemsOnPage;
        private final int pageSize;
        private final int page;

        public PiwigoGetGroupsListRetrievedResponse(long messageId, String piwigoMethod, int page, int pageSize, int itemsOnPage, HashSet<Group> groups) {
            super(messageId, piwigoMethod, true);
            this.page = page;
            this.pageSize = pageSize;
            this.itemsOnPage = itemsOnPage;
            this.groups = groups;
        }

        public int getItemsOnPage() {
            return itemsOnPage;
        }

        public int getPage() {
            return page;
        }

        public int getPageSize() {
            return pageSize;
        }

        public HashSet<Group> getGroups() {
            return groups;
        }
    }
}