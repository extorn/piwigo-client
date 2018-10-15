package delit.piwigoclient.piwigoApi.handlers;

import android.support.annotation.NonNull;

import com.crashlytics.android.Crashlytics;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;

import delit.piwigoclient.model.piwigo.User;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class UserGetInfoResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "UsernamesListRspHdlr";
    private final String username;
    private final String userType;
    private final long userId;

    public UserGetInfoResponseHandler(long userId) {
        super("pwg.users.getList", TAG);
        this.userId = userId;
        this.username = null;
        this.userType = null;
    }

    public UserGetInfoResponseHandler(@NonNull String username, @NonNull String userType) {
        super("pwg.users.getList", TAG);
        this.userId = -1;
        this.username = username;
        this.userType = userType;
    }

    public static ArrayList<User> parseUsersFromJson(JsonArray usersObj) throws JSONException {
        ArrayList<User> users = new ArrayList<>(usersObj.size());

        SimpleDateFormat piwigoDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.UK);

        for (int i = 0; i < usersObj.size(); i++) {
            JsonObject userObj = usersObj.get(i).getAsJsonObject();
            User user = parseUserFromJson(userObj, piwigoDateFormat);
            users.add(user);
        }
        return users;
    }

    public static User parseUserFromJson(JsonObject userObj, SimpleDateFormat piwigoDateFormat) throws JSONException {
        long id = userObj.get("id").getAsLong();
        String username = userObj.get("username").getAsString();
        String userType = userObj.get("status").getAsString();
        int privacyLevel = userObj.get("level").getAsInt();
        JsonElement emailJsonElem = userObj.get("email");
        String email = null;
        if (emailJsonElem != null && !emailJsonElem.isJsonNull()) {
            email = emailJsonElem.getAsString();
        }
        boolean highDefEnabled = false;
        if (userObj.has("enabled_high")) {
            highDefEnabled = userObj.get("enabled_high").getAsBoolean();
        }
        Date lastVisitDate = null;
        if (userObj.has("last_visit") && !userObj.get("last_visit").isJsonNull()) {
            String lastVisitDateStr = userObj.get("last_visit").getAsString();
            if (lastVisitDateStr != null) {
                try {
                    lastVisitDate = piwigoDateFormat.parse(lastVisitDateStr);
                } catch (ParseException e) {
                    Crashlytics.logException(e);
                    throw new JSONException("Unable to parse date " + lastVisitDateStr);
                }
            }
        }
        JsonArray groupsArr = userObj.get("groups").getAsJsonArray();
        HashSet<Long> groups = new HashSet<>(groupsArr.size());
        for (int j = 0; j < groupsArr.size(); j++) {
            long groupId = groupsArr.get(j).getAsLong();
            groups.add(groupId);
        }

        User user = new User(id, username, userType, privacyLevel, email, highDefEnabled, lastVisitDate);
        user.setGroups(groups);

        return user;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        if(userId > 0) {
            params.put("userId", userId);
        } else {
            params.put("username", username);
            params.put("status", userType);
        }
        params.put("page", "0");
        params.put("per_page", "5");
        params.put("display", "username,email,status,level,groups,enabled_high,last_visit"); // useful information for the app
        params.put("order", "last_visit");
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
        ArrayList<User> users = parseUsersFromJson(usersObj);
        PiwigoGetUserDetailsResponse r = new PiwigoGetUserDetailsResponse(getMessageId(), getPiwigoMethod(), page, pageSize, itemsOnPage, users);
        storeResponse(r);
    }

    public static class PiwigoGetUserDetailsResponse extends UsersGetListResponseHandler.PiwigoGetUsersListResponse {

        private final User selectedUser;

        public PiwigoGetUserDetailsResponse(long messageId, String piwigoMethod, int page, int pageSize, int itemsOnPage, ArrayList<User> users) {
            super(messageId, piwigoMethod, page, pageSize, itemsOnPage, users);
            selectedUser = getUsers().remove(0);
        }

        public User getSelectedUser() {
            return selectedUser;
        }
    }
}