package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;

import org.json.JSONException;

import java.util.HashSet;

import delit.libs.http.RequestParams;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.User;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class UserUpdateInfoResponseHandler<T extends ResourceItem> extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "UpdateUserInfoRspHdlr";
    private final User user;

    public UserUpdateInfoResponseHandler(User user) {
        super("pwg.users.setInfo", TAG);
        this.user = user;
    }

    @Override
    public RequestParams buildRequestParameters() {

        //TODO this will give an unusual error if the user is not logged in.... better way?

        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("user_id", String.valueOf(user.getId()));
        params.put("username", user.getUsername());
        if (user.getPassword() != null) {
            params.put("password", user.getPassword());
        }
        params.put("email", user.getEmail());
        params.put("status", user.getUserType());
        params.put("level", String.valueOf(user.getPrivacyLevel()));
        params.put("enabled_high", String.valueOf(user.isHighDefinitionEnabled()));
        if (user.getGroups() != null && user.getGroups().size() > 0) {
            for (Long groupId : user.getGroups()) {
                params.add("group_id[]", String.valueOf(groupId));
            }
        } else {
            // clear all groups (special API flag).
            params.add("group_id[]", String.valueOf(-1));
        }
        params.put("pwg_token", getPwgSessionToken());
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        if (user.getGroups() == null) {
            user.setGroups(new HashSet<>(0));
        }
        PiwigoUpdateUserInfoResponse r = new PiwigoUpdateUserInfoResponse(getMessageId(), getPiwigoMethod(), user, isCached);
        storeResponse(r);
    }

    public static class PiwigoUpdateUserInfoResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private final User user;

        public PiwigoUpdateUserInfoResponse(long messageId, String piwigoMethod, User user, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.user = user;
        }

        public User getUser() {
            return user;
        }
    }
}