package delit.piwigoclient.piwigoApi.handlers;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
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
import delit.piwigoclient.piwigoApi.Worker;
import delit.piwigoclient.piwigoApi.http.CachingAsyncHttpClient;
import delit.piwigoclient.piwigoApi.http.RequestHandle;
import delit.piwigoclient.ui.events.PiwigoLoginSuccessEvent;

/**
 * Created by gareth on 10/10/17.
 */

public abstract class AbstractBasicPiwigoResponseHandler extends AsyncHttpResponseHandler {
    private final boolean built;
    private final String tag;
    private boolean allowSessionRefreshAttempt;
    private String sessionToken;
    private boolean triedLoggingInAgain;
    private HttpClientFactory httpClientFactory;
    private boolean isSuccess;
    private boolean cancelCallAsap;
    private RequestHandle requestHandle;
    private boolean isRunning;
    private boolean rerunningCall;
    private Context context;
    private Throwable error;
    private int statusCode;
    private Header[] headers;
    private byte[] responseBody;
    private ConnectionPreferences.ProfilePreferences connectionPrefs;
    private SharedPreferences sharedPrefs;
    private boolean isPerformingLogin;


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
        try {
            super.handleMessage(message);
            switch (message.what) {
                case FAILURE_MESSAGE:
                case SUCCESS_MESSAGE:
                case CANCEL_MESSAGE:
                case FINISH_MESSAGE:
                    postCall(isSuccess);
                    if (rerunningCall) {
                        rerunningCall = false;
                    }
                    isRunning = false;
                    break;
                default:
                    if (BuildConfig.DEBUG) {
                        Log.i(tag, "rx " + message.what);
                    }
            }
        } finally {
            synchronized (this) {
                try {
                    this.notifyAll();
                } catch (IllegalMonitorStateException e) {
                    Crashlytics.logException(e);
                    if (BuildConfig.DEBUG) {
                        Log.e(getTag(), "unable to notify threads waiting on this object", e);
                    }
                }
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

    protected void onSuccess(int statusCode, Header[] headers, byte[] responseBody, boolean hasBrandNewSession) {
    }

    protected void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error, boolean triedToGetNewSession) {
    }

    public void setCallDetails(Context parentContext, ConnectionPreferences.ProfilePreferences connectionPrefs, boolean useAsyncMode) {
        setCallDetails(parentContext, connectionPrefs, useAsyncMode, true);
    }

    protected Context getContext() {
        return context;
    }

    public void setCallDetails(Context parentContext, ConnectionPreferences.ProfilePreferences connectionPrefs, boolean useAsyncMode, boolean allowSessionRefreshAttempt) {
        clearCallDetails();

        this.context = parentContext;

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        this.httpClientFactory = HttpClientFactory.getInstance(context);
        this.allowSessionRefreshAttempt = allowSessionRefreshAttempt;
        if (this.connectionPrefs == null) {
            this.connectionPrefs = connectionPrefs;
        }
        this.sessionToken = PiwigoSessionDetails.getActiveSessionToken(this.connectionPrefs);

        if (useAsyncMode && Looper.myLooper() == null) {
            // use a thread from the threadpool the request is sent using to handle the response
            setUsePoolThread(true);
        } else {
            super.setUseSynchronousMode(!useAsyncMode);
        }
    }

    @Override
    public void setUseSynchronousMode(boolean sync) {
        if (built && sync != getUseSynchronousMode()) {
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
            if (error instanceof IOException && ("Unhandled exception: Cache has been shut down".equals(error.getMessage())
                    || "Unhandled exception: Connection pool shut down".equals(error.getMessage()))) {
                tryingAgain = true;
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    // wait just a fraction of a second to give another cache or connection pool time to come up.
                }
                rerunCall();
            } else if (error instanceof SocketTimeoutException) {
                tryingAgain = true;
                rerunCall();
            } else if (error instanceof SSLException && error.getMessage() != null && error.getMessage().contains("Connection reset by peer")
                    || error instanceof SSLHandshakeException && error.getMessage() != null && error.getMessage().contains("I/O error during system call")) {
                tryingAgain = true;
                rerunCall();
            } else if (allowSessionRefreshAttempt
                    && (statusCode == HttpStatus.SC_UNAUTHORIZED && !triedLoggingInAgain && (error == null || error.getMessage().equalsIgnoreCase("Access denied")))) {

                boolean newLoginAcquired = false;
                synchronized (AbstractBasicPiwigoResponseHandler.class) {
                    // Only one instance of this class can perform a login at a time - all others will wait for the outcome
                    triedLoggingInAgain = true;
                    PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);
                    String newToken = null;
                    if (sessionDetails != null) {
                        newToken = sessionDetails.getSessionToken();
                    }
                    if (newToken != null && !newToken.equals(sessionToken)) {
                        newLoginAcquired = true;
                    } else if (!isPerformingLogin() && !(sessionDetails != null && sessionDetails.isLoggedIn() && !sessionDetails.isFullyLoggedIn())) {
                        // if we're not trying to get a new login at the moment. (otherwise this recurses).

                        // clear the cookies to ensure the fresh cookie is used in subsequent requests.
                        //TODO is this required?
                        httpClientFactory.flushCookies(connectionPrefs);

                        // Ensure that the login code knows that the current session token may be invalid despite seemingly being okay
                        if (sessionDetails != null && sessionDetails.isOlderThanSeconds(5)) {
                            sessionDetails.setSessionMayHaveExpired();
                        }

                        // try and get a new session token
                        synchronized (Worker.class) {
                            // only one worker at a time can do this.
                            newLoginAcquired = getNewLogin();
                        }
                    }
                }

                // if we either got a new token here or another thread did, retry the original failing call.
                if (newLoginAcquired) {
                    sessionToken = PiwigoSessionDetails.getActiveSessionToken(connectionPrefs);
                    // ensure we ignore this error (if it errors again, we'll capture that one)
                    tryingAgain = true;
                    // just run the original call again (another thread has retrieved a new session
                    rerunCall();
                }
            }
        }
        if (!tryingAgain) {
            if (BuildConfig.DEBUG && error != null && !"Method name is not valid".equals(error.getMessage())) {
                Log.e(getTag(), "Tracking piwigo failure class: " + error.getClass() + " message: " + error.getMessage(), error);
            }
            this.statusCode = statusCode;
            this.headers = headers;
            this.responseBody = responseBody;
            if (this.error == null) {
                this.error = error;
            }
            onFailure(statusCode, headers, responseBody, this.error, triedLoggingInAgain);
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

    protected void resetFailureAsASuccess() {
        isSuccess = true;
    }

    public Throwable getError() {
        return error;
    }

    protected void setError(Throwable error) {
        this.error = error;
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
                client = getHttpClientFactory().getSyncHttpClient(connectionPrefs, context);
            } else {
                client = getHttpClientFactory().getAsyncHttpClient(connectionPrefs, context);
            }
            if (client == null) {
                // unable to build a client from configuration properties.
                sendFailureMessage(-1, null, null, new IllegalArgumentException(getContext().getString(R.string.error_server_configuration_invalid)));
            } else {
                requestHandle = runCall(client, this);
            }
        } catch (RuntimeException e) {
            Crashlytics.logException(e);
            if (client == null) {
                sendFailureMessage(-1, null, null, new IllegalStateException(getContext().getString(R.string.error_building_http_engine), e));
            } else {
                sendFailureMessage(-1, null, null, new RuntimeException(getContext().getString(R.string.error_unexpected_error_calling_server), e));
            }
        } finally {
            if (requestHandle == null) {
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

        int loginStatus = PiwigoSessionDetails.NOT_LOGGED_IN;
        int newLoginStatus = PiwigoSessionDetails.NOT_LOGGED_IN;
        boolean exit = false;
        LoginResponseHandler handler = new LoginResponseHandler();
        do {
            handler.invokeAndWait(context, getConnectionPrefs());
            PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);
            if (handler.isLoginSuccess()) {
                if (handler.getNestedFailureMethod() != null) {
                    // failed internally. - still a failure!
                    newLoginStatus = PiwigoSessionDetails.LOGGED_IN;
                } else if (sessionDetails != null && sessionDetails.isFullyLoggedIn()) {
                    newLoginStatus = PiwigoSessionDetails.LOGGED_IN_WITH_SESSION_AND_USER_DETAILS;
                    exit = true;
                    LoginResponseHandler.PiwigoOnLoginResponse response = (LoginResponseHandler.PiwigoOnLoginResponse) handler.getResponse();
                    EventBus.getDefault().post(new PiwigoLoginSuccessEvent(response.getOldCredentials(), false));
                    onGetNewSessionSuccess();
                    // update the session token for this handler.
                    return true;
                } else if (sessionDetails != null && sessionDetails.isLoggedInWithFullSessionDetails()) {
                    newLoginStatus = PiwigoSessionDetails.LOGGED_IN_WITH_SESSION_DETAILS;
                } else if (sessionDetails != null && sessionDetails.isLoggedIn()) {
                    newLoginStatus = PiwigoSessionDetails.LOGGED_IN;
                }
            } else {
                reportNestedFailure(handler);
            }
            if (newLoginStatus == loginStatus) {
                // no progression - fail call.
                exit = true;
                onGetNewSessionAndOrUserDetailsFailed();
            }
            loginStatus = newLoginStatus;
        } while (!exit);

        return handler.isSuccess();
    }

    public boolean isCancelCallAsap() {
        return cancelCallAsap;
    }

    public void cancelCallAsap() {
        synchronized (this) {
            this.notifyAll();
            cancelCallAsap = true;
            if (requestHandle != null && !(requestHandle.isFinished() || requestHandle.isCancelled())) {
                boolean cancelled = requestHandle.cancel(true);
                if (cancelled) {
                    sendFailureMessage(-1, null, null, new IllegalArgumentException(getContext().getString(R.string.error_request_timed_out)));
                }
            }
        }
    }

    public RequestHandle getRequestHandle() {
        return requestHandle;
    }

    protected String getPiwigoServerUrl() {
        return connectionPrefs.getPiwigoServerAddress(sharedPrefs, context);
    }

    protected SharedPreferences getSharedPrefs() {
        return sharedPrefs;
    }

    public String getPiwigoWsApiUri() {
        return getPiwigoServerUrl() + "/ws.php?format=json";
    }

    public boolean isRunning() {
        return isRunning;
    }

    public ConnectionPreferences.ProfilePreferences getConnectionPrefs() {
        return connectionPrefs;
    }

    public void withConnectionPreferences(ConnectionPreferences.ProfilePreferences connectionPrefs) {
        this.connectionPrefs = connectionPrefs;
    }

    public boolean isPerformingLogin() {
        return isPerformingLogin;
    }

    public void setPerformingLogin() {
        isPerformingLogin = true;
    }

    /**
     * Called before initial call and when manual retry invoked.
     */
    public void beforeCall() {
        this.triedLoggingInAgain = false;
    }
}
