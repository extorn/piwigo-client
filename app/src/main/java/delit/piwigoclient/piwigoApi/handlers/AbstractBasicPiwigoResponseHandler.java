package delit.piwigoclient.piwigoApi.handlers;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;

import com.loopj.android.http.AsyncHttpResponseHandler;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.net.SocketTimeoutException;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpStatus;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.HttpClientFactory;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.CachingAsyncHttpClient;
import delit.piwigoclient.piwigoApi.http.RequestHandle;
import delit.piwigoclient.ui.MyApplication;
import delit.piwigoclient.ui.events.PiwigoLoginSuccessEvent;

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
    private final String tag;
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
                synchronized (this) {
                    notifyAll();
                }
                break;
            default:
                if(BuildConfig.DEBUG) {
                    Log.i(tag, "rx " + message.what);
                }
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
        if (!cancelCallAsap) {
            // attempt login and resend original message.
            if(error instanceof IOException && ("Unhandled exception: Cache has been shut down".equals(error.getMessage())
            || "Unhandled exception: Connection pool shut down".equals(error.getMessage()))) {
                tryingAgain = true;
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    // wait just a fraction of a second to give another cache or connection pool time to come up.
                }
                rerunCall();
            } else if(error instanceof SocketTimeoutException) {
                tryingAgain = true;
                rerunCall();
            } else if(error instanceof SSLException && error.getMessage() != null && error.getMessage().contains("Connection reset by peer")
                    || error instanceof SSLHandshakeException && error.getMessage() != null && error.getMessage().contains("I/O error during system call")) {
                tryingAgain = true;
                rerunCall();
            } else if(allowSessionRefreshAttempt
                    && (statusCode == HttpStatus.SC_UNAUTHORIZED && !triedLoggingInAgain && error == null || error.getMessage().equalsIgnoreCase("Access denied"))) {

                boolean newLoginAcquired = false;
                synchronized (AbstractBasicPiwigoResponseHandler.class) {
                    // Only one instance of this class can perform a login at a time - all others will wait for the outcome
                    triedLoggingInAgain = true;
                    String newToken = PiwigoSessionDetails.getActiveSessionToken();
                    if (newToken != null && !newToken.equals(sessionToken)) {
                        newLoginAcquired = true;
                    } else if(!(PiwigoSessionDetails.isLoggedIn() && !PiwigoSessionDetails.isFullyLoggedIn())) {
                        // if we're not trying to get a new login at the moment. (otherwise this recurses).

                        // clear the cookies to ensure the fresh cookie is used in subsequent requests.
                        //TODO is this required?
                        httpClientFactory.flushCookies();

                        // Ensure that the login code knows that the current session token may be invalid despite seemingly being okay
                        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance();
                        if(sessionDetails != null) {
                            sessionDetails.setSessionMayHaveExpired();
                        }

                        // try and get a new session token
                        newLoginAcquired = getNewLogin();
                    }
                }

                // if we either got a new token here or another thread did, retry the original failing call.
                if(newLoginAcquired) {
                    sessionToken = PiwigoSessionDetails.getActiveSessionToken();
                    // ensure we ignore this error (if it errors again, we'll capture that one)
                    tryingAgain = true;
                    // just run the original call again (another thread has retrieved a new session
                    rerunCall();
                }
            }
        }
        if(!tryingAgain) {
            if(BuildConfig.DEBUG && !"Method name is not valid".equals(error.getMessage())) {
                Log.e(getTag(),"Tracking piwigo failure class: " + error.getClass() +" message: " + error.getMessage(), error);
            }
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
                client = getHttpClientFactory().getSyncHttpClient(context);
            } else {
                client = getHttpClientFactory().getAsyncHttpClient(context);
            }
            if(client == null) {
                // unable to build a client from configuration properties.
                sendFailureMessage(-1, null, null, new IllegalArgumentException(getContext().getString(R.string.error_server_configuration_invalid)));
            } else {
                requestHandle = runCall(client, this);
            }
        } catch(RuntimeException e) {
            if(client == null) {
                sendFailureMessage(-1, null, null, new IllegalStateException(getContext().getString(R.string.error_building_http_engine), e));
            } else {
                sendFailureMessage(-1, null, null, e);
            }
        } finally {
            if(requestHandle == null) {
                isRunning = false;
                synchronized (this) {
                    notifyAll();
                }
            }
        }
    }

    protected void preRunCall() {
    }

    public abstract RequestHandle runCall(CachingAsyncHttpClient client, AsyncHttpResponseHandler handler);

    public HttpClientFactory getHttpClientFactory() {
        return httpClientFactory;
    }

    public boolean getNewLogin() {

        // send a server login request
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        piwigoServerUrl = ConnectionPreferences.getTrimmedNonNullPiwigoServerAddress(prefs, context);

        String username = ConnectionPreferences.getPiwigoUsername(prefs, context);
        String password = ConnectionPreferences.getPiwigoPassword(prefs, context);

        int loginStatus = PiwigoSessionDetails.NOT_LOGGED_IN;
        int newLoginStatus = PiwigoSessionDetails.NOT_LOGGED_IN;
        boolean exit = false;
        do {
            LoginResponseHandler handler = new LoginResponseHandler(username, password);
            runLoginHandlerAndWaitForOutcome(handler);
            if (handler.isLoginSuccess()) {
                if (handler.getNestedFailureMethod() != null) {
                    // failed internally. - still a failure!
                    newLoginStatus = PiwigoSessionDetails.LOGGED_IN;
                } else if (PiwigoSessionDetails.isFullyLoggedIn()) {
                    newLoginStatus = PiwigoSessionDetails.LOGGED_IN_WITH_SESSION_AND_USER_DETAILS;
                    exit = true;
                    PiwigoResponseBufferingHandler.PiwigoOnLoginResponse response = (PiwigoResponseBufferingHandler.PiwigoOnLoginResponse) handler.getResponse();
                    EventBus.getDefault().post(new PiwigoLoginSuccessEvent(response.getOldCredentials(), false));
                    onGetNewSessionSuccess();
                    // update the session token for this handler.
                    return true;
                } else if (PiwigoSessionDetails.isLoggedInWithSessionDetails()) {
                    newLoginStatus = PiwigoSessionDetails.LOGGED_IN_WITH_SESSION_DETAILS;
                } else if (PiwigoSessionDetails.isLoggedIn()) {
                    newLoginStatus = PiwigoSessionDetails.LOGGED_IN;
                }
            }
            if (newLoginStatus == loginStatus) {
                // no progression - fail call.
                exit = true;
                onGetNewSessionAndOrUserDetailsFailed();
            }
            loginStatus = newLoginStatus;
        } while (!exit);

        return false;
    }

    private void runLoginHandlerAndWaitForOutcome(LoginResponseHandler handler) {
        handler.setCallDetails(context, piwigoServerUrl, !getUseSynchronousMode());
        handler.setPublishResponses(false);
        handler.runCall();

        // this is the absolute timeout (5min) - in case something is seriously wrong.
        long callTimeoutAtTime = System.currentTimeMillis() + 300000;

        synchronized (handler) {
            while (handler.isRunning() && !cancelCallAsap) {
                long waitForMillis = callTimeoutAtTime - System.currentTimeMillis();
                if (waitForMillis > 0) {
                    try {
                        handler.wait(waitForMillis);
                    } catch (InterruptedException e) {
                        // Either this has been cancelled or timed out
                        if (cancelCallAsap) {
                            if (BuildConfig.DEBUG) {
                                Log.e(handler.getTag(), "Service call cancelled before login handler could finish running");
                            }
                            handler.cancelCallAsap();
                        }
                    }
                }
            }
        }
        if(handler.isRunning()) {
            if(BuildConfig.DEBUG) {
                Log.e(handler.getTag(), "Timeout while waiting for service call login handler to finish running");
            }
            handler.cancelCallAsap();
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
                sendFailureMessage(-1, null, null, new IllegalArgumentException(getContext().getString(R.string.error_request_timed_out)));
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
