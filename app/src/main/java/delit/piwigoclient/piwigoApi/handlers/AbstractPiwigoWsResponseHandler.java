package delit.piwigoclient.piwigoApi.handlers;

import android.content.Context;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.loopj.android.http.AsyncHttpResponseHandler;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONException;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Arrays;

import cz.msebera.android.httpclient.Header;
import delit.libs.http.RequestParams;
import delit.libs.util.http.HttpUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoJsonResponse;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.CachingAsyncHttpClient;
import delit.piwigoclient.piwigoApi.http.RequestHandle;
import delit.piwigoclient.ui.events.PiwigoMethodNowUnavailableUsingFallback;

/**
 * Created by gareth on 25/06/17.
 */
public abstract class AbstractPiwigoWsResponseHandler extends AbstractPiwigoDirectResponseHandler {

    private final String piwigoMethod;
    private RequestParams requestParams;
    private String nestedFailureMethod;
    private Throwable nestedFailure;
    private Gson gson;
    private String failedOriginalMethod;
    private String piwigoMethodToUse;

    protected AbstractPiwigoWsResponseHandler(String piwigoMethod, String tag) {
        super(tag);
        this.piwigoMethod = piwigoMethod;
    }

    public String getPiwigoMethod() {
        return piwigoMethod;
    }

    protected String getPiwigoMethodOverrideIfPossible(String overrideMethod) {
        if(piwigoMethodToUse == null) {
            piwigoMethodToUse = piwigoMethod;
            PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(getConnectionPrefs());
            if(sessionDetails == null) {
                boolean newLoginAcquired = testLoginGetNewSessionIfNeeded();
                if(newLoginAcquired) {
                    sessionDetails = PiwigoSessionDetails.getInstance(getConnectionPrefs());
                }
            }
            if (sessionDetails != null) {
                if (sessionDetails.isMethodAvailable(overrideMethod)) {
                    piwigoMethodToUse = overrideMethod;
                }
            }
        }
        return piwigoMethodToUse;
    }

    public boolean isMethodAvailable(Context context, ConnectionPreferences.ProfilePreferences connectionPrefs) {
        setCallDetails(context, connectionPrefs, false);
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);
        if(sessionDetails == null) {
            LoginResponseHandler handler = new LoginResponseHandler();
            handler.invokeAndWait(context, connectionPrefs);
            if(handler.isLoginSuccess()) {
                sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);
            }
        }
        return sessionDetails != null && sessionDetails.isMethodAvailable(getPiwigoMethod());
    }

    public RequestParams getRequestParameters() {
        if (requestParams == null) {
            requestParams = buildRequestParameters();
        } else {
            requestParams.remove("method");
            requestParams.put("method", getPiwigoMethod());
        }
        return requestParams;
    }

    protected abstract RequestParams buildRequestParameters();

    @Override
    public void clearCallDetails() {
        nestedFailureMethod = null;
        nestedFailure = null;
        gson = null;
        super.clearCallDetails();
    }

    public String getNestedFailureMethod() {
        return nestedFailureMethod;
    }

    public Throwable getNestedFailure() {
        return nestedFailure;
    }

    protected Gson buildGson() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
        gsonBuilder.setLenient();
        Gson gson = gsonBuilder.create();
        return gson;
    }

    protected Gson getGson() {
        if (gson == null) {
            gson = buildGson();
        }
        return gson;
    }

    @Override
    protected void onSuccess(int statusCode, Header[] headers, byte[] responseBody, boolean hasBrandNewSession, boolean isResponseCached) {
        if (BuildConfig.DEBUG) {
            String responseBodyStr = "<NONE PRESENT>";
            if (responseBody != null) {
                responseBodyStr = new String(responseBody);
            }

            StringBuilder msgBuilder = new StringBuilder();
            msgBuilder.append("Connection Profile:");
            msgBuilder.append(getConnectionPrefs());
            msgBuilder.append('\n');
            msgBuilder.append("Method (succeeded):");
            msgBuilder.append(getPiwigoMethod());
            msgBuilder.append('\n');
            if (getNestedFailureMethod() != null) {
                msgBuilder.append("Nested Method (failed):");
                msgBuilder.append(getNestedFailureMethod());
                msgBuilder.append('\n');
            }
            msgBuilder.append("Request Params:");
            msgBuilder.append('\n');
            msgBuilder.append(getRequestParameters());
            msgBuilder.append('\n');
            msgBuilder.append("Response Headers:");
            msgBuilder.append('\n');
            if(headers != null) {
                for (Header h : headers) {
                    msgBuilder.append(h.getName());
                    msgBuilder.append(':');
                    msgBuilder.append(h.getValue());
                    msgBuilder.append('\n');
                }
            } else {
                msgBuilder.append("<NONE PRESENT>");
                msgBuilder.append('\n');
            }
            msgBuilder.append("Response Body:");
            msgBuilder.append('\n');
            msgBuilder.append(responseBodyStr);
            Crashlytics.log(Log.VERBOSE, getTag(),  msgBuilder.toString());
        }
        if(failedOriginalMethod != null) {
            EventBus.getDefault().post(new PiwigoMethodNowUnavailableUsingFallback(failedOriginalMethod, getPiwigoMethod()));
            failedOriginalMethod = null;
        }
        boolean isCached = isResponseCached(headers);


        String response = null;
        try {
            int jsonStartsAt = 0;
            if (responseBody != null) {
                int idx = -1;
                for (int i = 0; i < responseBody.length; i++) {
                    if (responseBody[i] == '{') {
                        idx = i;
                        break;
                    }
                }
                jsonStartsAt = idx;
            }
            ByteArrayInputStream jsonBis = new ByteArrayInputStream(responseBody);
            if (jsonStartsAt > 0) {
                jsonBis.skip(jsonStartsAt - 1);
            }
            PiwigoJsonResponse piwigoResponse = getGson().fromJson(new InputStreamReader(jsonBis), PiwigoJsonResponse.class);
            processJsonResponse(getMessageId(), getPiwigoMethod(), piwigoResponse, responseBody, isCached);

        } catch(IllegalStateException e) {
            // some devices seem to cause this to be an illegal state exception not json syntax exception....
            String responseBodyStr = responseBody != null ? new String(responseBody) : null;
            logJsonSyntaxError(responseBodyStr);
            if(!"Expected BEGIN_OBJECT but was STRING at line 1 column 1 path $".equals(e.getMessage())) {
                Crashlytics.logException(e);
            }
            boolean handled = handleCombinedJsonAndHtmlResponse(statusCode, headers, responseBody, new JsonSyntaxException(e.getMessage()), hasBrandNewSession);
            if (!handled) {
                PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse(this, statusCode, e.getMessage(), e, isCached);
                r.setResponse(responseBodyStr);
                storeResponse(r);
            }
        } catch (JsonSyntaxException e) {
            String responseBodyStr = responseBody != null ? new String(responseBody) : null;
            logJsonSyntaxError(responseBodyStr);
            if(!"Expected BEGIN_OBJECT but was STRING at line 1 column 1 path $".equals(e.getMessage())) {
                Crashlytics.logException(e);
            }
            boolean handled = handleCombinedJsonAndHtmlResponse(statusCode, headers, responseBody, e, hasBrandNewSession);
            if (!handled) {
                PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse(this, statusCode, e.getMessage(), e, isCached);
                r.setResponse(responseBodyStr);
                storeResponse(r);
            }
        } catch (JsonIOException e) {
            String responseBodyStr = responseBody != null ? new String(responseBody) : null;
            logJsonSyntaxError(responseBodyStr);
            Crashlytics.logException(e);
            PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse(this, statusCode, e.getMessage(), e, isCached);
            r.setResponse(responseBodyStr);
            storeResponse(r);
        }
    }

    protected void logJsonSyntaxError(String responseBodyStr) {
        Crashlytics.log(String.format("Json Syntax error: %1$s (%2$s) : %3$s", getPiwigoMethod(), getRequestParameters(), responseBodyStr));
    }

    private boolean handleCombinedJsonAndHtmlResponse(int statusCode, Header[] headers, byte[] responseBody, JsonSyntaxException e, boolean hasBrandNewSession) {
        if (e.getMessage().equals("java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1 path $")) {
            int idx = 0;
            for (int i = responseBody.length - 1; i > 0; i--) {
                if ('{' == responseBody[i]) {
                    idx = i;
                    break;
                }
            }
            if (idx > 0) {
                byte[] actualJson = Arrays.copyOfRange(responseBody, idx, responseBody.length);
                onSuccess(statusCode, headers, actualJson, hasBrandNewSession, isResponseCached(headers));
                // skip remaining method code.
                return true;
            }
        }
        return false;
    }

    private void processJsonResponse(long messageId, String piwigoMethod, PiwigoJsonResponse jsonResponse, byte[] rawData, boolean isCached) {
        try {
            if(jsonResponse != null && jsonResponse.getStat() != null) {
                switch (jsonResponse.getStat()) {
                    case "fail":
                        onPiwigoFailure(jsonResponse, isCached);
                        break;
                    case "ok":
                        onPiwigoSuccess(jsonResponse.getResult(), isCached);
                        break;
                    default:
                        throw new JSONException("Unexpected piwigo response code");
                }
            } else {
                Crashlytics.log(Log.ERROR, getTag(), piwigoMethod + " onReceiveResult: \n" + getRequestParameters() + '\n');
                String rawResponseStr = new String(rawData, Charset.forName("UTF-8"));
                PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse(this, PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse.OUTCOME_UNKNOWN, rawResponseStr, isCached);
                storeResponse(r);
            }
        } catch (JSONException|JsonIOException|NullPointerException|NumberFormatException e) {
            Crashlytics.log(Log.ERROR, getTag(), piwigoMethod + " onReceiveResult: \n" + getRequestParameters() + '\n');
            Crashlytics.logException(e);
            String rawResponseStr = new String(rawData, Charset.forName("UTF-8"));
            PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse(this, PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse.OUTCOME_UNKNOWN, rawResponseStr, isCached);
            storeResponse(r);
        }
    }

    protected void onPiwigoFailure(PiwigoJsonResponse rsp, boolean isCached) throws JSONException {
        setError(new Throwable(rsp.getMessage()));
        PiwigoResponseBufferingHandler.PiwigoServerErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoServerErrorResponse(this, rsp.getErr(), rsp.getMessage(), isCached);
        storeResponse(r);
    }

    protected void reportNestedFailure(AbstractBasicPiwigoResponseHandler nestedHandler) {
        if (nestedHandler instanceof AbstractPiwigoWsResponseHandler) {
            nestedFailureMethod = ((AbstractPiwigoWsResponseHandler) nestedHandler).getPiwigoMethod();
            nestedFailure = nestedHandler.getError();
            setError(nestedHandler.getError());
        }
        super.reportNestedFailure(nestedHandler);
    }

    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        PiwigoResponseBufferingHandler.PiwigoSuccessResponse r = new PiwigoResponseBufferingHandler.PiwigoSuccessResponse(getMessageId(), getPiwigoMethod(), rsp, isCached);
        storeResponse(r);
    }

    // When the response returned by REST has Http response code other than '200'
    @Override
    protected boolean onFailure(final int statusCode, Header[] headers, byte[] responseBody, final Throwable error, boolean triedToGetNewSession, boolean isCached) {

        if (BuildConfig.DEBUG) {
            String errorBody = "<NONE PRESENT>";
            if (responseBody != null) {
                errorBody = new String(responseBody);
            }

            StringBuilder msgBuilder = new StringBuilder();
            msgBuilder.append("Connection Profile:");
            msgBuilder.append(getConnectionPrefs());
            msgBuilder.append('\n');
            msgBuilder.append("Method (failed):");
            msgBuilder.append(getPiwigoMethod());
            msgBuilder.append('\n');
            if (getNestedFailureMethod() != null) {
                msgBuilder.append("Nested Method (failed):");
                msgBuilder.append(getNestedFailureMethod());
                msgBuilder.append('\n');
            }
            msgBuilder.append("Request Params:");
            msgBuilder.append('\n');
            msgBuilder.append(getRequestParameters());
            msgBuilder.append('\n');
            msgBuilder.append("Response Headers:");
            msgBuilder.append('\n');
            if(headers != null) {
                for (Header h : headers) {
                    msgBuilder.append(h.getName());
                    msgBuilder.append(':');
                    msgBuilder.append(h.getValue());
                    msgBuilder.append('\n');
                }
            } else {
                msgBuilder.append("<NONE PRESENT>");
                msgBuilder.append('\n');
            }
            msgBuilder.append("Response Body:");
            msgBuilder.append('\n');
            msgBuilder.append(errorBody);
            Crashlytics.log(Log.ERROR, getTag(),  msgBuilder.toString());
            Crashlytics.logException(error);
        }

        boolean canRetryCall = false;
        if (statusCode == 501 && "Method name is not valid".equals(error.getMessage())) {
            failedOriginalMethod = getPiwigoMethod();
            if(!failedOriginalMethod.startsWith("pwg.")) {
                PiwigoSessionDetails.getInstance(getConnectionPrefs()).onMethodNotAvailable(getPiwigoMethod());
                // allow retry if there is a fallback method (otherwise report it!).
                canRetryCall = !failedOriginalMethod.equals(getPiwigoMethod());
                if(!canRetryCall) {
                    EventBus.getDefault().post(new PiwigoMethodNowUnavailableUsingFallback(failedOriginalMethod, null));
                }
            } else {
                failedOriginalMethod = null;
            }
        }

        if(!isCached && isSuccess()) {
            Log.d(getTag(), "cache revalidation failed");
            // this is a cache revalidation attempt.. flag that perhaps?
        }

        if(!canRetryCall) {
            String[] errorDetail = HttpUtils.getHttpErrorMessage(getContext(), statusCode, error);
            if (getNestedFailureMethod() != null) {
                errorDetail[0] = getNestedFailureMethod() + " : " + errorDetail[0];
            } else {
                errorDetail[0] = getPiwigoMethod() + " : " + errorDetail[0];
            }
            PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse(this, statusCode, errorDetail[0], errorDetail[1], error, isCached);
            r.setResponse(responseBody != null ? new String(responseBody) : "");
            storeResponse(r);
        }
        return canRetryCall;
    }

    @Override
    public RequestHandle runCall(CachingAsyncHttpClient client, AsyncHttpResponseHandler handler) {
//        String thread = Thread.currentThread().getName();
        if (BuildConfig.DEBUG) {
            Log.d(getTag(), "calling " + getPiwigoWsApiUri() + '&' + getRequestParameters().toString());
            Log.e(getTag(), "Invoking call to server (" + getRequestParameters() + ") thread from thread " + Thread.currentThread().getName());
        }
        if(isUseHttpGet()) {
            PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(getConnectionPrefs());
            boolean onlyUseCache = sessionDetails != null && sessionDetails.isCached();
            return client.get(getContext(), getPiwigoWsApiUri(), buildOfflineAccessHeaders(onlyUseCache), getRequestParameters(), handler);
        } else {
            return client.post(getContext(), getPiwigoWsApiUri(), getRequestParameters(), handler);
        }
    }

    public boolean isUseHttpGet() {
        return false;
    }
}