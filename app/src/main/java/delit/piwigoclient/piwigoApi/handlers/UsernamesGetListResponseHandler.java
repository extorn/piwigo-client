package delit.piwigoclient.piwigoApi.handlers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import delit.piwigoclient.model.piwigo.Username;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class UsernamesGetListResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "UsernamesListRspHdlr";
    private final long page;
    private final long pageSize;
    private final List<Long> groupIds;

    public UsernamesGetListResponseHandler(List<Long> groupIds, long page, long pageSize) {
        super("pwg.users.getList", TAG);
        this.groupIds = groupIds;
        this.page = page;
        this.pageSize = pageSize;
    }

    public UsernamesGetListResponseHandler(long page, long pageSize) {
        super("pwg.users.getList", TAG);
        this.groupIds = null;
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
        params.put("order", "username"); // user name
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {
        JSONObject result = rsp.getJSONObject("result");
        JSONObject pagingObj = result.getJSONObject("paging");
        int page = pagingObj.getInt("page");
        int pageSize = pagingObj.getInt("per_page");
        int itemsOnPage = pagingObj.getInt("count");
        JSONArray usersObj = result.getJSONArray("users");
        ArrayList<Username> usernames = new ArrayList<>(usersObj.length());
        for (int i = 0; i < usersObj.length(); i++) {
            JSONObject userObj = usersObj.getJSONObject(i);
            long id = userObj.getLong("id");
            String username = userObj.getString("username");
            String userType = userObj.getString("status");
            usernames.add(new Username(id, username, userType));
        }
        PiwigoResponseBufferingHandler.PiwigoGetUsernamesListResponse r = new PiwigoResponseBufferingHandler.PiwigoGetUsernamesListResponse(getMessageId(), getPiwigoMethod(), page, pageSize, itemsOnPage, usernames);
        storeResponse(r);
    }
}