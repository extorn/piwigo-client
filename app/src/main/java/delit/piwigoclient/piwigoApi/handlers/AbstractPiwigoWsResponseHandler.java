package delit.piwigoclient.piwigoApi.handlers;

import android.util.Log;

import com.loopj.android.http.AsyncHttpResponseHandler;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;

import cz.msebera.android.httpclient.Header;
import delit.piwigoclient.BuildConfig;
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
    public void onSuccess(int statusCode, Header[] headers, byte[] responseBody, boolean hasBrandNewSession) {
        String response = null;
        try {
            response = responseBody == null ? null : new String(responseBody, getCharset());
            processJsonResponse(getMessageId(), piwigoMethod, response);

        } catch (UnsupportedEncodingException e) {
            if (BuildConfig.DEBUG) {
                Log.e(getTag(), "onSuccess: ", e);
            }
            PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse(getMessageId(), piwigoMethod, statusCode, e.getMessage());
            storeResponse(r);
        }
    }

    private void processJsonResponse(long messageId, String piwigoMethod, String jsonResponse) {
        // JSON Object
        JSONObject rsp;
        String status;
        try {
            rsp = new JSONObject(jsonResponse);
            status = rsp.getString("stat");
        } catch (JSONException e) {
            if (BuildConfig.DEBUG) {
                Log.e(getTag(), "onReceiveResult: ", e);
            }
            PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse(messageId, piwigoMethod, PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse.OUTCOME_UNKNOWN, jsonResponse);
            storeResponse(r);
            return;
        }
        if (status.equals("fail")) {
            try {
                if (BuildConfig.DEBUG) {
                    Log.e(getTag(), "onReceiveResult: " + jsonResponse);
                }
                int errorCode = rsp.getInt("err");
                String errorMessage = rsp.getString("message");
                PiwigoResponseBufferingHandler.PiwigoServerErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoServerErrorResponse(messageId, piwigoMethod, errorCode, errorMessage);
                storeResponse(r);
            } catch (JSONException e) {
                if (BuildConfig.DEBUG) {
                    Log.e(getTag(), "onReceiveResult: ", e);
                }
                PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse(messageId, piwigoMethod, PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse.OUTCOME_FAILED, jsonResponse);
                storeResponse(r);
            }
        } else if (status.equals("ok")) {
            try {
                onPiwigoSuccess(rsp);
            } catch (JSONException e) {
                if (BuildConfig.DEBUG) {
                    Log.e(getTag(), "onReceiveResult: ", e);
                }
                PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse(messageId, piwigoMethod, PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse.OUTCOME_SUCCESS, jsonResponse);
                storeResponse(r);
            }
        } else {
            if (BuildConfig.DEBUG) {
                Log.e(getTag(), "onReceiveResult: " + jsonResponse);
            }
            PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse(messageId, piwigoMethod, PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse.OUTCOME_FAILED, jsonResponse);
            storeResponse(r);
        }
    }

    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {
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
            Log.e(getTag(), "onFailure: " + errorBody, error);
        }
        String errorMsg = HttpUtils.getHttpErrorMessage(statusCode, error);
        PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse(getMessageId(), piwigoMethod, statusCode, errorMsg, error.getMessage());
        storeResponse(r);
    }

    @Override
    public RequestHandle runCall(CachingAsyncHttpClient client, AsyncHttpResponseHandler handler) {
        String thread = Thread.currentThread().getName();
        return client.post(getPiwigoWsApiUri(), getRequestParameters(), handler);
    }

}