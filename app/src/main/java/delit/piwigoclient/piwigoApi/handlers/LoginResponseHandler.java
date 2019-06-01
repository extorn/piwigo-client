package delit.piwigoclient.piwigoApi.handlers;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.gson.JsonElement;
import com.loopj.android.http.AsyncHttpResponseHandler;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONException;

import java.net.UnknownHostException;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.User;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.CachingAsyncHttpClient;
import delit.piwigoclient.piwigoApi.http.RequestHandle;
import delit.piwigoclient.piwigoApi.http.RequestParams;
import delit.piwigoclient.ui.events.BlockingUserInteractionQuestion;
import delit.piwigoclient.ui.events.ServerConfigErrorEvent;
import delit.piwigoclient.ui.events.UserNotUniqueWarningEvent;

public class LoginResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "LoginRspHdlr";
    private String password = null;
    private boolean haveValidSessionKey;
    private boolean acceptCachedResponse;

    public LoginResponseHandler() {
        super("n/a", TAG);
        setPerformingLogin();
    }

    public LoginResponseHandler(String password) {
        super(null, TAG);
        this.password = password;
        setPerformingLogin();
    }

    public LoginResponseHandler withCachedResponsesAllowed(boolean acceptCachedResponse) {
        this.acceptCachedResponse = acceptCachedResponse;
        return this;
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

        PiwigoOnLoginResponse loginResponse = new PiwigoOnLoginResponse(getMessageId(), "UserLogin", false);
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

        if(canContinue && !PiwigoSessionDetails.getInstance(connectionPrefs).isMethodsAvailableListAvailable()) {
            canContinue = loadMethodsAvailable();
        }

        if (canContinue && PiwigoSessionDetails.getInstance(connectionPrefs).isCommunityPluginInstalled()) {
            canContinue = retrieveCommunityPluginSession(PiwigoSessionDetails.getInstance(connectionPrefs));
        } else {
            PiwigoSessionDetails instance = PiwigoSessionDetails.getInstance(connectionPrefs);
            if(instance != null) {
                instance.setUseCommunityPlugin(false);
            }
        }

        if (canContinue && isNeedUserDetails(PiwigoSessionDetails.getInstance(connectionPrefs))) {
            canContinue = loadUserDetails();
        }

        if(canContinue) {
            PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);
            if (sessionDetails.isMethodAvailable(PiwigoClientGetPluginDetailResponseHandler.WS_METHOD_NAME)) {
                sessionDetails.setPiwigoClientPluginVersion("1.0.5");
                canContinue = loadPiwigoClientPluginDetails();
            } else {
                sessionDetails.setPiwigoClientPluginVersion("1.0.4");
            }
        }

        if(canContinue) {
            loadGalleryConfig();
        }

        setError(getNestedFailure());
        storeResponse(loginResponse);

        if(canContinue) {
            // this is needed because we aren't calling the onSuccess method.
            resetFailureAsASuccess();
        }

        return null;
    }

    private boolean loadGalleryConfig() {
        GalleryGetConfigResponseHandler handler = new GalleryGetConfigResponseHandler();
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(getConnectionPrefs());
        if (sessionDetails.isMethodAvailable(handler.getPiwigoMethod())) {
            handler.setPerformingLogin();
            handler.invokeAndWait(getContext(), getConnectionPrefs());
            if (!handler.isSuccess()) {
                reportNestedFailure(handler);
                return false;
            } else {
                GalleryGetConfigResponseHandler.PiwigoGalleryGetConfigResponse response = (GalleryGetConfigResponseHandler.PiwigoGalleryGetConfigResponse) handler.getResponse();
                sessionDetails.setServerConfig(response.getServerConfig());
            }
        }
        // we don't need this information (not always available) so lets not fail the login.
        return true;
    }

    private boolean loadPiwigoClientPluginDetails() {
        PiwigoClientGetPluginDetailResponseHandler handler = new PiwigoClientGetPluginDetailResponseHandler();
        handler.setPerformingLogin();
        handler.invokeAndWait(getContext(), getConnectionPrefs());
        if (!handler.isSuccess()) {
            reportNestedFailure(handler);
            return false;
        }
        return true;
    }

    private boolean loadMethodsAvailable() {
        GetMethodsAvailableResponseHandler methodsHandler = new GetMethodsAvailableResponseHandler();
        methodsHandler.setPerformingLogin();
        methodsHandler.invokeAndWait(getContext(), getConnectionPrefs());
        if (!methodsHandler.isSuccess()) {
            reportNestedFailure(methodsHandler);
            return false;
        }
        return true;
    }

    private boolean retrieveCommunityPluginSession(PiwigoSessionDetails newCredentials) {
        //TODO forcing true will allow thumbnails to be made available (with extra call) for albums hidden to admin users.
        CommunitySessionStatusResponseHandler communitySessionLoadHandler = new CommunitySessionStatusResponseHandler(false);
        communitySessionLoadHandler.setPerformingLogin(); // need this otherwise it will go recursive getting another login session
        communitySessionLoadHandler.invokeAndWait(getContext(), getConnectionPrefs());
        if (!newCredentials.isLoggedInWithFullSessionDetails()) {
            reportNestedFailure(communitySessionLoadHandler);
        }
        return communitySessionLoadHandler.isSuccess();
    }

    private boolean getNewSessionKey(String password) {
        GetNewSessionKeyResponseHandler newSessionKeyHandler = new GetNewSessionKeyResponseHandler(password, getContext());
        newSessionKeyHandler.setPerformingLogin(); // need this otherwise it will go recursive getting another login session
        newSessionKeyHandler.invokeAndWait(getContext(), getConnectionPrefs());
        if (!newSessionKeyHandler.isSuccess()) {
            if (newSessionKeyHandler.getError().getCause() instanceof UnknownHostException) {
                if (acceptCachedResponse) {
                    return true;
                }
                BlockingUserInteractionQuestion userQuestion = new BlockingUserInteractionQuestion(R.string.switch_to_cached_mode);
                userQuestion.askQuestion();
                boolean userWantsCachedMode = userQuestion.getResponse();
                return userWantsCachedMode;
            }
            reportNestedFailure(newSessionKeyHandler);
            return false;
        }
        return true;
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
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
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
        sessionLoadHandler.invokeAndWait(getContext(), getConnectionPrefs());
        if (!sessionLoadHandler.isSuccess()) {
            reportNestedFailure(sessionLoadHandler);
            return false;
        }
        return true;
    }

    private boolean loadUserDetails() {
        boolean canContinue;
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(getConnectionPrefs());
        UserGetInfoResponseHandler userInfoHandler = new UserGetInfoResponseHandler(sessionDetails.getUsername(), sessionDetails.getUserType());
        userInfoHandler.setPerformingLogin(); // need this otherwise it will go recursive getting another login session
        userInfoHandler.invokeAndWait(getContext(), getConnectionPrefs());
        if (userInfoHandler.isSuccess()) {
            UserGetInfoResponseHandler.PiwigoGetUserDetailsResponse response = (UserGetInfoResponseHandler.PiwigoGetUserDetailsResponse) userInfoHandler.getResponse();
            User userDetails = response.getSelectedUser();
            if (response.getUsers().size() > 0) {
                EventBus.getDefault().post(new UserNotUniqueWarningEvent(userDetails, response.getUsers()));
            }
            sessionDetails.setUserDetails(userDetails);
            canContinue = true;
        } else {
            canContinue = false;
            PiwigoResponseBufferingHandler.ErrorResponse response = (PiwigoResponseBufferingHandler.ErrorResponse) userInfoHandler.getResponse();
            if((response instanceof PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse && ((PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) response).getStatusCode() == 401)
             && sessionDetails.isAdminUser()) {
                FirebaseAnalytics.getInstance(getContext()).logEvent("UserDowngrade_General", null);
                sessionDetails.updateUserType("general");
                EventBus.getDefault().post(new ServerConfigErrorEvent(getContext().getString(R.string.admin_user_missing_admin_permissions_after_login_warning_pattern, userInfoHandler.getPiwigoServerUrl())));
            }
            reportNestedFailure(userInfoHandler);
        }
        return canContinue;
    }

    public boolean isLoginSuccess() {
        return haveValidSessionKey;
    }

    public static class PiwigoOnLoginResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private PiwigoSessionDetails oldCredentials;
        private PiwigoSessionDetails sessionDetails;

        public PiwigoOnLoginResponse(long messageId, String piwigoMethod, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
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
