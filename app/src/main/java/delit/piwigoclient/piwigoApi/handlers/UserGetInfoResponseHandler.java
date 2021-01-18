package delit.piwigoclient.piwigoApi.handlers;

import android.os.Bundle;

import androidx.annotation.NonNull;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;

import delit.libs.core.util.Logging;
import delit.libs.http.RequestParams;
import delit.piwigoclient.model.piwigo.User;

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

        for (int i = 0; i < usersObj.size(); i++) {
            JsonObject userObj = usersObj.get(i).getAsJsonObject();
            User user = parseUserFromJson(userObj);
            users.add(user);
        }
        return users;
    }

    public static User parseUserFromJson(JsonObject userObj) throws JSONException {
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
                    lastVisitDate = parsePiwigoServerDate(lastVisitDateStr);
                } catch (ParseException e) {
                    Logging.recordException(e);
                    throw new JSONException("Unable to parse date " + lastVisitDateStr);
                }
            }
        }
        HashSet<Long> groups;
        if(userObj.get("groups").isJsonArray()) {
            JsonArray groupsArr = userObj.get("groups").getAsJsonArray();
            groups = new HashSet<>(groupsArr.size());
            for (int j = 0; j < groupsArr.size(); j++) {
                long groupId = groupsArr.get(j).getAsLong();
                groups.add(groupId);
            }
        } else {
            groups = new HashSet<>(0);
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
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();
        JsonObject pagingObj = result.get("paging").getAsJsonObject();
        int page = pagingObj.get("page").getAsInt();
        int pageSize = pagingObj.get("per_page").getAsInt();
        int itemsOnPage = pagingObj.get("count").getAsInt();
        JsonArray usersObj = result.get("users").getAsJsonArray();
        ArrayList<User> users = parseUsersFromJson(usersObj);
        if (users.size() == 0) {
            Bundle bundle = new Bundle();
            if (userId >= 0) {
                bundle.putLong("userId", userId);
            } else {
                bundle.putString("username", username);
                bundle.putString("status", userType);
            }
            FirebaseAnalytics.getInstance(getContext()).logEvent("noUserFound", bundle);
        }
        PiwigoGetUserDetailsResponse r = new PiwigoGetUserDetailsResponse(getMessageId(), getPiwigoMethod(), page, pageSize, itemsOnPage, users, isCached);
        storeResponse(r);
    }

    public static class PiwigoGetUserDetailsResponse extends UsersGetListResponseHandler.PiwigoGetUsersListResponse {

        private final User selectedUser;

        public PiwigoGetUserDetailsResponse(long messageId, String piwigoMethod, int page, int pageSize, int itemsOnPage, ArrayList<User> users, boolean isCached) {
            super(messageId, piwigoMethod, page, pageSize, itemsOnPage, users, isCached);
            if (!getUsers().isEmpty()) {
                selectedUser = getUsers().remove(0);
            } else {
                selectedUser = null;
            }
        }

        public User getSelectedUser() {
            return selectedUser;
        }
    }

    public boolean isUseHttpGet() {
        return true;
    }
}