package delit.piwigoclient.piwigoApi.handlers;

import com.loopj.android.http.AsyncHttpResponseHandler;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONException;
import org.json.JSONObject;

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
        if (PiwigoSessionDetails.isLoggedIn() || username == null || username.trim().isEmpty()) {
            onLogin();
            return null;
        } else {
            return super.runCall(client, handler);
        }
    }

    @Override
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {
        onLogin();
    }

    public void onLogin() {
        loginSuccess = true;
        PiwigoResponseBufferingHandler.PiwigoOnLoginResponse r = new PiwigoResponseBufferingHandler.PiwigoOnLoginResponse(getMessageId(), getPiwigoMethod());
        if(!PiwigoSessionDetails.isLoggedInWithSessionDetails()) {
            GetSessionStatusResponseHandler sessionLoadHandler = new GetSessionStatusResponseHandler();
            runAndWaitForHandlerToFinish(sessionLoadHandler);
            if(PiwigoSessionDetails.isLoggedInWithSessionDetails()) {
                r.setSessionRetrieved();
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
        }
    }

    private void runAndWaitForHandlerToFinish(AbstractPiwigoWsResponseHandler handler) {
        handler.setCallDetails(getContext(), getPiwigoServerUrl(), !getUseSynchronousMode());
        handler.setPublishResponses(false);
        handler.runCall();
        while(handler.isRunning()) {
            if(isCancelCallAsap()) {
                handler.cancelCallAsap();
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                handler.cancelCallAsap();
            }
        }
    }

    public boolean isLoginSuccess() {
        return loginSuccess;
    }
}
