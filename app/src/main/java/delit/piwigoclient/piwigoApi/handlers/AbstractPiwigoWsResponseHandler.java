package delit.piwigoclient.piwigoApi.handlers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.loopj.android.http.AsyncHttpResponseHandler;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.entity.ContentType;
import cz.msebera.android.httpclient.entity.StringEntity;
import cz.msebera.android.httpclient.message.BasicNameValuePair;
import delit.libs.core.util.Logging;
import delit.libs.http.RequestParams;
import delit.libs.http.cache.CachingAsyncHttpClient;
import delit.libs.http.cache.RequestHandle;
import delit.libs.util.IOUtils;
import delit.libs.util.http.HttpUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoJsonResponse;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.ui.events.PiwigoMethodNowUnavailableUsingFallback;

/**
 * Created by gareth on 25/06/17.
 */
public abstract class AbstractPiwigoWsResponseHandler extends AbstractPiwigoDirectResponseHandler {

    private static final String TAG = "AbPwgResHndlr";
    private static final boolean VERBOSE_LOGGING = false;
    private final String piwigoMethod;
    private RequestParams requestParams;
    private String nestedFailureMethod;
    private Throwable nestedFailure;
    private URI nestedRequestURI;
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

    protected String getPwgSessionToken() {
        String sessionToken = "";
        PiwigoSessionDetails sessionDetails = getPiwigoSessionDetails();
        if (sessionDetails != null && sessionDetails.isLoggedInWithFullSessionDetails()) {
            sessionToken = sessionDetails.getSessionToken();
        }
        return sessionToken;
    }

    protected String getPiwigoMethodOverrideIfPossible(String overrideMethod) {
        if(piwigoMethodToUse == null) {
            piwigoMethodToUse = piwigoMethod;
            PiwigoSessionDetails sessionDetails = getPiwigoSessionDetails();
            if(sessionDetails == null) {
                boolean newLoginAcquired = testLoginGetNewSessionIfNeeded();
                if(newLoginAcquired) {
                    sessionDetails = getPiwigoSessionDetails();
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

    public boolean isMethodAvailable(@NonNull Context context, ConnectionPreferences.ProfilePreferences connectionPrefs) {
        setCallDetails(context, connectionPrefs, !getUseSynchronousMode());
        PiwigoSessionDetails sessionDetails = getPiwigoSessionDetails();
        if(sessionDetails == null) {
            LoginResponseHandler handler = new LoginResponseHandler();
            handler.invokeAndWait(context, connectionPrefs);
            if(handler.isLoginSuccess()) {
                sessionDetails = getPiwigoSessionDetails();
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
            if (requestParams.has("pwg_token")) {
                requestParams.remove("pwg_token");
                requestParams.put("pwg_token", getPwgSessionToken());
            }
        }
        return requestParams;
    }

    public StringEntity getJsonFormParameters() {

        List<BasicNameValuePair> formParams = new ArrayList<>(getRequestParameters().getParamsList());
        // for tidiness remove the pwg method - this must always be a request parameter
        for (Iterator<BasicNameValuePair> iterator = formParams.iterator(); iterator.hasNext(); ) {
            BasicNameValuePair nvp = iterator.next();
            if (nvp.getName().equals("method")) {
                iterator.remove();
                break;
            }
        }

        Gson gson = new GsonBuilder().registerTypeAdapter(BasicNameValuePair.class, new BasicNameValuePairJsonAdapter()).create();
        return new StringEntity(gson.toJson(getRequestParameters().getParamsList()), ContentType.APPLICATION_JSON);
    }

    protected abstract RequestParams buildRequestParameters();

    @Override
    public void clearCallDetails() {
        nestedFailureMethod = null;
        nestedFailure = null;
        nestedRequestURI = null;
        gson = null;
        super.clearCallDetails();
    }

    public String getNestedFailureMethod() {
        return nestedFailureMethod;
    }

    public URI getNestedRequestURI() {
        return nestedRequestURI;
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
        if (VERBOSE_LOGGING && BuildConfig.DEBUG) {
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
            Logging.log(Log.VERBOSE, getTag(),  msgBuilder.toString());
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
                int skip = jsonStartsAt - 1;
                long skipped = jsonBis.skip(skip);
                if (skipped != skip) {
                    Logging.log(Log.ERROR, getTag(), "Tried to skip " + skip + " characters, but actually skipped " + skipped);
                }
            }
            PiwigoJsonResponse piwigoResponse = getGson().fromJson(new InputStreamReader(jsonBis), PiwigoJsonResponse.class);
            processJsonResponse(getMessageId(), getPiwigoMethod(), piwigoResponse, responseBody, isCached);

        } catch(IllegalStateException e) {
            // some devices seem to cause this to be an illegal state exception not json syntax exception....
            String responseBodyStr = responseBody != null ? new String(responseBody) : null;
            logJsonSyntaxError(responseBodyStr);
            if(!"Expected BEGIN_OBJECT but was STRING at line 1 column 1 path $".equals(e.getMessage())) {
                Logging.recordException(e);
            }
            if (!handleCombinedJsonAndHtmlResponse(statusCode, headers, responseBody, new JsonSyntaxException(e.getMessage()), hasBrandNewSession)) {
                recordFailureHandingHttp200Response(statusCode, isCached, e, responseBodyStr);
            }
        } catch (JsonSyntaxException e) {
            String responseBodyStr = responseBody != null ? new String(responseBody) : null;
            logJsonSyntaxError(responseBodyStr);
            if(!"Expected BEGIN_OBJECT but was STRING at line 1 column 1 path $".equals(e.getMessage())) {
                Logging.recordException(e);
            }
            if (!handleCombinedJsonAndHtmlResponse(statusCode, headers, responseBody, e, hasBrandNewSession)) {
                //Uh-oh something weird has occurred - possibly the server threw a load of sql back
                recordFailureHandingHttp200Response(statusCode, isCached, e, responseBodyStr);
            }
        } catch (JsonIOException e) {
            String responseBodyStr = responseBody != null ? new String(responseBody) : null;
            logJsonSyntaxError(responseBodyStr);
            Logging.recordException(e);
            recordFailureHandingHttp200Response(statusCode, isCached, e, responseBodyStr);
        }
    }

    private void recordFailureHandingHttp200Response(int statusCode, boolean isCached, Exception e, String responseBodyStr) {
        PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse(this, getRequestURIAsStr(), statusCode, e.getMessage(), e, isCached);
        r.setResponse(responseBodyStr);
        resetSuccessAsFailure();
        storeResponse(r);
    }

    protected void logJsonSyntaxError(String responseBodyStr) {
        RequestParams reqParams = getRequestParameters();
        if (reqParams.has("password")) {
            reqParams.remove("password");
            reqParams.put("password", "********");
        }
        Logging.log(Log.ERROR, TAG, String.format("Json Syntax error: %1$s (%2$s) : %3$s", getPiwigoMethod(), reqParams, responseBodyStr));
    }

    private boolean handleCombinedJsonAndHtmlResponse(int statusCode, Header[] headers, byte[] responseBody, JsonSyntaxException e, boolean hasBrandNewSession) {
        if (Objects.equals(e.getMessage(), "java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1 path $")) {
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
                Logging.log(Log.ERROR, getTag(), piwigoMethod + " onReceiveResult: \n" + getRequestParameters() + '\n');
                String rawResponseStr = new String(rawData, IOUtils.getUtf8Charset());
                PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse(this, PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse.OUTCOME_UNKNOWN, rawResponseStr, isCached);
                storeResponse(r);
            }
        } catch (JSONException|JsonIOException|NullPointerException|NumberFormatException e) {
            Logging.log(Log.ERROR, getTag(), piwigoMethod + " onReceiveResult: \n" + getRequestParameters() + '\n');
            Logging.recordException(e);
            String rawResponseStr = new String(rawData, IOUtils.getUtf8Charset());
            PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse(this, PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse.OUTCOME_UNKNOWN, rawResponseStr, isCached);
            storeResponse(r);
        }
    }

    protected void onPiwigoFailure(PiwigoJsonResponse rsp, boolean isCached) throws JSONException {
        setError(new Throwable(rsp.getMessage()));
        PiwigoResponseBufferingHandler.PiwigoServerErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoServerErrorResponse(this, getRequestURIAsStr(), rsp.getErr(), rsp.getMessage(), isCached);
        storeResponse(r);
    }

    protected void reportNestedFailure(AbstractBasicPiwigoResponseHandler nestedHandler) {
        if (nestedHandler instanceof AbstractPiwigoWsResponseHandler) {
            nestedFailureMethod = ((AbstractPiwigoWsResponseHandler) nestedHandler).getPiwigoMethod();
            nestedFailure = nestedHandler.getError();
            nestedRequestURI = nestedHandler.getRequestURI();
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
    protected boolean onFailure(String uri, final int statusCode, Header[] headers, byte[] responseBody, final Throwable error, boolean triedToGetNewSession, boolean isCached) {

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
            Logging.log(Log.ERROR, getTag(),  msgBuilder.toString());
            Logging.recordException(error);
        }

        boolean canRetryCall = false;
        if (statusCode == 501 && "Method name is not valid".equals(error.getMessage())) {
            failedOriginalMethod = getPiwigoMethod();
            if(!failedOriginalMethod.startsWith("pwg.")) {
                getPiwigoSessionDetails().onMethodNotAvailable(getPiwigoMethod());
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
            PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse(this, uri, statusCode, errorDetail[0], errorDetail[1], error, isCached);
            r.setResponse(responseBody != null ? new String(responseBody) : "");
            storeResponse(r);
        }
        return canRetryCall;
    }

    protected String getRequestURIAsStr() {
        URI requestUri = getRequestURI();
        if(requestUri == null) {
            requestUri = getNestedRequestURI();
        }
        return requestUri != null ? requestUri.toString() : "N/A";
    }

    @Override
    public RequestHandle runCall(CachingAsyncHttpClient client, AsyncHttpResponseHandler handler, boolean forceResponseRevalidation) {
//        String thread = Thread.currentThread().getName();
        if (BuildConfig.DEBUG) {
            Log.d(getTag(), "calling " + getPiwigoWsApiUri() + '&' + getRequestParameters().toString());
            Log.e(getTag(), "Invoking call to server (" + getRequestParameters() + ") thread from thread " + Thread.currentThread().getName());
        }
        if(isUseHttpGet()) {
            PiwigoSessionDetails sessionDetails = getPiwigoSessionDetails();
            boolean onlyUseCache = sessionDetails != null && sessionDetails.isCached();
            return client.get(getContext(), getPiwigoWsApiUri(), buildCustomCacheControlHeaders(forceResponseRevalidation, onlyUseCache), getRequestParameters(), handler);
        } else {
            //TODO get form params entity working.
//            StringEntity formParamsEntity = getJsonFormParameters();
//            if(formParamsEntity != null) {
//                return client.post(getContext(), getPiwigoWsApiUri(), formParamsEntity, ContentType.APPLICATION_JSON.getMimeType(), handler);
//            } else {
//                return client.post(getContext(), getPiwigoWsApiUri(), getRequestParameters(), handler);
//            }
            return client.post(getContext(), getPiwigoWsApiUri(), getRequestParameters(), handler);
        }
    }

    public boolean isUseHttpGet() {
        return false;
    }

    private static class BasicNameValuePairJsonAdapter extends TypeAdapter<BasicNameValuePair> {
        @Override
        public BasicNameValuePair read(JsonReader reader) throws IOException {
            BasicNameValuePair value = null;
            reader.beginObject();
            String pairName;
            String pairValue;

            while (reader.hasNext()) {
                JsonToken token = reader.peek();

                if (token.equals(JsonToken.NAME)) {
                    //get the current token
                    pairName = reader.nextName();
                    //move to next token
                    token = reader.peek();
                    pairValue = reader.nextString();
                    value = new BasicNameValuePair(pairName, pairValue);
                }
            }
            reader.endObject();
            return value;
        }

        @Override
        public void write(com.google.gson.stream.JsonWriter writer, BasicNameValuePair nameValuePair) throws IOException {
            writer.beginObject();
            writer.name(nameValuePair.getName());
            writer.value(nameValuePair.getValue());
            writer.endObject();
        }
    }
}