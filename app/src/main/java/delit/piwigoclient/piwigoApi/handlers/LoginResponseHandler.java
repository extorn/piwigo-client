package delit.piwigoclient.piwigoApi.handlers;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.gson.JsonElement;
import com.loopj.android.http.AsyncHttpResponseHandler;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONException;

import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.User;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.CachingAsyncHttpClient;
import delit.piwigoclient.piwigoApi.http.RequestHandle;
import delit.piwigoclient.piwigoApi.http.RequestParams;
import delit.piwigoclient.ui.events.UserNotUniqueWarningEvent;

public class LoginResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "LoginRspHdlr";
    private final String password;
    private final String username;
    private boolean loginSuccess;

    public LoginResponseHandler(String username, String password) {
        super("pwg.session.login", TAG);
        this.username = username;
        this.password = password;
    }

    public LoginResponseHandler(Context context) {
        super("pwg.session.login", TAG);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        this.password = ConnectionPreferences.getPiwigoPassword(prefs, getContext());
        this.username = ConnectionPreferences.getPiwigoUsername(prefs, getContext());
    }

    public LoginResponseHandler(String password, Context context) {
        super("pwg.session.login", TAG);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        this.password = password;
        this.username = ConnectionPreferences.getPiwigoUsername(prefs, getContext());
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("username", username);
        params.put("password", password);
        return params;
    }

    @Override
    public RequestHandle runCall(CachingAsyncHttpClient client, AsyncHttpResponseHandler handler) {
        if ((PiwigoSessionDetails.getInstance() != null && !PiwigoSessionDetails.getInstance().isSessionMayHaveExpired() && PiwigoSessionDetails.isLoggedIn()) || username == null || username.trim().isEmpty()) {
            onLogin();
            return null;
        } else {
            return super.runCall(client, handler);
        }
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        onLogin();
    }

    private void onLogin() {
        loginSuccess = true;
        PiwigoResponseBufferingHandler.PiwigoOnLoginResponse r = new PiwigoResponseBufferingHandler.PiwigoOnLoginResponse(getMessageId(), getPiwigoMethod());
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance();
        if(sessionDetails == null || sessionDetails.isSessionMayHaveExpired() || !PiwigoSessionDetails.isLoggedInWithSessionDetails()) {
            GetSessionStatusResponseHandler sessionLoadHandler = new GetSessionStatusResponseHandler();
            runAndWaitForHandlerToFinish(sessionLoadHandler);
            if(PiwigoSessionDetails.isLoggedInWithSessionDetails()
                    && sessionLoadHandler.getResponse() instanceof PiwigoResponseBufferingHandler.PiwigoSessionStatusRetrievedResponse) {

                PiwigoSessionDetails oldCredentials = ((PiwigoResponseBufferingHandler.PiwigoSessionStatusRetrievedResponse)sessionLoadHandler.getResponse()).getOldCredentials();

                r.setSessionRetrieved();
                r.setOldCredentials(oldCredentials);

            } else {
                reportNestedFailure(sessionLoadHandler);
            }
        }
        if(PiwigoSessionDetails.isLoggedInWithSessionDetails()) {
            if(!PiwigoSessionDetails.isFullyLoggedIn()) {
                loadUserDetails();
            }
            if(PiwigoSessionDetails.isFullyLoggedIn()) {
                r.setUserDetailsRetrieved();
            }
        }
        storeResponse(r);
    }

    private void loadUserDetails() {
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance();
        UserGetInfoResponseHandler userInfoHandler = new UserGetInfoResponseHandler(sessionDetails.getUsername(), sessionDetails.getUserType());
        runAndWaitForHandlerToFinish(userInfoHandler);
        if (userInfoHandler.isSuccess()) {
            PiwigoResponseBufferingHandler.PiwigoGetUserDetailsResponse response = (PiwigoResponseBufferingHandler.PiwigoGetUserDetailsResponse) userInfoHandler.getResponse();
            User userDetails = response.getSelectedUser();
            if (response.getUsers().size() > 0) {
                EventBus.getDefault().post(new UserNotUniqueWarningEvent(userDetails, response.getUsers()));
            }
            sessionDetails.setUserDetails(userDetails);
        } else {
            reportNestedFailure(userInfoHandler);
        }
    }

    public boolean isLoginSuccess() {
        return loginSuccess;
    }
}
