package delit.piwigoclient.piwigoApi.http;

import android.os.SystemClock;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashSet;

import javax.net.ssl.SSLException;

import cz.msebera.android.httpclient.NoHttpResponseException;
import cz.msebera.android.httpclient.client.HttpRequestRetryHandler;
import cz.msebera.android.httpclient.client.methods.HttpUriRequest;
import cz.msebera.android.httpclient.protocol.HttpContext;
import cz.msebera.android.httpclient.protocol.HttpCoreContext;

class RetryHandler implements HttpRequestRetryHandler {
    private final static HashSet<Class<?>> exceptionWhitelist = new HashSet<>();
    private final static HashSet<Class<?>> exceptionBlacklist = new HashSet<>();

    static {
        // Retry if the server dropped connection on us
        exceptionWhitelist.add(NoHttpResponseException.class);
        // retry-this, since it may happens as part of a Wi-Fi to 3G failover
        exceptionWhitelist.add(UnknownHostException.class);
        // retry-this, since it may happens as part of a Wi-Fi to 3G failover
        exceptionWhitelist.add(SocketException.class);

        // never retry timeouts
        exceptionBlacklist.add(InterruptedIOException.class);
        // never retry SSL handshake failures
        exceptionBlacklist.add(SSLException.class);
    }

    private final int maxRetries;
    private final int retrySleepTimeMS;

    public RetryHandler(int maxRetries, int retrySleepTimeMS) {
        this.maxRetries = maxRetries;
        this.retrySleepTimeMS = retrySleepTimeMS;
    }

    static void addClassToWhitelist(Class<?> cls) {
        exceptionWhitelist.add(cls);
    }

    static void addClassToBlacklist(Class<?> cls) {
        exceptionBlacklist.add(cls);
    }

    @Override
    public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
        boolean retry = true;

        Boolean b = (Boolean) context.getAttribute(HttpCoreContext.HTTP_REQ_SENT);
        boolean sent = (b != null && b);

        if (executionCount > maxRetries) {
            // Do not retry if over max retry count
            retry = false;
        } else if (isInList(exceptionWhitelist, exception)) {
            // immediately retry if error is whitelisted
            retry = true;
        } else if (isInList(exceptionBlacklist, exception)) {
            // immediately cancel retry if the error is blacklisted
            retry = false;
        } else if (!sent) {
            // for most other errors, retry only if request hasn't been fully sent yet
            retry = true;
        }

        if (retry) {
            // resend all idempotent requests
            HttpUriRequest currentReq = (HttpUriRequest) context.getAttribute(HttpCoreContext.HTTP_REQUEST);
            if (currentReq == null) {
                return false;
            }
        }

        if (retry) {
            SystemClock.sleep(retrySleepTimeMS);
        } else {
            exception.printStackTrace();
        }

        return retry;
    }

    protected boolean isInList(HashSet<Class<?>> list, Throwable error) {
        for (Class<?> aList : list) {
            if (aList.isAssignableFrom(error.getClass())) {
                return true;
            }
        }
        return false;
    }
}