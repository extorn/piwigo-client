package delit.piwigoclient.piwigoApi.handlers;

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
    private String password = null;
    private boolean haveValidSessionKey;

    public LoginResponseHandler() {
        super(null, TAG);
        setPerformingLogin();
    }

    public LoginResponseHandler(String password) {
        super(null, TAG);
        this.password = password;
        setPerformingLogin();
    }

    @Override
    public RequestParams buildRequestParameters() {
        return null;
    }

    @Override
    public String getPiwigoMethod() {
        return getNestedFailureMethod();
    }

    @Override
    public RequestHandle runCall(CachingAsyncHttpClient client, AsyncHttpResponseHandler handler) {

        ConnectionPreferences.ProfilePreferences connectionPrefs = getConnectionPrefs();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        PiwigoOnLoginResponse loginResponse = new PiwigoOnLoginResponse(getMessageId(), "UserLogin");
        loginResponse.setOldCredentials(PiwigoSessionDetails.getInstance(connectionPrefs));

        boolean canContinue = true;

        if (isSessionKeyInvalid(PiwigoSessionDetails.getInstance(connectionPrefs), prefs, connectionPrefs)) {
            canContinue = getNewSessionKey(password);
        }

        if (canContinue) {
            haveValidSessionKey = true;
        }

        if (canContinue && isSessionDetailsOutOfDate(PiwigoSessionDetails.getInstance(connectionPrefs))) {
            canContinue = getNewSessionDetails(loginResponse);
        }

        loginResponse.setNewSessionDetails(PiwigoSessionDetails.getInstance(connectionPrefs));

        if (canContinue && isCommunityPluginSessionStatusUnknown(PiwigoSessionDetails.getInstance(connectionPrefs))) {
            canContinue = retrieveCommunityPluginSession(PiwigoSessionDetails.getInstance(connectionPrefs));
        }

        if (canContinue && isNeedUserDetails(PiwigoSessionDetails.getInstance(connectionPrefs))) {
            canContinue = loadUserDetails();
        }

        setError(getNestedFailure());

        storeResponse(loginResponse);

        // this is needed because we aren't calling the onSuccess method.
        resetFailureAsASuccess();

        return null;
    }

    private boolean isCommunityPluginSessionStatusUnknown(PiwigoSessionDetails currentCredentials) {
        return currentCredentials != null && !currentCredentials.isSessionMayHaveExpired();
    }

    private boolean retrieveCommunityPluginSession(PiwigoSessionDetails newCredentials) {
        //TODO forcing true will allow thumbnails to be made available (with extra call) for albums hidden to admin users.
        CommunitySessionStatusResponseHandler communitySessionLoadHandler = new CommunitySessionStatusResponseHandler(false);
        communitySessionLoadHandler.setPerformingLogin(); // need this otherwise it will go recursive getting another login session
        communitySessionLoadHandler.run(getContext(), getConnectionPrefs());
        if (!newCredentials.isLoggedInWithFullSessionDetails()) {
            reportNestedFailure(communitySessionLoadHandler);
        }
        return communitySessionLoadHandler.isSuccess();
    }

    private boolean getNewSessionKey(String password) {
        GetNewSessionKeyResponseHandler newSessionKeyHandler = new GetNewSessionKeyResponseHandler(password, getContext());
        newSessionKeyHandler.setPerformingLogin(); // need this otherwise it will go recursive getting another login session
        newSessionKeyHandler.run(getContext(), getConnectionPrefs());
        if (!newSessionKeyHandler.isSuccess()) {
            reportNestedFailure(newSessionKeyHandler);
        }
        return newSessionKeyHandler.isSuccess();
    }

    private boolean isNeedUserDetails(PiwigoSessionDetails sessionDetails) {
        if (sessionDetails == null || !sessionDetails.isLoggedInWithFullSessionDetails()) {
            return false;
        }
        return !sessionDetails.isFullyLoggedIn();
    }

    private boolean isSessionDetailsOutOfDate(PiwigoSessionDetails sessionDetails) {
        return sessionDetails == null || sessionDetails.isSessionMayHaveExpired() || !sessionDetails.isLoggedInWithFullSessionDetails();
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        throw new UnsupportedOperationException("will never run");
    }

    private boolean isSessionKeyInvalid(PiwigoSessionDetails sessionDetails, SharedPreferences prefs, ConnectionPreferences.ProfilePreferences connectionPrefs) {
        String username = connectionPrefs.getPiwigoUsername(prefs, getContext());
        if (sessionDetails == null) {
            return username != null && !username.trim().isEmpty();
        } else {
            return sessionDetails.isSessionMayHaveExpired() || !sessionDetails.isLoggedIn();
        }
    }

    private boolean getNewSessionDetails(PiwigoOnLoginResponse loginResponse) {
        GetSessionStatusResponseHandler sessionLoadHandler = new GetSessionStatusResponseHandler();
        sessionLoadHandler.setPerformingLogin(); // need this otherwise it will go recursive getting another login session
        sessionLoadHandler.run(getContext(), getConnectionPrefs());
        PiwigoSessionDetails activeSessionDetails = PiwigoSessionDetails.getInstance(getConnectionPrefs());
        if (activeSessionDetails != null && activeSessionDetails.isLoggedInWithBasicSessionDetails() // get new copy (old may be invalid)
                && sessionLoadHandler.getResponse() instanceof GetSessionStatusResponseHandler.PiwigoSessionStatusRetrievedResponse) {
        } else {
            reportNestedFailure(sessionLoadHandler);
        }
        return sessionLoadHandler.isSuccess();
    }

    private boolean loadUserDetails() {
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(getConnectionPrefs());
        UserGetInfoResponseHandler userInfoHandler = new UserGetInfoResponseHandler(sessionDetails.getUsername(), sessionDetails.getUserType());
        userInfoHandler.setPerformingLogin(); // need this otherwise it will go recursive getting another login session
        userInfoHandler.run(getContext(), getConnectionPrefs());
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
        return userInfoHandler.isSuccess();
    }

    public boolean isLoginSuccess() {
        return haveValidSessionKey;
    }

    public static class PiwigoOnLoginResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private PiwigoSessionDetails oldCredentials;
        private PiwigoSessionDetails sessionDetails;

        public PiwigoOnLoginResponse(long messageId, String piwigoMethod) {
            super(messageId, piwigoMethod, true);
        }

        public PiwigoSessionDetails getOldCredentials() {
            return oldCredentials;
        }

        public void setOldCredentials(PiwigoSessionDetails oldCredentials) {
            this.oldCredentials = oldCredentials;
        }

        public PiwigoSessionDetails getNewSessionDetails() {
            return sessionDetails;
        }

        public void setNewSessionDetails(PiwigoSessionDetails sessionDetails) {
            this.sessionDetails = sessionDetails;
        }
    }
}
