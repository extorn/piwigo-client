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
    private String nestedFailureMethod;

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

    @Override
    public void onSuccess(int statusCode, Header[] headers, byte[] responseBody, boolean hasBrandNewSession) {
        String response = null;
        try {
            response = responseBody == null ? null : new String(responseBody, getCharset());
            processJsonResponse(getMessageId(), piwigoMethod, response);

        } catch (UnsupportedEncodingException e) {
            if (BuildConfig.DEBUG) {
                Log.e(getTag(), piwigoMethod + " onSuccess: " + response, e);
            }
            PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse(this, statusCode, e.getMessage());
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
                Log.e(getTag(), piwigoMethod + " onReceiveResult: \n" + getRequestParameters() + '\n', e);
            }
            PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse(this, PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse.OUTCOME_UNKNOWN, jsonResponse);
            storeResponse(r);
            return;
        }
        switch (status) {
            case "fail":
                try {
                    onPiwigoFailure(rsp);
                } catch (JSONException e) {
                    if (BuildConfig.DEBUG) {
                        Log.e(getTag(), piwigoMethod + " onReceiveResult: \n" + getRequestParameters() + '\n', e);
                    }
                    PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse(this, PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse.OUTCOME_FAILED, jsonResponse);
                    storeResponse(r);
                }
                break;
            case "ok":
                try {
                    onPiwigoSuccess(rsp);
                } catch (JSONException e) {
                    if (BuildConfig.DEBUG) {
                        Log.e(getTag(), piwigoMethod + " onReceiveResult: \n" + getRequestParameters() + '\n', e);
                    }
                    PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse(this, PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse.OUTCOME_SUCCESS, jsonResponse);
                    storeResponse(r);
                }
                break;
            default:
                if (BuildConfig.DEBUG) {
                    Log.e(getTag(), piwigoMethod + " onReceiveResult: \n" + getRequestParameters() + '\n' + jsonResponse);
                }
                PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse(this, PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse.OUTCOME_FAILED, jsonResponse);
                storeResponse(r);
                break;
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

    protected void onPiwigoFailure(JSONObject rsp) throws JSONException {
        int errorCode = rsp.getInt("err");
        String errorMessage = rsp.getString("message");
        PiwigoResponseBufferingHandler.PiwigoServerErrorResponse r = new PiwigoResponseBufferingHandler.PiwigoServerErrorResponse(this, errorCode, errorMessage);
        storeResponse(r);
    }

    protected void reportNestedFailure(AbstractBasicPiwigoResponseHandler nestedHandler) {
        if(nestedHandler instanceof AbstractPiwigoWsResponseHandler) {
            nestedFailureMethod = ((AbstractPiwigoWsResponseHandler)nestedHandler).getPiwigoMethod();
        }
        super.reportNestedFailure(nestedHandler);
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