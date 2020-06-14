package delit.libs.http.cache;

import android.content.Context;

import com.loopj.android.http.ResponseHandlerInterface;

import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.methods.HttpUriRequest;
import cz.msebera.android.httpclient.conn.ssl.SSLConnectionSocketFactory;
import cz.msebera.android.httpclient.protocol.HttpContext;

/**
 * Created by gareth on 13/10/17.
 */

public class CachingSyncHttpClient extends CachingAsyncHttpClient {
    /**
     * Creates a new CachingAsyncHttpClient.
     *
     * @param sslConnectionSocketFactory
     */
    public CachingSyncHttpClient(SSLConnectionSocketFactory sslConnectionSocketFactory) {
        super(sslConnectionSocketFactory);
    }

    @Override
    protected RequestHandle sendRequest(HttpClient client,
                                        HttpContext httpContext, HttpUriRequest uriRequest,
                                        String contentType, ResponseHandlerInterface responseHandler,
                                        Context context) {
        if (contentType != null) {
            uriRequest.addHeader(CachingAsyncHttpClient.HEADER_CONTENT_TYPE, contentType);
        }

        responseHandler.setUseSynchronousMode(true);

        /*
         * will execute the request directly
         */
        newAsyncHttpRequest(client, httpContext, uriRequest, contentType, responseHandler, context).run();

        // Return a Request Handle that cannot be used to cancel the request
        // because it is already complete by the time this returns
        return new RequestHandle(null);
    }
}
