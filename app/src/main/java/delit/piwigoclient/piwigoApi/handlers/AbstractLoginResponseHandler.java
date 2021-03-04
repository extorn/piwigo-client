package delit.piwigoclient.piwigoApi.handlers;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.google.gson.JsonElement;
import com.loopj.android.http.AsyncHttpResponseHandler;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONException;

import java.net.UnknownHostException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import delit.libs.core.util.Logging;
import delit.libs.http.RequestParams;
import delit.libs.http.cache.CachingAsyncHttpClient;
import delit.libs.http.cache.RequestHandle;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.User;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.ui.events.BlockingUserInteractionQuestion;
import delit.piwigoclient.ui.events.ServerConfigErrorEvent;
import delit.piwigoclient.ui.events.UserNotUniqueWarningEvent;

public class AbstractLoginResponseHandler<T extends AbstractLoginResponseHandler<T>> extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "LoginRspHdlr";
    private String password = null;
    private boolean haveValidSessionKey;
    private boolean acceptCachedResponse;

    public AbstractLoginResponseHandler() {
        super("n/a", TAG);
        setPerformingLogin();
    }

    public AbstractLoginResponseHandler(String password) {
        super(null, TAG);
        this.password = password;
        setPerformingLogin();
    }

    public T withCachedResponsesAllowed(boolean acceptCachedResponse) {
        this.acceptCachedResponse = acceptCachedResponse;
        return (T)this;
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
    public RequestHandle runCall(CachingAsyncHttpClient client, AsyncHttpResponseHandler handler, boolean forceResponseRevalidation) {

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
            canContinue = getNewSessionDetails();
        }

        loginResponse.setNewSessionDetails(PiwigoSessionDetails.getInstance(connectionPrefs));

        if(canContinue) {
            loadMethodsAvailable();
        }

        ThreadPoolExecutor executor = new ThreadPoolExecutor(5, 5, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        if(canContinue) {

            if (PiwigoSessionDetails.getInstance(connectionPrefs).isCommunityPluginInstalled()) {
                executor.execute(()->retrieveCommunityPluginSession(PiwigoSessionDetails.getInstance(connectionPrefs)));
            } else {
                PiwigoSessionDetails instance = getPiwigoSessionDetails();
                if (instance != null) {
                    instance.setUseCommunityPlugin(false);
                } else {
                    Bundle b = new Bundle();
                    b.putString("location", "LoginCode");
                    Logging.logAnalyticEvent(getContext(),"SessionNull", b);
                }
            }
            if (isNeedUserDetails(PiwigoSessionDetails.getInstance(connectionPrefs))) {
                executor.execute(this::loadUserDetails);
            }
            executor.execute(this::loadGalleryConfig);
            executor.execute(()->loadActiveServerPlugins(getContext(), connectionPrefs));


            PiwigoSessionDetails sessionDetails = getPiwigoSessionDetails();
            if (sessionDetails.isMethodAvailable(PiwigoClientGetPluginDetailResponseHandler.WS_METHOD_NAME)) {
                sessionDetails.setPiwigoClientPluginVersion("1.0.5");
                executor.execute(this::loadPiwigoClientPluginDetails);
            } else {
                sessionDetails.setPiwigoClientPluginVersion("1.0.4");
            }
            performExtraServerCalls(getContext(), connectionPrefs, executor);

            // shut the executor waiting for the server calls to finish.
            executor.shutdown();
            try {
                if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException ex) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        setRequestURI(getNestedRequestURI());
        setError(getNestedFailure());
        storeResponse(loginResponse);

        if(canContinue) {
            // this is needed because we aren't calling the onSuccess method.
            resetFailureAsASuccess();
        }

        return null;
    }

    protected void performExtraServerCalls(@NonNull Context context, @NonNull ConnectionPreferences.ProfilePreferences connectionPrefs, ThreadPoolExecutor executor) {
    }

    private boolean loadGalleryConfig() {
        GalleryGetConfigResponseHandler handler = new GalleryGetConfigResponseHandler();
        PiwigoSessionDetails sessionDetails = getPiwigoSessionDetails();
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

    protected boolean retrieveCommunityPluginSession(PiwigoSessionDetails newCredentials) {
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
            Throwable error = newSessionKeyHandler.getError();
            if (error != null && error.getCause() instanceof UnknownHostException) {
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

    private boolean getNewSessionDetails() {
        GetSessionStatusResponseHandler sessionLoadHandler = new GetSessionStatusResponseHandler();
        sessionLoadHandler.setPerformingLogin(); // need this otherwise it will go recursive getting another login session
        sessionLoadHandler.invokeAndWait(getContext(), getConnectionPrefs());
        if (!sessionLoadHandler.isSuccess()) {
            reportNestedFailure(sessionLoadHandler);
            return false;
        }
        return true;
    }

    private boolean isOnPiwigoComSite() {
        return getPiwigoServerUrl().toLowerCase().matches("https://[^.]*\\.piwigo\\.com[/]?");
    }

    private boolean loadUserDetails() {
        boolean canContinue;
        PiwigoSessionDetails sessionDetails = getPiwigoSessionDetails();
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
            canContinue = isOnPiwigoComSite(); // this particular site hides the community plugin api calls.
            PiwigoResponseBufferingHandler.ErrorResponse response = (PiwigoResponseBufferingHandler.ErrorResponse) userInfoHandler.getResponse();
            if((response instanceof PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse && ((PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) response).getStatusCode() == 401)
             && sessionDetails.isAdminUser()) {
                Logging.logAnalyticEvent(getContext(),"UserDowngrade_General", null);
                sessionDetails.updateUserType("general");
                if (!canContinue) {
                    EventBus.getDefault().post(new ServerConfigErrorEvent(getContext().getString(R.string.admin_user_missing_admin_permissions_after_login_warning_pattern, userInfoHandler.getPiwigoServerUrl())));
                } else {
                    sessionDetails.setUseCommunityPlugin(true);
                }
            }
            if (!canContinue) {
                reportNestedFailure(userInfoHandler);
            }
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

    private boolean loadActiveServerPlugins(Context context, ConnectionPreferences.ProfilePreferences connectionPrefs) {
        PiwigoSessionDetails sessionDetails = getPiwigoSessionDetails();
        if(sessionDetails.isAdminUser()) {
            ServerAdminGetPluginsResponseHandler pluginsResponseHandler = new ServerAdminGetPluginsResponseHandler();
            pluginsResponseHandler.setPerformingLogin(); // need this otherwise it will go recursive getting another login session
            pluginsResponseHandler.invokeAndWait(context, connectionPrefs);
            if (pluginsResponseHandler.isSuccess()) {
                ServerAdminGetPluginsResponseHandler.ServerPluginListResponse response = (ServerAdminGetPluginsResponseHandler.ServerPluginListResponse) pluginsResponseHandler.getResponse();
                sessionDetails.setActiveServerPlugins(response.getActivePlugins());
                return true;
            }
            return false;
        }
        return true;
    }

}
