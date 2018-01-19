package delit.piwigoclient.piwigoApi.handlers;

import android.util.Log;

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

    private String piwigoMethod;
    private RequestParams requestParams;
    private String nestedFailureMethod;
    private Gson gson;

    public AbstractPiwigoWsResponseHandler(String piwigoMethod, String tag) {
        super(tag);
        this.piwigoMethod = piwigoMethod;
    }


    public String getPiwigoMethod() {
        return piwigoMethod;
    }

    public final RequestParams getRequestParameters() {
        if(requestParams == null) {
            requestParams = buildRequestParameters();
        }
        return requestParams;
    }

    public abstract RequestParams buildRequestParameters();

    @Override
    public void clearCallDetails() {
        nestedFailureMethod = null;
        super.clearCallDetails();
    }

    public String getNestedFailureMethod() {
        return nestedFailureMethod;
    }

    protected Gson buildGson() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
        gsonBuilder.setLenient();
        Gson gson = gsonBuilder.create();
        return gson;
    }

    protected Gson getGson() {
        if(gson == null) {
            gson = buildGson();
        }
        return gson;
    }

    @Override
    public void onSuccess(int statusCode, Header[] headers, byte[] responseBody, boolean hasBrandNewSession) {
        String response = null;
        try {

            PiwigoJsonResponse piwigoResponse = getGson().fromJson(new InputStreamReader(new ByteArrayInputStream(responseBody)), PiwigoJsonResponse.class);
            processJsonResponse(getMessageId(), piwigoMethod, piwigoResponse, responseBody);

        } catch (JsonSyntaxException e) {
            boolean handled = handleLogLoginFailurePluginResponse(statusCode, headers, responseBody, e, hasBrandNewSession);
            if(!handled) {
                PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse(this, statusCode, e.getMessage());
                storeResponse(r);
            }
        } catch (JsonIOException e) {
            PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse(this, statusCode, e.getMessage());
            storeResponse(r);
        }
    }

    private boolean handleLogLoginFailurePluginResponse(int statusCode, Header[] headers, byte[] responseBody, JsonSyntaxException e, boolean hasBrandNewSession) {
        if(e.getMessage().equals("java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1 path $")) {
            int idx = 0;
            for(int i = responseBody.length - 1; i > 0; i--) {
                if('{' == responseBody[i]) {
                    idx = i;
                    break;
                }
            }
            if(idx > 0) {
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
        } catch (JSONException e) {
            if (BuildConfig.DEBUG) {
                Log.e(getTag(), piwigoMethod + " onReceiveResult: \n" + getRequestParameters() + '\n', e);
            }
            String rawResponseStr = new String(rawData, Charset.forName("UTF-8"));
            PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse(this, PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse.OUTCOME_UNKNOWN, rawResponseStr);
            storeResponse(r);
            return;
        }
    }

    protected void runAndWaitForHandlerToFinish(AbstractPiwigoWsResponseHandler handler) {
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

    protected void onPiwigoFailure(PiwigoJsonResponse rsp) throws JSONException {
        PiwigoResponseBufferingHandler.PiwigoServerErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoServerErrorResponse(this, rsp.getErr(), rsp.getMessage());
        storeResponse(r);
    }

    protected void reportNestedFailure(AbstractBasicPiwigoResponseHandler nestedHandler) {
        if(nestedHandler instanceof AbstractPiwigoWsResponseHandler) {
            nestedFailureMethod = ((AbstractPiwigoWsResponseHandler)nestedHandler).getPiwigoMethod();
        }
        super.reportNestedFailure(nestedHandler);
    }

    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        PiwigoResponseBufferingHandler.PiwigoSuccessResponse r = new PiwigoResponseBufferingHandler.PiwigoSuccessResponse(getMessageId(), piwigoMethod, rsp);
        storeResponse(r);
    }

    // When the response returned by REST has Http response code other than '200'
    @Override
    public void onFailure(final int statusCode, Header[] headers, byte[] responseBody, final Throwable error, boolean triedToGetNewSession) {

        if (BuildConfig.DEBUG) {
            String errorBody = null;
            if(responseBody != null) {
                errorBody = new String(responseBody);
            }

            if(getNestedFailureMethod() != null) {
                Log.e(getTag(), getNestedFailureMethod() + " onFailure: \n" + errorBody, error);
            } else {
                Log.e(getTag(), piwigoMethod + " onFailure: \n" + getRequestParameters() + '\n' + errorBody, error);
            }
        }
        String errorMsg = HttpUtils.getHttpErrorMessage(statusCode, error);
        if(getNestedFailureMethod() != null) {
            errorMsg = getNestedFailureMethod() + " : " + errorMsg;
        } else {
            errorMsg = getPiwigoMethod() + " : " + errorMsg;
        }
        String errorDetail = error != null ? error.getMessage() : "";
        PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse(this, statusCode, errorMsg, errorDetail);
        storeResponse(r);
    }

    @Override
    public RequestHandle runCall(CachingAsyncHttpClient client, AsyncHttpResponseHandler handler) {
        String thread = Thread.currentThread().getName();
        return client.post(getPiwigoWsApiUri(), getRequestParameters(), handler);
    }

}