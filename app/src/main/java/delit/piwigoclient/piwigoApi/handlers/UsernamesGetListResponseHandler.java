package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import delit.piwigoclient.model.piwigo.Username;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class UsernamesGetListResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "UsernamesListRspHdlr";
    private final long page;
    private final long pageSize;
    private final Collection<Long> groupIds;
    private final Collection<Long> userIds;

    public UsernamesGetListResponseHandler(Collection<Long> groupIds, long page, long pageSize) {
        super("pwg.users.getList", TAG);
        this.groupIds = groupIds;
        this.userIds = null;
        this.page = page;
        this.pageSize = pageSize;
    }

    public UsernamesGetListResponseHandler(Collection<Long> userIds) {
        super("pwg.users.getList", TAG);
        this.userIds = userIds;
        this.groupIds = null;
        page = 0;
        pageSize = userIds.size();
    }

    public UsernamesGetListResponseHandler(long page, long pageSize) {
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
        params.put("page", String.valueOf(page));
        params.put("per_page", String.valueOf(pageSize));
        params.put("display", "username,status"); // username and user type
        if(groupIds != null) {
            for (Long groupId : groupIds) {
                params.add("group_id[]", String.valueOf(groupId));
            }
        }
        if(userIds != null) {
            for (Long userId : userIds) {
                params.add("user_id[]", String.valueOf(userId));
            }
        }
        params.put("order", "username"); // user name
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
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
        PiwigoResponseBufferingHandler.PiwigoGetUsernamesListResponse r = new PiwigoResponseBufferingHandler.PiwigoGetUsernamesListResponse(getMessageId(), getPiwigoMethod(), page, pageSize, itemsOnPage, usernames);
        storeResponse(r);
    }
}