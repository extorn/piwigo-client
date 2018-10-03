package delit.piwigoclient.piwigoApi.handlers;

import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.loopj.android.http.AsyncHttpResponseHandler;

import org.json.JSONException;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Arrays;

import cz.msebera.android.httpclient.Header;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.model.piwigo.PiwigoJsonResponse;
import delit.piwigoclient.piwigoApi.HttpUtils;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.CachingAsyncHttpClient;
import delit.piwigoclient.piwigoApi.http.RequestHandle;
import delit.piwigoclient.piwigoApi.http.RequestParams;

/**
 * Created by gareth on 25/06/17.
 */
public abstract class AbstractPiwigoWsResponseHandler extends AbstractPiwigoDirectResponseHandler {

    private final String piwigoMethod;
    private RequestParams requestParams;
    private String nestedFailureMethod;
    private Throwable nestedFailure;
    private Gson gson;

    protected AbstractPiwigoWsResponseHandler(String piwigoMethod, String tag) {
        super(tag);
        this.piwigoMethod = piwigoMethod;
    }


    public String getPiwigoMethod() {
        return piwigoMethod;
    }

    private RequestParams getRequestParameters() {
        if (requestParams == null) {
            requestParams = buildRequestParameters();
        }
        return requestParams;
    }

    protected abstract RequestParams buildRequestParameters();

    @Override
    public void clearCallDetails() {
        nestedFailureMethod = null;
        nestedFailure = null;
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
    protected void onSuccess(int statusCode, Header[] headers, byte[] responseBody, boolean hasBrandNewSession) {
        String response = null;
        try {
            int idx = -1;
            for (int i = 0; i < responseBody.length; i++) {
                if (responseBody[i] == '{') {
                    idx = i;
                    break;
                }
            }
            int jsonStartsAt = idx;
            ByteArrayInputStream jsonBis = new ByteArrayInputStream(responseBody);
            if (jsonStartsAt > 0) {
                jsonBis.skip(jsonStartsAt - 1);
            }
            PiwigoJsonResponse piwigoResponse = getGson().fromJson(new InputStreamReader(jsonBis), PiwigoJsonResponse.class);
            processJsonResponse(getMessageId(), piwigoMethod, piwigoResponse, responseBody);

        } catch (JsonSyntaxException e) {
            String responseBodyStr = new String(responseBody);
            Crashlytics.log(String.format("Json Syntax error: %1$s : %2$s", getPiwigoMethod(), responseBodyStr));
            if(!"Expected BEGIN_OBJECT but was STRING at line 1 column 1 path $".equals(e.getMessage())) {
                Crashlytics.logException(e);
            }
            boolean handled = handleLogLoginFailurePluginResponse(statusCode, headers, responseBody, e, hasBrandNewSession);
            if (!handled) {
                PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse(this, statusCode, e.getMessage());
                r.setResponse(responseBodyStr);
                storeResponse(r);
            }
        } catch (JsonIOException e) {
            String responseBodyStr = new String(responseBody);
            Crashlytics.log(String.format("Json Syntax error: %1$s : %2$s", getPiwigoMethod(), responseBodyStr));
            Crashlytics.logException(e);
            PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse(this, statusCode, e.getMessage());
            r.setResponse(responseBodyStr);
            storeResponse(r);
        }
    }

    private boolean handleLogLoginFailurePluginResponse(int statusCode, Header[] headers, byte[] responseBody, JsonSyntaxException e, boolean hasBrandNewSession) {
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
                onSuccess(statusCode, headers, actualJson, hasBrandNewSession);
                // skip remaining method code.
                return true;
            }
        }
        return false;
    }

    private void processJsonResponse(long messageId, String piwigoMethod, PiwigoJsonResponse jsonResponse, byte[] rawData) {
        try {
            if(jsonResponse != null && jsonResponse.getStat() != null) {
                switch (jsonResponse.getStat()) {
                    case "fail":
                        onPiwigoFailure(jsonResponse);
                        break;
                    case "ok":
                        onPiwigoSuccess(jsonResponse.getResult());
                        break;
                    default:
                        throw new JSONException("Unexpected piwigo response code");
                }
            } else {
                Crashlytics.log(Log.ERROR, getTag(), piwigoMethod + " onReceiveResult: \n" + getRequestParameters() + '\n');
                String rawResponseStr = new String(rawData, Charset.forName("UTF-8"));
                PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse(this, PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse.OUTCOME_UNKNOWN, rawResponseStr);
                storeResponse(r);
            }
        } catch (JSONException e) {
            Crashlytics.log(Log.ERROR, getTag(), piwigoMethod + " onReceiveResult: \n" + getRequestParameters() + '\n');
            Crashlytics.logException(e);
            String rawResponseStr = new String(rawData, Charset.forName("UTF-8"));
            PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse(this, PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse.OUTCOME_UNKNOWN, rawResponseStr);
            storeResponse(r);
        }
    }

    protected void onPiwigoFailure(PiwigoJsonResponse rsp) throws JSONException {
        setError(new Throwable(rsp.getMessage()));
        PiwigoResponseBufferingHandler.PiwigoServerErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoServerErrorResponse(this, rsp.getErr(), rsp.getMessage());
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

    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        PiwigoResponseBufferingHandler.PiwigoSuccessResponse r = new PiwigoResponseBufferingHandler.PiwigoSuccessResponse(getMessageId(), piwigoMethod, rsp);
        storeResponse(r);
    }

    // When the response returned by REST has Http response code other than '200'
    @Override
    protected void onFailure(final int statusCode, Header[] headers, byte[] responseBody, final Throwable error, boolean triedToGetNewSession) {

        if (BuildConfig.DEBUG) {
            String errorBody = "<NONE PRESENT>";
            if (responseBody != null) {
                errorBody = new String(responseBody);
            }

            StringBuilder msgBuilder = new StringBuilder();
            msgBuilder.append("Method (failed):");
            msgBuilder.append(piwigoMethod);
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
        String errorMsg = HttpUtils.getHttpErrorMessage(statusCode, error);
        if (getNestedFailureMethod() != null) {
            errorMsg = getNestedFailureMethod() + " : " + errorMsg;
        } else {
            errorMsg = getPiwigoMethod() + " : " + errorMsg;
        }
        String errorDetail = error != null ? error.getMessage() : "";
        PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse(this, statusCode, errorMsg, errorDetail);
        r.setResponse(responseBody != null ? new String(responseBody) : "");
        storeResponse(r);
    }

    @Override
    public RequestHandle runCall(CachingAsyncHttpClient client, AsyncHttpResponseHandler handler) {
//        String thread = Thread.currentThread().getName();
        if (BuildConfig.DEBUG) {
            Log.d(getTag(), "calling " + getPiwigoWsApiUri() + '&' + getRequestParameters().toString());
            Log.e(getTag(), "Invoking call to server (" + getRequestParameters() + ") thread from thread " + Thread.currentThread().getName());
        }
        return client.post(getPiwigoWsApiUri(), getRequestParameters(), handler);
    }
}