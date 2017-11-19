package delit.piwigoclient.business.video;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSourceException;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Predicate;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.DataAsyncHttpResponseHandler;
import com.loopj.android.http.PersistentCookieStore;

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.InvalidClassException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.message.BasicHeader;
import delit.piwigoclient.piwigoApi.HttpClientFactory;
import delit.piwigoclient.piwigoApi.http.CachingSyncHttpClient;

/**
 * An {@link HttpDataSource} that uses Android's {@link HttpURLConnection}.
 * <p>
 * By default this implementation will not follow cross-protocol redirects (i.e. redirects from
 * HTTP to HTTPS or vice versa). Cross-protocol redirects can be enabled by using the
 * {@link #HttpClientBasedHttpDataSource(Context, String, Predicate, TransferListener, int, int, boolean,
 * RequestProperties, boolean, boolean)} constructor and passing {@code true} as the second last argument.
 */
public class HttpClientBasedHttpDataSource implements HttpDataSource {

    /**
     * The default connection timeout, in milliseconds.
     */
    public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 8 * 1000;
    /**
     * The default read timeout, in milliseconds.
     */
    public static final int DEFAULT_READ_TIMEOUT_MILLIS = 8 * 1000;

    private static final String TAG = "DefaultHttpDataSource";
    private static final Pattern CONTENT_RANGE_HEADER =
            Pattern.compile("^bytes (\\d+)-(\\d+)/(\\d+)$");
    private static final AtomicReference<byte[]> skipBufferReference = new AtomicReference<>();

    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final String userAgent;
    private final Predicate<String> contentTypePredicate;
    private final RequestProperties requestProperties;
    private final TransferListener<? super HttpClientBasedHttpDataSource> listener;
    private final boolean cachingEnabled;
    private boolean notifyCacheListenerImmediatelyIfCached;
    private PersistentCookieStore cookieStore;
    private CachingSyncHttpClient client;
    private final Context context;

    private DataSpec dataSpec;
    private InputStream inputStream;
    private boolean opened;

    private long bytesToSkip;
    private long bytesToRead;

    private long bytesSkipped;
    private long bytesRead;
    private HttpResponse httpResponse;
    private String connectedToFile;
    private RandomAccessFile localCacheFile;
    private CachedContent cacheFileContent;
    private CacheListener cacheListener;
    private CachedContent.SerializableRange cachedRange;


    /**
     * @param userAgent            The User-Agent string that should be used.
     * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
     *                             predicate then a {@link HttpDataSource.InvalidContentTypeException} is thrown from
     *                             {@link #open(DataSpec)}.
     */
    public HttpClientBasedHttpDataSource(Context context, String userAgent, Predicate<String> contentTypePredicate) {
        this(context, userAgent, contentTypePredicate, null);
    }

    /**
     * @param userAgent            The User-Agent string that should be used.
     * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
     *                             predicate then a {@link HttpDataSource.InvalidContentTypeException} is thrown from
     *                             {@link #open(DataSpec)}.
     * @param listener             An optional listener.
     */
    public HttpClientBasedHttpDataSource(Context context, String userAgent, Predicate<String> contentTypePredicate,
                                         TransferListener<? super HttpClientBasedHttpDataSource> listener) {
        this(context, userAgent, contentTypePredicate, listener, DEFAULT_CONNECT_TIMEOUT_MILLIS,
                DEFAULT_READ_TIMEOUT_MILLIS);
    }

    /**
     * @param userAgent            The User-Agent string that should be used.
     * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
     *                             predicate then a {@link HttpDataSource.InvalidContentTypeException} is thrown from
     *                             {@link #open(DataSpec)}.
     * @param listener             An optional listener.
     * @param connectTimeoutMillis The connection timeout, in milliseconds. A timeout of zero is
     *                             interpreted as an infinite timeout.
     * @param readTimeoutMillis    The read timeout, in milliseconds. A timeout of zero is interpreted
     *                             as an infinite timeout.
     */
    public HttpClientBasedHttpDataSource(Context context, String userAgent, Predicate<String> contentTypePredicate,
                                         TransferListener<? super HttpClientBasedHttpDataSource> listener, int connectTimeoutMillis,
                                         int readTimeoutMillis) {
        this(context, userAgent, contentTypePredicate, listener, connectTimeoutMillis, readTimeoutMillis, false,
                null, false, false);
    }

    /**
     * @param userAgent                   The User-Agent string that should be used.
     * @param contentTypePredicate        An optional {@link Predicate}. If a content type is rejected by the
     *                                    predicate then a {@link HttpDataSource.InvalidContentTypeException} is thrown from
     *                                    {@link #open(DataSpec)}.
     * @param listener                    An optional listener.
     * @param connectTimeoutMillis        The connection timeout, in milliseconds. A timeout of zero is
     *                                    interpreted as an infinite timeout. Pass {@link #DEFAULT_CONNECT_TIMEOUT_MILLIS} to use
     *                                    the default value.
     * @param readTimeoutMillis           The read timeout, in milliseconds. A timeout of zero is interpreted
     *                                    as an infinite timeout. Pass {@link #DEFAULT_READ_TIMEOUT_MILLIS} to use the default value.
     * @param allowCrossProtocolRedirects Whether cross-protocol redirects (i.e. redirects from HTTP
     *                                    to HTTPS and vice versa) are enabled.
     * @param defaultRequestProperties    The default request properties to be sent to the server as
     *                                    HTTP headers or {@code null} if not required.
     */
    public HttpClientBasedHttpDataSource(Context context, String userAgent, Predicate<String> contentTypePredicate,
                                         TransferListener<? super HttpClientBasedHttpDataSource> listener, int connectTimeoutMillis,
                                         int readTimeoutMillis, boolean allowCrossProtocolRedirects,
                                         RequestProperties defaultRequestProperties, boolean cachingEnabled, boolean notifyCacheListenerImmediatelyIfCached) {
        this.userAgent = Assertions.checkNotEmpty(userAgent);
        this.contentTypePredicate = contentTypePredicate;
        this.listener = listener;
        this.requestProperties = new RequestProperties();
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        RequestProperties defaultRequestProperties1 = defaultRequestProperties;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        this.context = context;
        this.cachingEnabled = cachingEnabled;
        this.notifyCacheListenerImmediatelyIfCached = notifyCacheListenerImmediatelyIfCached;
        startClient();
    }

    private void startClient() {
        if(client == null) {
            client = HttpClientFactory.getInstance(context).buildVideoDownloadSyncHttpClient();
        }
    }

    public void setCacheListener(CacheListener cacheListener) {
        this.cacheListener = cacheListener;
    }

    @Override
    public Uri getUri() {

        if (httpResponse == null) {
            return null;
        } else {
            Header header = httpResponse.getFirstHeader("Location");
            if (header == null) {
                return dataSpec.uri;
            }
            return Uri.parse(header.getValue());
        }
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return getHeaderFields();
    }

    @Override
    public void setRequestProperty(String name, String value) {
        Assertions.checkNotNull(name);
        Assertions.checkNotNull(value);
        requestProperties.set(name, value);
    }

    @Override
    public void clearRequestProperty(String name) {
        Assertions.checkNotNull(name);
        requestProperties.remove(name);
    }

    @Override
    public void clearAllRequestProperties() {
        requestProperties.clear();
    }

    @Override
    public long open(DataSpec dataSpec) throws HttpDataSourceException {

        if(cachingEnabled) {
            long bytesAvailable = loadCache(dataSpec);

            if(cacheListener != null && notifyCacheListenerImmediatelyIfCached && cacheFileContent.isComplete()) {
                notifyCacheListenerImmediatelyIfCached = false;
                cacheListener.onFullyCached(cacheFileContent.getCachedDataFile());
            }

            if(bytesAvailable > 0) {

                this.dataSpec = dataSpec;
                this.bytesRead = 0;
                this.bytesSkipped = 0;

                return bytesAvailable;
            }
        }


        startClient(); //TODO pointless as occurs when object is created.
        this.dataSpec = dataSpec;
        this.bytesRead = 0;
        this.bytesSkipped = 0;
        try {
            makeConnection(dataSpec);
        } catch (IOException e) {
            throw new HttpDataSourceException("Unable to connect to " + dataSpec.uri.toString(), e,
                    dataSpec, HttpDataSourceException.TYPE_OPEN);
        }

        int responseCode;
        try {
            responseCode = httpResponse.getStatusLine().getStatusCode();
        } catch (NullPointerException e) {
            closeConnectionQuietly();
            throw new HttpDataSourceException("Unable to connect to " + dataSpec.uri.toString(), new IOException(e),
                    dataSpec, HttpDataSourceException.TYPE_OPEN);
        }

        // Check for a valid response code.
        if (responseCode < 200 || responseCode > 299) {
            Map<String, List<String>> headers = getHeaderFields();
            closeConnectionQuietly();
            InvalidResponseCodeException exception =
                    new InvalidResponseCodeException(responseCode, headers, dataSpec);
            if (responseCode == 416) {
                exception.initCause(new DataSourceException(DataSourceException.POSITION_OUT_OF_RANGE));
            }
            throw exception;
        }

        // Check for a valid content type.
        Header contentTypeHeader = httpResponse.getEntity().getContentType();
        String contentType = contentTypeHeader.getValue();
        if (contentTypePredicate != null && !contentTypePredicate.evaluate(contentType)) {
            closeConnectionQuietly();
            throw new InvalidContentTypeException(contentType, dataSpec);
        }

        // If we requested a range starting from a non-zero position and received a 200 rather than a
        // 206, then the server does not support partial requests. We'll need to manually skip to the
        // requested position.
        bytesToSkip = responseCode == 200 && dataSpec.position != 0 ? dataSpec.position : 0;

        // Determine the length of the data to be read, after skipping.
        if (!dataSpec.isFlagSet(DataSpec.FLAG_ALLOW_GZIP)) {
            if (dataSpec.length != C.LENGTH_UNSET) {
                bytesToRead = dataSpec.length;
            } else {
                long contentLength = getContentLength();
                bytesToRead = contentLength != C.LENGTH_UNSET ? (contentLength - bytesToSkip)
                        : C.LENGTH_UNSET;
            }
        } else {
            // Gzip is enabled. If the server opts to use gzip then the content length in the response
            // will be that of the compressed data, which isn't what we want. Furthermore, there isn't a
            // reliable way to determine whether the gzip was used or not. Always use the dataSpec length
            // in this case.
            bytesToRead = dataSpec.length;
        }

        try {
            inputStream = httpResponse.getEntity().getContent();
        } catch (IOException e) {
            closeConnectionQuietly();
            throw new HttpDataSourceException(e, dataSpec, HttpDataSourceException.TYPE_OPEN);
        }

        opened = true;
        if (listener != null) {
            listener.onTransferStart(this, dataSpec);
        }

        return bytesToRead;
    }

    private long loadCache(DataSpec dataSpec) throws HttpDataSourceException {
        try {
            connectedToFile = CacheUtils.getVideoFilename(dataSpec.uri.toString());
            File cacheFileMetadataFile = CacheUtils.getCacheMetadataFile(context, connectedToFile);
            File cacheDataFile = CacheUtils.getCacheDataFile(context, connectedToFile);

            if(cacheFileMetadataFile.exists()) {
                if(!cacheDataFile.exists()) {
                    // cache is corrupt - delete meta data file
                    cacheFileMetadataFile.delete();
                } else {
                    localCacheFile = new RandomAccessFile(cacheDataFile, "rw");
                    cacheFileContent = CacheUtils.loadCachedContent(cacheFileMetadataFile);
                    cachedRange = cacheFileContent.getRangeContaining(dataSpec.position);

                    if (cachedRange != null) {
                        return cachedRange.available(dataSpec.position);
                    } else {
                        return 0;
                    }
                }
            }

            localCacheFile = new RandomAccessFile(cacheDataFile, "rw");
            cacheFileContent = new CachedContent();
            cacheFileContent.setCacheDataFilename(connectedToFile);
            cacheFileContent.setPersistTo(cacheFileMetadataFile);

        } catch (FileNotFoundException e) {
            throw new HttpDataSourceException("Error loading cache", e, dataSpec, HttpDataSourceException.TYPE_OPEN);
        } catch(InvalidClassException e) {
            // The cache file structure has changed and is no longer compatible. :-(
            // delete any old cache file and start over.
            try {
                CacheUtils.deleteCachedContent(context, connectedToFile);
            } catch (IOException e1) {
                throw new HttpDataSourceException("Error loading cache", e, dataSpec, HttpDataSourceException.TYPE_OPEN);
            }
        } catch (IOException e) {
            throw new HttpDataSourceException("Error loading cache", e, dataSpec, HttpDataSourceException.TYPE_OPEN);
        }

        return 0;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws HttpDataSourceException {
        try {
            skipInternal();
            return readInternal(buffer, offset, readLength);
        } catch (IOException e) {
            throw new HttpDataSourceException(e, dataSpec, HttpDataSourceException.TYPE_READ);
        }
    }

    @Override
    public void close() throws HttpDataSourceException {

        if(cachingEnabled && localCacheFile != null) {
            try {
                localCacheFile.close();
            } catch (IOException e) {
                throw new HttpDataSourceException("Closing local cache file", e, dataSpec, HttpDataSourceException.TYPE_CLOSE);
            }
            try {
                CacheUtils.saveCachedContent(cacheFileContent);
            } catch(IOException e) {
                throw new HttpDataSourceException("Writing local cache metadata file", e, dataSpec, HttpDataSourceException.TYPE_CLOSE);
            }
        }

        // Do I need to hard close this resource?
//        try {
            // must not close this as it will stop it working for all future videos.
//            if (client != null) {
//                client.cancelAllRequests(true);
//                client.close();
//                client = null;
//            }
//        } /*catch (IOException e) {
//            throw new HttpDataSourceException(e, dataSpec, HttpDataSourceException.TYPE_CLOSE);
//        }*/ finally {
            try {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        throw new HttpDataSourceException(e, dataSpec, HttpDataSourceException.TYPE_CLOSE);
                    }
                }
            } finally {
                inputStream = null;
                closeConnectionQuietly();
                if (opened) {
                    opened = false;
                    if (listener != null) {
                        listener.onTransferEnd(this);
                    }
                }
            }
//        }
    }

    /**
     * Returns the number of bytes that have been skipped since the most recent call to
     * {@link #open(DataSpec)}.
     *
     * @return The number of bytes skipped.
     */
    protected final long bytesSkipped() {
        return bytesSkipped;
    }

    /**
     * Returns the number of bytes that have been read since the most recent call to
     * {@link #open(DataSpec)}.
     *
     * @return The number of bytes read.
     */
    protected final long bytesRead() {
        return bytesRead;
    }

    /**
     * Returns the number of bytes that are still to be read for the current {@link DataSpec}.
     * <p>
     * If the total length of the data being read is known, then this length minus {@code bytesRead()}
     * is returned. If the total length is unknown, {@link C#LENGTH_UNSET} is returned.
     *
     * @return The remaining length, or {@link C#LENGTH_UNSET}.
     */
    protected final long bytesRemaining() {
        return bytesToRead == C.LENGTH_UNSET ? bytesToRead : bytesToRead - bytesRead;
    }

    /**
     * Establishes a connection, following redirects to do so where permitted.
     */
    private void makeConnection(DataSpec dataSpec) throws IOException {
        URL url = new URL(dataSpec.uri.toString());
        byte[] postBody = dataSpec.postBody;
        long position = dataSpec.position;
        long length = dataSpec.length;
        boolean allowGzip = dataSpec.isFlagSet(DataSpec.FLAG_ALLOW_GZIP);

        makeConnection(url, postBody, position, length, allowGzip);
    }

    /**
     * Configures a connection and opens it.
     *
     * @param url       The url to connect to.
     * @param postBody  The body data for a POST request.
     * @param position  The byte offset of the requested data.
     * @param length    The length of the requested data, or {@link C#LENGTH_UNSET}.
     * @param allowGzip Whether to allow the use of gzip.
     */
    private void makeConnection(URL url, byte[] postBody, long position,
                                long length, boolean allowGzip) throws IOException {

        client.setConnectTimeout(connectTimeoutMillis);
        client.setResponseTimeout(readTimeoutMillis);

        List<Header> headers = new ArrayList<>();

        if (!(position == 0 && length == C.LENGTH_UNSET)) {
            String rangeRequest = "bytes=" + position + "-";
            if (length != C.LENGTH_UNSET) {
                rangeRequest += (position + length - 1);
            } /*else {
                rangeRequest += (position + 102400 - 1); //100Kb if player isn't specific.
            }*/
            headers.add(new BasicHeader("Range", rangeRequest));
        }
        headers.add(new BasicHeader("User-Agent", userAgent));
        if (!allowGzip) {
            headers.add(new BasicHeader("Accept-Encoding", "identity"));
        }
        client.setEnableRedirects(true);
        CustomResponseHandler responseHandler = new CustomResponseHandler();

        client.get(context, dataSpec.uri.toString(), headers.toArray(new Header[headers.size()]), null, responseHandler);
    }

    /**
     * Attempts to extract the length of the content from the response headers of an open connection.
     *
     * @return The extracted length, or {@link C#LENGTH_UNSET}.
     */
    private long getContentLength() {
        long contentLength = C.LENGTH_UNSET;
        Header header = httpResponse.getFirstHeader("Content-Length");
        String contentLengthHeader = header.getValue();
        if (!TextUtils.isEmpty(contentLengthHeader)) {
            try {
                contentLength = Long.parseLong(contentLengthHeader);

                if(cachingEnabled) {
                    cacheFileContent.setTotalBytes(contentLength);
                }

            } catch (NumberFormatException e) {
                Log.e(TAG, "Unexpected Content-Length [" + contentLengthHeader + "]");
            }
        }
        header = httpResponse.getFirstHeader("Content-Range");
        String contentRangeHeader = header.getValue();
        if (!TextUtils.isEmpty(contentRangeHeader)) {
            Matcher matcher = CONTENT_RANGE_HEADER.matcher(contentRangeHeader);
            if (matcher.find()) {
                try {
                    long totalContentBytes = Long.parseLong(matcher.group(2));

                    if(cachingEnabled) {
                        cacheFileContent.setTotalBytes(totalContentBytes);
                    }

                    long contentLengthFromRange =
                            totalContentBytes - Long.parseLong(matcher.group(1)) + 1;
                    if (contentLength < 0) {
                        // Some proxy servers strip the Content-Length header. Fall back to the length
                        // calculated here in this case.
                        contentLength = contentLengthFromRange;
                    } else if (contentLength != contentLengthFromRange) {
                        // If there is a discrepancy between the Content-Length and Content-Range headers,
                        // assume the one with the larger value is correct. We have seen cases where carrier
                        // change one of them to reduce the size of a request, but it is unlikely anybody would
                        // increase it.
                        Log.w(TAG, "Inconsistent headers [" + contentLengthHeader + "] [" + contentRangeHeader
                                + "]");
                        contentLength = Math.max(contentLength, contentLengthFromRange);
                    }
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Unexpected Content-Range [" + contentRangeHeader + "]");
                }
            }
        }
        return contentLength;
    }

    /**
     * Skips any bytes that need skipping. Else does nothing.
     * <p>
     * This implementation is based roughly on {@code libcore.io.Streams.skipByReading()}.
     *
     * @throws InterruptedIOException If the thread is interrupted during the operation.
     * @throws EOFException           If the end of the input stream is reached before the bytes are skipped.
     */
    private void skipInternal() throws IOException {
        if (bytesSkipped == bytesToSkip) {
            return;
        }

        // Acquire the shared skip buffer.
        byte[] skipBuffer = skipBufferReference.getAndSet(null);
        if (skipBuffer == null) {
            skipBuffer = new byte[4096];
        }

        while (bytesSkipped != bytesToSkip) {
            int readLength = (int) Math.min(bytesToSkip - bytesSkipped, skipBuffer.length);
            int read = inputStream.read(skipBuffer, 0, readLength);
            if (Thread.interrupted()) {
                throw new InterruptedIOException();
            }
            if (read == -1) {
                throw new EOFException();
            }
            bytesSkipped += read;
            if (listener != null) {
                listener.onBytesTransferred(this, read);
            }
        }

        // Release the shared skip buffer.
        skipBufferReference.set(skipBuffer);
    }

    /**
     * Reads up to {@code length} bytes of data and stores them into {@code buffer}, starting at
     * index {@code offset}.
     * <p>
     * This method blocks until at least one byte of data can be read, the end of the opened range is
     * detected, or an exception is thrown.
     *
     * @param buffer     The buffer into which the read data should be stored.
     * @param offset     The start offset into {@code buffer} at which data should be written.
     * @param readLength The maximum number of bytes to read.
     * @return The number of bytes read, or {@link C#RESULT_END_OF_INPUT} if the end of the opened
     * range is reached.
     * @throws IOException If an error occurs reading from the source.
     */
    private int readInternal(byte[] buffer, int offset, int readLength) throws IOException {

        if(inputStream == null) {
            // check what can be read from the local cache and return if the answer is nothing
            long maxReadLen = Math.min(readLength, cachedRange.available(dataSpec.position + bytesRead));
            if(maxReadLen == 0) {
                return C.RESULT_END_OF_INPUT;
            }
            //read from local cache
            localCacheFile.seek(dataSpec.position + bytesRead);
            int read = localCacheFile.read(buffer, offset, (int)maxReadLen);
            if (read == -1) {
                // uh oh! the cache file didn't contain what it should - something is wrong.
                if (bytesToRead != C.LENGTH_UNSET) {
                    cacheFileContent.clear();
                }
                // returning 0 will prompt a new connection to be created (uncached this time).
                return 0;
            }
            bytesRead += read;
            return read;
        }

        if (readLength == 0) {
            return 0;
        }
        if (bytesToRead != C.LENGTH_UNSET) {
            long bytesRemaining = bytesToRead - bytesRead;
            if (bytesRemaining == 0) {
                return C.RESULT_END_OF_INPUT;
            }
            readLength = (int) Math.min(readLength, bytesRemaining);
        }

        int read = inputStream.read(buffer, offset, readLength);
        if (read == -1) {
            if (bytesToRead != C.LENGTH_UNSET) {
                // End of stream reached having not read sufficient data.
                throw new EOFException();
            }
            return C.RESULT_END_OF_INPUT;
        }

        if(cachingEnabled) {
            localCacheFile.seek(dataSpec.position + bytesRead);
            localCacheFile.write(buffer, offset, read);
            cacheFileContent.addRange(dataSpec.position, dataSpec.position + bytesRead + read - 1);
            if (cacheFileContent.isComplete() && cacheListener != null) {
                cacheListener.onFullyCached(cacheFileContent.getCachedDataFile());
                // discard what was just read and force use of local cache
                return C.RESULT_END_OF_INPUT;
            }
        }

        bytesRead += read;
        if (listener != null) {
            listener.onBytesTransferred(this, read);
        }

        return read;
    }

    /**
     * Closes the current connection quietly, if there is one.
     */
    private void closeConnectionQuietly() {
        if (httpResponse != null) {
            AsyncHttpClient.silentCloseInputStream(inputStream);
            AsyncHttpClient.endEntityViaReflection(httpResponse.getEntity());
            httpResponse = null;
        }
    }

    public Map<String, List<String>> getHeaderFields() {
        if (httpResponse == null) {
            return null;
        }
        Map<String, List<String>> headersMap = new HashMap<>(httpResponse.getAllHeaders().length);
        for (Header header : httpResponse.getAllHeaders()) {
            List<String> headerValues = headersMap.get(header.getName());
            if (headerValues == null) {
                headerValues = new ArrayList<>();
                headersMap.put(header.getName(), headerValues);
            }
            headerValues.add(header.getValue());
        }
        return headersMap;
    }

    private class CustomResponseHandler extends DataAsyncHttpResponseHandler {

        public CustomResponseHandler() {
            setUseSynchronousMode(true);
        }

        @Override
        public void sendResponseMessage(HttpResponse response) throws IOException {
            // do not process if request has been cancelled
            httpResponse = response;
        }

        @Override
        public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
            // do nothing
        }

        @Override
        public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
            // do nothing
        }

    }

    public interface CacheListener {
        void onFullyCached(File cacheContent);
    }
}

