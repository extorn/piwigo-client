package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collection;

import delit.piwigoclient.model.piwigo.PagedList;
import delit.piwigoclient.model.piwigo.Username;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class UsernamesGetListResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "UsernamesListRspHdlr";
    private final int page;
    private final int pageSize;
    private final Collection<Long> groupIds;
    private final Collection<Long> userIds;

    public UsernamesGetListResponseHandler(Collection<Long> groupIds, int page, int pageSize) {
        super("pwg.users.getList", TAG);
        this.groupIds = groupIds;
        this.page = page;
        this.pageSize = pageSize;
        this.userIds = null;
    }

    public UsernamesGetListResponseHandler(Collection<Long> userIds) {
        super("pwg.users.getList", TAG);
        this.userIds = userIds;
        this.groupIds = null;
        page = PagedList.MISSING_ITEMS_PAGE;
        pageSize = userIds.size();
    }

    public UsernamesGetListResponseHandler(int page, int pageSize) {
        super("pwg.users.getList", TAG);
        this.groupIds = null;
        this.userIds = null;
        this.page = page;
        this.pageSize = pageSize;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("page", page == PagedList.MISSING_ITEMS_PAGE ? "0" : String.valueOf(page));
        params.put("per_page", String.valueOf(pageSize));
        params.put("display", "username,status"); // username and user type
        if (groupIds != null) {
            for (Long groupId : groupIds) {
                params.add("group_id[]", String.valueOf(groupId));
            }
        }
        if (userIds != null) {
            for (Long userId : userIds) {
                params.add("user_id[]", String.valueOf(userId));
            }
        }
        params.put("order", "username"); // user name
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();
        JsonObject pagingObj = result.get("paging").getAsJsonObject();
        int page = pagingObj.get("page").getAsInt();
        int pageSize = pagingObj.get("per_page").getAsInt();
        int itemsOnPage = pagingObj.get("count").getAsInt();
        JsonArray usersObj = result.get("users").getAsJsonArray();
        ArrayList<Username> usernames = new ArrayList<>(usersObj.size());
        for (int i = 0; i < usersObj.size(); i++) {
            JsonObject userObj = usersObj.get(i).getAsJsonObject();
            long id = userObj.get("id").getAsLong();
            String username = userObj.get("username").getAsString();
            String userType = userObj.get("status").getAsString();
            usernames.add(new Username(id, username, userType));
        }
        if (this.page == PagedList.MISSING_ITEMS_PAGE) {
            page = this.page;
        }
        PiwigoGetUsernamesListResponse r = new PiwigoGetUsernamesListResponse(getMessageId(), getPiwigoMethod(), page, pageSize, itemsOnPage, usernames, isCached);
        storeResponse(r);
    }

    public static class PiwigoGetUsernamesListResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private final ArrayList<Username> usernames;
        private final int itemsOnPage;
        private final int pageSize;
        private final int page;

        public PiwigoGetUsernamesListResponse(long messageId, String piwigoMethod, int page, int pageSize, int itemsOnPage, ArrayList<Username> usernames, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.page = page;
            this.pageSize = pageSize;
            this.itemsOnPage = itemsOnPage;
            this.usernames = usernames;
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

        public ArrayList<Username> getUsernames() {
            return usernames;
        }
    }

    public boolean isUseHttpGet() {
        return true;
    }
}