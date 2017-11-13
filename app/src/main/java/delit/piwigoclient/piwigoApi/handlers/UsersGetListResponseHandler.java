package delit.piwigoclient.piwigoApi.handlers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;

import delit.piwigoclient.model.piwigo.User;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class UsersGetListResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "UsernamesListRspHdlr";
    private final long pageSize;
    private final long page;

    public UsersGetListResponseHandler(long page, long pageSize) {
        super("pwg.users.getList", TAG);
        this.page = page;
        this.pageSize = pageSize;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("page", String.valueOf(page));
        params.put("per_page", String.valueOf(pageSize));
        params.put("display", "username,email,status,level,groups,enabled_high,last_visit"); // useful information for the app
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
        ArrayList<User> users = parseUsersFromJson(usersObj);
        PiwigoResponseBufferingHandler.PiwigoGetUsersListResponse r = new PiwigoResponseBufferingHandler.PiwigoGetUsersListResponse(getMessageId(), getPiwigoMethod(), page, pageSize, itemsOnPage, users);
        storeResponse(r);
    }

    public static ArrayList<User> parseUsersFromJson(JSONArray usersObj) throws JSONException {
        ArrayList<User> users = new ArrayList<>(usersObj.length());

        SimpleDateFormat piwigoDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (int i = 0; i < usersObj.length(); i++) {
            JSONObject userObj = usersObj.getJSONObject(i);
            User user = parseUserFromJson(userObj, piwigoDateFormat);
            users.add(user);
        }
        return users;
    }

    public static User parseUserFromJson(JSONObject userObj, SimpleDateFormat piwigoDateFormat) throws JSONException {
        long id = userObj.getLong("id");
        String username = userObj.getString("username");
        String userType = userObj.getString("status");
        int privacyLevel = userObj.getInt("level");
        String email = userObj.getString("email");
        if ("null".equals(email)) {
            email = null;
        }
        boolean highDefEnabled = false;
        if(userObj.has("enabled_high")) {
            highDefEnabled = userObj.getBoolean("enabled_high");
        }
        Date lastVisitDate = null;
        if(userObj.has("last_visit")) {
            String lastVisitDateStr = userObj.getString("last_visit");
            if (lastVisitDateStr != null) {
                try {
                    lastVisitDate = piwigoDateFormat.parse(lastVisitDateStr);
                } catch (ParseException e) {
                    throw new JSONException("Unable to parse date " + lastVisitDateStr);
                }
            }
        }
        JSONArray groupsArr = userObj.getJSONArray("groups");
        HashSet<Long> groups = new HashSet<>(groupsArr.length());
        for (int j = 0; j < groupsArr.length(); j++) {
            long groupId = groupsArr.getLong(j);
            groups.add(groupId);
        }

        User user = new User(id, username, userType, privacyLevel, email, highDefEnabled, lastVisitDate);
        user.setGroups(groups);

        return user;
    }

}