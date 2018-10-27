package delit.piwigoclient.piwigoApi.handlers;

import com.loopj.android.http.AsyncHttpResponseHandler;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import cz.msebera.android.httpclient.Header;
import delit.piwigoclient.piwigoApi.HttpUtils;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.CachingAsyncHttpClient;
import delit.piwigoclient.piwigoApi.http.RequestHandle;
import delit.piwigoclient.ui.events.CancelDownloadEvent;

/**
 * Created by gareth on 25/06/17.
 */

public class ImageGetToByteArrayHandler extends AbstractPiwigoDirectResponseHandler {

    private static final String TAG = "GetImgRspHdlr";
    private String resourceUrl;
    private boolean reattemptedLogin;

    public ImageGetToByteArrayHandler(String resourceUrl) {
        super(TAG);
        this.resourceUrl = resourceUrl;
        reattemptedLogin = false;
        EventBus.getDefault().register(this);
    }

    @Override
    public void onFinish() {
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onSuccess(int statusCode, Header[] headers, byte[] responseBody, boolean hasBrandNewSession) {
        Header contentTypeHeader = HttpUtils.getContentTypeHeader(headers);
        if(contentTypeHeader != null && !contentTypeHeader.getValue().startsWith("image/")) {
            boolean newLoginAcquired = false;
            if(!reattemptedLogin) {
                reattemptedLogin = true;
                // this was redirected to an http page - login failed most probable - try to force a login and retry!
                newLoginAcquired = acquireNewSessionAndRetryCallIfAcquired();
                // if we either got a new token here or another thread did, retry the original failing call.
            }
            if (newLoginAcquired) {
                // just run the original call again (another thread has retrieved a new session
                rerunCall();
            } else {
                storeResponse(new PiwigoResponseBufferingHandler.UrlErrorResponse(this, resourceUrl, 200, responseBody, "Unsupported content type", "Content-Type http response header returned - ("+contentTypeHeader.getValue()+"). image/* expected"));
                resetSuccessAsFailure();
            }
        } else {
            PiwigoResponseBufferingHandler.UrlSuccessResponse r = new PiwigoResponseBufferingHandler.UrlSuccessResponse(getMessageId(), resourceUrl, responseBody);
            storeResponse(r);
        }
    }

    @Override
    protected void storeResponse(PiwigoResponseBufferingHandler.BaseResponse response) {
        reattemptedLogin = false; // this is reset in case the handler is reused (on manual retry)
        super.storeResponse(response);
    }

    @Override
    public boolean onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error, boolean triedToGetNewSession) {
        PiwigoResponseBufferingHandler.UrlErrorResponse r = new PiwigoResponseBufferingHandler.UrlErrorResponse(this, resourceUrl, statusCode, responseBody, HttpUtils.getHttpErrorMessage(statusCode, error), error.getMessage());
        storeResponse(r);
        return triedToGetNewSession;
    }

    @Override
    public RequestHandle runCall(CachingAsyncHttpClient client, AsyncHttpResponseHandler handler) {
        if (getConnectionPrefs().isForceHttps(getSharedPrefs(), getContext()) && resourceUrl.toLowerCase().startsWith("http://")) {
            resourceUrl = resourceUrl.replaceFirst("://", "s://");
        }
        return client.get(resourceUrl, handler);
    }

    @Subscribe
    public void onEvent(CancelDownloadEvent event) {
        if (event.messageId == this.getMessageId()) {
            cancelCallAsap();
        }
    }
}
