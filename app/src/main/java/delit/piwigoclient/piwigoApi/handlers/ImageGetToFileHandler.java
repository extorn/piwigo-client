package delit.piwigoclient.piwigoApi.handlers;

import android.net.Uri;
import android.util.Log;

import androidx.core.content.MimeTypeFilter;
import androidx.documentfile.provider.DocumentFile;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.HttpStatus;
import cz.msebera.android.httpclient.StatusLine;
import cz.msebera.android.httpclient.client.HttpResponseException;
import cz.msebera.android.httpclient.util.ByteArrayBuffer;
import delit.libs.core.util.Logging;
import delit.libs.http.cache.CachingAsyncHttpClient;
import delit.libs.http.cache.RequestHandle;
import delit.libs.util.IOUtils;
import delit.libs.util.http.HttpUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.ui.events.CancelDownloadEvent;
import delit.piwigoclient.util.UriUtils;

/**
 * Created by gareth on 25/06/17.
 */

public class ImageGetToFileHandler extends AbstractPiwigoDirectResponseHandler {

    private static final String TAG = "GetImgToFileRspHdlr";
    private final Uri outputFileUri;
    private String resourceUrl;

    public ImageGetToFileHandler(String resourceUrl, Uri outputFileUri) {
        super(TAG);
        this.resourceUrl = resourceUrl;
        this.outputFileUri = outputFileUri;
        EventBus.getDefault().register(this);
    }

    @Override
    public void onFinish() {
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void sendResponseMessage(HttpResponse response) throws IOException {
        // do not process if request has been cancelled
        if (!Thread.currentThread().isInterrupted()) {
            StatusLine status = response.getStatusLine();
            boolean isSuccess = false;
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                isSuccess = writeResponseDataToFile(response.getEntity());
            }
            // additional cancellation check as getResponseData() can take non-zero time to process
            if (!Thread.currentThread().isInterrupted()) {
                if (isCancelCallAsap()) {
                    sendFailureMessage(-1, null, null, null);
                } else if (status.getStatusCode() >= 300) {
                    sendFailureMessage(status.getStatusCode(), response.getAllHeaders(), null, new HttpResponseException(status.getStatusCode(), status.getReasonPhrase()));
                } else if(isSuccess){
                    sendSuccessMessage(status.getStatusCode(), response.getAllHeaders(), null);
                } else {
                    // capture error
                    sendFailureMessage(200, null, null, getError());
                }
            }
        }
    }

    public boolean writeResponseDataToFile(HttpEntity entity) {
        boolean isSuccess = false;

        BufferedInputStream bis = null;
        try(BufferedOutputStream bos = new BufferedOutputStream(getContext().getContentResolver().openOutputStream(outputFileUri))) {

            bis = new BufferedInputStream(entity.getContent());
            Header contentTypeHeader = entity.getContentType();
            if (contentTypeHeader != null && !MimeTypeFilter.matches(contentTypeHeader.getValue(), "image/*")) {
                boolean newLoginAcquired = false;
                if (!isTriedLoggingInAgain()) {
                    // this was redirected to an http page - login failed most probable - try to force a login and retry!
                    newLoginAcquired = acquireNewSessionAndRetryCallIfAcquired();
                }
                if (!newLoginAcquired) {
                    byte[] responseBody = getResponseBodyAsByteArray(entity);
                    resetSuccessAsFailure();
                    storeResponse(new PiwigoResponseBufferingHandler.UrlErrorResponse(this, resourceUrl, 200, responseBody, "Unsupported content type", "Content-Type http response header returned - (" + contentTypeHeader.getValue() + "). image/* expected"));
                }
                return false; // this isn't a success yet, but there's another call in progress so we're getting a second chance!
            }

            long contentLength = entity.getContentLength();
            int bufferSize = (contentLength > BUFFER_SIZE || contentLength < 1024) ? BUFFER_SIZE : (int) contentLength;
            int progress = -1;
            long totalBytesRead = 0;
            if (contentLength >= 0) {
                progress = 0;
                onDownloadProgress(progress);
            }
            byte[] buffer = new byte[bufferSize];
            int bytesRead;
            long lastProgressUpdateAt = System.currentTimeMillis();
            do {
                bytesRead = bis.read(buffer);
                if (bytesRead > 0) {
                    bos.write(buffer, 0, bytesRead);
                }
                if (bytesRead > 0) {
                    totalBytesRead += bytesRead;
                    if (progress >= 0) {
                        long currentTime = System.currentTimeMillis();
                        // report progress a maximum of once every second.
                        if (currentTime - lastProgressUpdateAt > 1000) {
                            lastProgressUpdateAt = currentTime;
                            progress = (int) Math.round(((double) totalBytesRead / contentLength) * 100);
                            onDownloadProgress(progress);
                        }
                    }
                }
                if (isCancelCallAsap()) {
                    bytesRead = -1;
                }
            } while (bytesRead >= 0);
            bos.flush();
            if (!isCancelCallAsap()) {
                if (progress >= 0) {
                    progress = 100;
                    onDownloadProgress(progress);
                }
            }
            isSuccess = true;
        } catch (Exception e) {
            setError(new IOException("Error parsing response", e));
        } finally {
            //TODO why would I have written this block?
            //            if (!cancelOperationAsap) {
            //                client.cancelAllRequests(true);
            //                client.getHttpClient().getConnectionManager().shutdown();
            //            }
            AsyncHttpClient.silentCloseInputStream(bis);
            AsyncHttpClient.endEntityViaReflection(entity);
            if (isCancelCallAsap()) {
                DocumentFile docFile = IOUtils.getSingleDocFile(getContext(), outputFileUri);
                if (docFile != null && !docFile.delete()) {
                    Logging.log(Log.ERROR, TAG, "Unable to delete partially downloaded file");
                }
            }
        }
        return isSuccess;
    }


    private byte[] getResponseBodyAsByteArray(HttpEntity entity) throws IOException {
        byte[] responseBody = null;
        if (entity != null) {
            InputStream instream = entity.getContent();
            if (instream != null) {
                long contentLength = entity.getContentLength();
                if (contentLength > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("HTTP entity too large to be buffered in memory");
                }
                int buffersize = (contentLength <= 0) ? BUFFER_SIZE : (int) contentLength;
                try {
                    ByteArrayBuffer buffer = new ByteArrayBuffer(buffersize);
                    try {
                        byte[] tmp = new byte[BUFFER_SIZE];
                        long count = 0;
                        int l;
                        // do not send messages if request has been cancelled
                        while ((l = instream.read(tmp)) != -1 && !Thread.currentThread().isInterrupted()) {
                            count += l;
                            buffer.append(tmp, 0, l);
//                            sendProgressMessage(count, (contentLength <= 0 ? 1 : contentLength));
                        }
                    } finally {
                        AsyncHttpClient.silentCloseInputStream(instream);
                        AsyncHttpClient.endEntityViaReflection(entity);
                    }
                    responseBody = buffer.toByteArray();
                } catch (OutOfMemoryError e) {
                    System.gc();
                    throw new IOException("File too large to fit into available memory");
                }
            }
        }
        return responseBody;
    }

    public void onDownloadProgress(int progressPercent) {
        if(BuildConfig.DEBUG) {
            Log.d(getTag(), String.format("download progress %1$d%%", progressPercent));
        }
        PiwigoResponseBufferingHandler.UrlProgressResponse r = new PiwigoResponseBufferingHandler.UrlProgressResponse(getMessageId(), resourceUrl, progressPercent);
        storeResponse(r);
    }

    @Override
    public void onSuccess(int statusCode, Header[] headers, byte[] responseBody, boolean hasBrandNewSession, boolean isResponseCached) {
        PiwigoResponseBufferingHandler.UrlToFileSuccessResponse r = new PiwigoResponseBufferingHandler.UrlToFileSuccessResponse(getMessageId(), resourceUrl, outputFileUri);
        storeResponse(r);
    }

    @Override
    public boolean onFailure(String uri, int statusCode, Header[] headers, byte[] responseBody, Throwable error, boolean triedToGetNewSession, boolean isCached) {
        if (statusCode != -1) {
            String[] errorDetails = HttpUtils.getHttpErrorMessage(getContext(), statusCode, error);
            PiwigoResponseBufferingHandler.UrlErrorResponse r = new PiwigoResponseBufferingHandler.UrlErrorResponse(this, resourceUrl, statusCode, responseBody, errorDetails[0], errorDetails[1]);
            storeResponse(r);
        } else {
            // user cancelled request.
            PiwigoResponseBufferingHandler.UrlCancelledResponse r = new PiwigoResponseBufferingHandler.UrlCancelledResponse(getMessageId(), resourceUrl);
            storeResponse(r);
        }
        return triedToGetNewSession;
    }

    @Override
    public RequestHandle runCall(CachingAsyncHttpClient client, AsyncHttpResponseHandler handler, boolean forceResponseRevalidation) {

        boolean forceHttps = getConnectionPrefs().isForceHttps(getSharedPrefs(), getContext());
        boolean testForExposingProxiedServer = getConnectionPrefs().isWarnInternalUriExposed(getSharedPrefs(), getContext());
        String uri = UriUtils.sanityCheckFixAndReportUri(resourceUrl, getPiwigoServerUrl(), forceHttps, testForExposingProxiedServer, getConnectionPrefs());

        PiwigoSessionDetails sessionDetails = getPiwigoSessionDetails();
        boolean onlyUseCache = sessionDetails != null && sessionDetails.isCached();
        return client.get(getContext(), uri, buildCustomCacheControlHeaders(forceResponseRevalidation, onlyUseCache), null, handler);
    }

    @Subscribe
    public void onEvent(CancelDownloadEvent event) {
        if (event.messageId == getMessageId()) {
            cancelCallAsap();
        }
    }

}
