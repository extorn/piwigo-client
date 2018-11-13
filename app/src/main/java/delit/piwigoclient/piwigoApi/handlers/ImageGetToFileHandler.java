package delit.piwigoclient.piwigoApi.handlers;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.HttpStatus;
import cz.msebera.android.httpclient.StatusLine;
import cz.msebera.android.httpclient.client.HttpResponseException;
import delit.piwigoclient.piwigoApi.HttpUtils;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.CachingAsyncHttpClient;
import delit.piwigoclient.piwigoApi.http.RequestHandle;
import delit.piwigoclient.ui.events.CancelDownloadEvent;

/**
 * Created by gareth on 25/06/17.
 */

public class ImageGetToFileHandler extends AbstractPiwigoDirectResponseHandler {

    private static final String TAG = "GetImgToFileRspHdlr";
    private final File outputFile;
    private String resourceUrl;

    public ImageGetToFileHandler(String resourceUrl, File outputFile) {
        super(TAG);
        this.resourceUrl = resourceUrl;
        this.outputFile = outputFile;
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
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                writeResponseDataToFile(response.getEntity());
            }
            // additional cancellation check as getResponseData() can take non-zero time to process
            if (!Thread.currentThread().isInterrupted()) {
                if (isCancelCallAsap()) {
                    sendFailureMessage(-1, null, null, null);
                } else if (status.getStatusCode() >= 300) {
                    sendFailureMessage(status.getStatusCode(), response.getAllHeaders(), null, new HttpResponseException(status.getStatusCode(), status.getReasonPhrase()));
                } else {
                    sendSuccessMessage(status.getStatusCode(), response.getAllHeaders(), null);
                }
            }
        }
    }

    public void writeResponseDataToFile(HttpEntity entity) throws IOException {

        InputStream is = entity.getContent();
        FileOutputStream fos = new FileOutputStream(outputFile);
        BufferedOutputStream bos = new BufferedOutputStream(fos);
        BufferedInputStream bis = new BufferedInputStream(is);
        try {
            long contentLength = entity.getContentLength();
            int buffersize = (contentLength > BUFFER_SIZE) ? BUFFER_SIZE : (int) contentLength;
            int progress = -1;
            long totalBytesRead = 0;
            if (contentLength >= 0) {
                progress = 0;
                onDownloadProgress(progress);
            }
            byte[] buffer = new byte[buffersize];
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
        } finally {
            //TODO why would I have written this block?
//            if (!cancelOperationAsap) {
//                client.cancelAllRequests(true);
//                client.getHttpClient().getConnectionManager().shutdown();
//            }
            AsyncHttpClient.silentCloseInputStream(bis);
            AsyncHttpClient.endEntityViaReflection(entity);
            AsyncHttpClient.silentCloseOutputStream(bos);
            if (isCancelCallAsap()) {
                outputFile.delete();
            }
        }
    }

    public void onDownloadProgress(int progressPercent) {
        PiwigoResponseBufferingHandler.UrlProgressResponse r = new PiwigoResponseBufferingHandler.UrlProgressResponse(getMessageId(), resourceUrl, progressPercent);
        storeResponse(r);
    }

    @Override
    public void onSuccess(int statusCode, Header[] headers, byte[] responseBody, boolean hasBrandNewSession) {
        PiwigoResponseBufferingHandler.UrlToFileSuccessResponse r = new PiwigoResponseBufferingHandler.UrlToFileSuccessResponse(getMessageId(), resourceUrl, outputFile);
        storeResponse(r);
    }

    @Override
    public boolean onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error, boolean triedToGetNewSession, boolean isCached) {
        if (statusCode != -1) {
            PiwigoResponseBufferingHandler.UrlErrorResponse r = new PiwigoResponseBufferingHandler.UrlErrorResponse(this, resourceUrl, statusCode, responseBody, HttpUtils.getHttpErrorMessage(statusCode, error), error.getMessage());
            storeResponse(r);
        } else {
            // user cancelled request.
            PiwigoResponseBufferingHandler.UrlCancelledResponse r = new PiwigoResponseBufferingHandler.UrlCancelledResponse(getMessageId(), resourceUrl);
            storeResponse(r);
        }
        return triedToGetNewSession;
    }

    @Override
    public RequestHandle runCall(CachingAsyncHttpClient client, AsyncHttpResponseHandler handler) {
        if (getConnectionPrefs().isForceHttps(getSharedPrefs(), getContext()) && resourceUrl.startsWith("http://")) {
            resourceUrl = resourceUrl.replaceFirst("://", "s://");
        }
        return client.get(getContext(), getPiwigoWsApiUri(), buildOfflineAccessHeaders(), null, handler);
    }

    @Subscribe
    public void onEvent(CancelDownloadEvent event) {
        if (event.messageId == getMessageId()) {
            cancelCallAsap();
        }
    }

}
