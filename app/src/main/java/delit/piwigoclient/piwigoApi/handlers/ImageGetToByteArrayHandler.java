package delit.piwigoclient.piwigoApi.handlers;

import android.net.Uri;
import android.util.Log;

import androidx.core.content.MimeTypeFilter;

import com.loopj.android.http.AsyncHttpResponseHandler;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.net.URI;

import cz.msebera.android.httpclient.Header;
import delit.libs.core.util.Logging;
import delit.libs.http.cache.CachingAsyncHttpClient;
import delit.libs.http.cache.RequestHandle;
import delit.libs.util.http.HttpUtils;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.ui.events.CancelDownloadEvent;
import delit.piwigoclient.util.UriUtils;

/**
 * Created by gareth on 25/06/17.
 */

public class ImageGetToByteArrayHandler extends AbstractPiwigoDirectResponseHandler {

    private static final String TAG = "GetImgRspHdlr";
    private String resourceUrl;

    public ImageGetToByteArrayHandler(String resourceUrl) {
        super(TAG);
        this.resourceUrl = resourceUrl;
        EventBus.getDefault().register(this);
    }

    @Override
    public void onFinish() {
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onSuccess(int statusCode, Header[] headers, byte[] responseBody, boolean hasBrandNewSession, boolean isResponseCached) {
        Header contentTypeHeader = HttpUtils.getContentTypeHeader(headers);
        if(contentTypeHeader != null && !MimeTypeFilter.matches(contentTypeHeader.getValue(),"image/*")) {
            boolean newLoginAcquired = false;
            if(!isTriedLoggingInAgain()) {
                // this was redirected to an http page - login failed most probable - try to force a login and retry!
                newLoginAcquired = acquireNewSessionAndRetryCallIfAcquired();
            }
            if (!newLoginAcquired) {
                resetSuccessAsFailure();
                storeResponse(new PiwigoResponseBufferingHandler.UrlErrorResponse(this, resourceUrl, 200, responseBody, "Unsupported content type", "Content-Type http response header returned - ("+contentTypeHeader.getValue()+"). image/* expected"));
            }
        } else {
            PiwigoResponseBufferingHandler.UrlSuccessResponse r = new PiwigoResponseBufferingHandler.UrlSuccessResponse(getMessageId(), resourceUrl, responseBody);
            storeResponse(r);
        }
    }

    @Override
    public boolean onFailure(String uri, int statusCode, Header[] headers, byte[] responseBody, Throwable error, boolean triedToGetNewSession, boolean isCached) {
        String[] errorDetails = HttpUtils.getHttpErrorMessage(getContext(), statusCode, error);
        PiwigoResponseBufferingHandler.UrlErrorResponse r = new PiwigoResponseBufferingHandler.UrlErrorResponse(this, resourceUrl, statusCode, responseBody, errorDetails[0], errorDetails[1]);
        storeResponse(r);
        return triedToGetNewSession;
    }

    @Override
    public RequestHandle runCall(CachingAsyncHttpClient client, AsyncHttpResponseHandler handler, boolean forceResponseRevalidation) {

        boolean isPerformUriPathSegmentEncoding = ConnectionPreferences.getActiveProfile().isPerformUriPathSegmentEncoding(getSharedPrefs(), getContext());
        boolean forceHttps = getConnectionPrefs().isForceHttps(getSharedPrefs(), getContext());
        boolean testForExposingProxiedServer = getConnectionPrefs().isWarnInternalUriExposed(getSharedPrefs(), getContext());
        String uri = UriUtils.sanityCheckFixAndReportUri(resourceUrl, getPiwigoServerUrl(), forceHttps, testForExposingProxiedServer, getConnectionPrefs());
        if (isPerformUriPathSegmentEncoding) {
            try {
                URI.create(uri);
            } catch(IllegalArgumentException e) {
                Logging.log(Log.WARN, TAG, "IllegalUriFixed : " + uri);
                Logging.recordException(e);
                uri = UriUtils.encodeUriSegments(Uri.parse(uri));
            }
        }
        PiwigoSessionDetails sessionDetails = getPiwigoSessionDetails();
        boolean onlyUseCache = sessionDetails != null && sessionDetails.isCached();
        return client.get(getContext(), uri, buildCustomCacheControlHeaders(forceResponseRevalidation, onlyUseCache), null, handler);
    }

    @Subscribe
    public void onEvent(CancelDownloadEvent event) {
        if (event.messageId == this.getMessageId()) {
            cancelCallAsap();
        }
    }

    public boolean isUseHttpGet() {
        return true;
    }
}
