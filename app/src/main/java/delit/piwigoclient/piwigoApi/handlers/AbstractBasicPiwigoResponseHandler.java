package delit.piwigoclient.piwigoApi.handlers;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;

import com.loopj.android.http.AsyncHttpResponseHandler;

import org.greenrobot.eventbus.EventBus;

import java.net.SocketTimeoutException;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpStatus;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.HttpClientFactory;
import delit.piwigoclient.piwigoApi.http.CachingAsyncHttpClient;
import delit.piwigoclient.piwigoApi.http.RequestHandle;
import delit.piwigoclient.ui.MyApplication;
import delit.piwigoclient.ui.events.PiwigoLoginSuccessEvent;
import delit.piwigoclient.ui.preferences.SecurePrefsUtil;

/**
 * Created by gareth on 10/10/17.
 */

public abstract class AbstractBasicPiwigoResponseHandler extends AsyncHttpResponseHandler {
    private final boolean built;
    private boolean allowSessionRefreshAttempt;
    private String sessionToken;
    private boolean triedLoggingInAgain;
    private HttpClientFactory httpClientFactory;
    private boolean isSuccess;
    private boolean cancelCallAsap;
    private RequestHandle requestHandle;
    private String piwigoServerUrl;
    private boolean isRunning;
    private boolean rerunningCall;
    private Context context;
    private String tag;
    private Throwable error;
    private int statusCode;
    private Header[] headers;
    private byte[] responseBody;


    public AbstractBasicPiwigoResponseHandler(String tag) {
        this.tag = tag;
        setTag(tag);
        built = true;
    }

    @Override
    public String getTag() {
        return tag;
    }

    @Override
    protected final void handleMessage(Message message) {
        super.handleMessage(message);
        switch (message.what) {
            case FAILURE_MESSAGE:
            case SUCCESS_MESSAGE:
            case CANCEL_MESSAGE:
            case FINISH_MESSAGE:
                 postCall(isSuccess);
                if(rerunningCall) {
                    rerunningCall = false;
                }
                isRunning = false;
                break;
            default:
                Log.i(tag, "rx " + message.what);
        }
    }

    protected void postCall(boolean success) {
        // do nothing by default
    }

    @Override
    public final void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
        this.isSuccess = true;
        onSuccess(statusCode, headers, responseBody, triedLoggingInAgain);
    }

    protected void onSuccess(int statusCode, Header[] headers, byte[] responseBody, boolean hasBrandNewSession) {}

    protected void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error, boolean triedToGetNewSession) {}

    public void setCallDetails(Context parentContext, String piwigoServerUrl, boolean useAsyncMode) {
        setCallDetails(parentContext, piwigoServerUrl, useAsyncMode, true);
    }

    protected Context getContext() {
        return context;
    }

    public void setCallDetails(Context parentContext, String piwigoServerUrl, boolean useAsyncMode, boolean allowSessionRefreshAttempt) {
        clearCallDetails();
        this.context = parentContext;
        this.httpClientFactory = HttpClientFactory.getInstance(context);
        this.allowSessionRefreshAttempt = allowSessionRefreshAttempt;
        this.sessionToken = PiwigoSessionDetails.getActiveSessionToken();
        this.piwigoServerUrl = piwigoServerUrl;
        if(useAsyncMode && Looper.myLooper() == null) {
            // use a thread from the threadpool the request is sent using to handle the response
            setUsePoolThread(true);
        } else {
            super.setUseSynchronousMode(!useAsyncMode);
        }
    }

    @Override
    public void setUseSynchronousMode(boolean sync) {
        if(built && sync != getUseSynchronousMode()) {
            UnsupportedOperationException e = new UnsupportedOperationException("Use set call details instead");
            e.fillInStackTrace();
            throw e;
        }
    }

    public void clearCallDetails() {
        this.allowSessionRefreshAttempt = false;
        this.sessionToken = null;
        this.error = null;
        this.responseBody = null;
        this.headers = null;
        this.statusCode = -1;
        this.triedLoggingInAgain = false;
        this.httpClientFactory = null;
        this.isSuccess = false;
        this.cancelCallAsap = false;
        this.requestHandle = null;
    }


    @Override
    public final void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
        isSuccess = false;
        boolean tryingAgain = false;
        if (!cancelCallAsap && allowSessionRefreshAttempt &&
                ((statusCode == HttpStatus.SC_UNAUTHORIZED && !triedLoggingInAgain && error.getMessage().equalsIgnoreCase("Access denied"))
                || (error instanceof SocketTimeoutException))) {

            triedLoggingInAgain = true;
            // attempt login and resend original message.

            if(error instanceof SocketTimeoutException) {
                rerunCall();
            } else {
                synchronized (LoginResponseHandler.class) {
                    String newToken = PiwigoSessionDetails.getActiveSessionToken();
                    if (newToken != null && !newToken.equals(sessionToken)) {
                        sessionToken = newToken;
                        // ensure we ignore this error (if it errors again, we'll capture that one)
                        tryingAgain = true;
                        // just run the original call again (another thread has retrieved a new session
                        rerunCall();
                    } else if(!(PiwigoSessionDetails.isLoggedIn() && !PiwigoSessionDetails.isFullyLoggedIn())) {
                        // ensure we ignore this error (if it errors again, we'll capture that one)
                        tryingAgain = true;
                        // if we're not trying to get a new login at the moment. (otherwise this recurses).
                        getNewLogin();
                    }
                }

            }
        }
        if(!tryingAgain) {
            this.statusCode = statusCode;
            this.headers = headers;
            this.responseBody = responseBody;
            this.error = error;
            onFailure(statusCode, headers, responseBody, error, triedLoggingInAgain);
        }
    }

    protected void reportNestedFailure(AbstractBasicPiwigoResponseHandler nestedHandler) {
        onFailure(nestedHandler.statusCode, nestedHandler.headers, nestedHandler.responseBody, nestedHandler.error, triedLoggingInAgain);
    }

    protected void onGetNewSessionSuccess() {
        // Do nothing by default
    }

    protected void onGetNewSessionAndOrUserDetailsFailed() {
        // Do nothing by default.
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public Throwable getError() {
        return error;
    }

    private void rerunCall() {
        rerunningCall = true;
        runCall();
    }

    public final void runCall() {
        CachingAsyncHttpClient client = null;
        try {
            preRunCall();

            isRunning = true;
            if (getUseSynchronousMode()) {
                client = getHttpClientFactory().getSyncHttpClient();
            } else {
                client = getHttpClientFactory().getAsyncHttpClient();
            }
            if(client == null) {
                // unable to build a client from configuration properties.
                sendFailureMessage(-1, null, null, new IllegalArgumentException(MyApplication.getInstance().getString(R.string.error_server_configuration_invalid)));
            } else {
                requestHandle = runCall(client, this);
            }
        } catch(RuntimeException e) {
            if(client == null) {
                sendFailureMessage(-1, null, null, new IllegalStateException(MyApplication.getInstance().getString(R.string.error_building_http_engine), e));
            } else {
                sendFailureMessage(-1, null, null, e);
            }
        } finally {
            if(requestHandle == null) {
                isRunning = false;
            }
        }
    }

    protected void preRunCall() {
    }

    public abstract RequestHandle runCall(CachingAsyncHttpClient client, AsyncHttpResponseHandler handler);

    public HttpClientFactory getHttpClientFactory() {
        return httpClientFactory;
    }

    private void getNewLogin() {

        // send a server login request
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MyApplication.getInstance());

        piwigoServerUrl = ConnectionPreferences.getTrimmedNonNullPiwigoServerAddress(prefs, context);

        String username = ConnectionPreferences.getPiwigoUsername(prefs, context);
        String password = ConnectionPreferences.getPiwigoPassword(prefs, context);

        int loginStatus = 0;
        int newLoginStatus = 0;
        boolean exit = false;
        do {
            LoginResponseHandler handler = new LoginResponseHandler(username, password);
            runLoginHandler(handler);
            if (handler.isLoginSuccess()) {
                if (PiwigoSessionDetails.isFullyLoggedIn()) {
                    newLoginStatus = 3;
                    exit = true;
                    EventBus.getDefault().post(new PiwigoLoginSuccessEvent(false));
                    onGetNewSessionSuccess();
                    rerunCall();
                } else if (PiwigoSessionDetails.isLoggedInWithSessionDetails()) {
                    newLoginStatus = 2;
                } else if (PiwigoSessionDetails.isLoggedIn()) {
                    newLoginStatus = 1;
                }
            }
            if(newLoginStatus == loginStatus) {
                // no progression - fail call.
                exit = true;
                onGetNewSessionAndOrUserDetailsFailed();
            }
            loginStatus = newLoginStatus;
        } while(!exit);
    }

    private void runLoginHandler(LoginResponseHandler handler) {
        handler.setCallDetails(context, piwigoServerUrl, !getUseSynchronousMode());
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

    public boolean isCancelCallAsap() {
        return cancelCallAsap;
    }

    public void cancelCallAsap() {
        cancelCallAsap = true;
        if(requestHandle != null && !(requestHandle.isFinished() || requestHandle.isCancelled())) {
            boolean cancelled = requestHandle.cancel(true);
            if(cancelled) {
                sendFailureMessage(-1, null, null, new IllegalArgumentException(MyApplication.getInstance().getString(R.string.error_request_timed_out)));
            }
        }
    }

    public RequestHandle getRequestHandle() {
        return requestHandle;
    }

    protected String getPiwigoServerUrl() {
        return piwigoServerUrl;
    }

    public String getPiwigoWsApiUri() {
        return piwigoServerUrl + "/ws.php?format=json";
    }

    public boolean isRunning() {
        return isRunning;
    }
}
