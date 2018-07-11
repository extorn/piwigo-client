package delit.piwigoclient.piwigoApi.http;

import android.content.Context;
import android.os.Looper;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.HttpDelete;
import com.loopj.android.http.HttpGet;
import com.loopj.android.http.LogHandler;
import com.loopj.android.http.LogInterface;
import com.loopj.android.http.PersistentCookieStore;
import com.loopj.android.http.ResponseHandlerInterface;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpHost;
import cz.msebera.android.httpclient.HttpRequest;
import cz.msebera.android.httpclient.HttpRequestInterceptor;
import cz.msebera.android.httpclient.HttpVersion;
import cz.msebera.android.httpclient.auth.AuthScope;
import cz.msebera.android.httpclient.auth.Credentials;
import cz.msebera.android.httpclient.auth.UsernamePasswordCredentials;
import cz.msebera.android.httpclient.client.CookieStore;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.config.RequestConfig;
import cz.msebera.android.httpclient.client.methods.HttpEntityEnclosingRequestBase;
import cz.msebera.android.httpclient.client.methods.HttpHead;
import cz.msebera.android.httpclient.client.methods.HttpPatch;
import cz.msebera.android.httpclient.client.methods.HttpPost;
import cz.msebera.android.httpclient.client.methods.HttpPut;
import cz.msebera.android.httpclient.client.methods.HttpUriRequest;
import cz.msebera.android.httpclient.client.protocol.HttpClientContext;
import cz.msebera.android.httpclient.config.ConnectionConfig;
import cz.msebera.android.httpclient.config.Registry;
import cz.msebera.android.httpclient.config.RegistryBuilder;
import cz.msebera.android.httpclient.config.SocketConfig;
import cz.msebera.android.httpclient.conn.HttpClientConnectionManager;
import cz.msebera.android.httpclient.conn.socket.ConnectionSocketFactory;
import cz.msebera.android.httpclient.conn.socket.PlainConnectionSocketFactory;
import cz.msebera.android.httpclient.conn.ssl.SSLConnectionSocketFactory;
import cz.msebera.android.httpclient.entity.HttpEntityWrapper;
import cz.msebera.android.httpclient.impl.auth.BasicScheme;
import cz.msebera.android.httpclient.impl.client.BasicAuthCache;
import cz.msebera.android.httpclient.impl.client.BasicCredentialsProvider;
import cz.msebera.android.httpclient.impl.client.CloseableHttpClient;
import cz.msebera.android.httpclient.impl.client.cache.CacheConfig;
import cz.msebera.android.httpclient.impl.client.cache.CachingHttpClients;
import cz.msebera.android.httpclient.impl.conn.PoolingHttpClientConnectionManager;
import cz.msebera.android.httpclient.protocol.HttpContext;


/**
 * The CachingAsyncHttpClient can be used to make asynchronous GET, POST, PUT and DELETE HTTP requests in
 * your Android applications. Requests can be made with additional parameters by passing a {@link
 * RequestParams} instance, and responses can be handled by passing an anonymously overridden {@link
 * ResponseHandlerInterface} instance. <p>&nbsp;</p> For example: <p>&nbsp;</p>
 * <pre>
 * CachingAsyncHttpClient client = new CachingAsyncHttpClient();
 * client.get("https://www.google.com", new AsyncHttpResponseHandler() {
 *     &#064;Override
 *     public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
 *          System.out.println(response);
 *     }
 *     &#064;Override
 *     public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable
 * error)
 * {
 *          error.printStackTrace(System.out);
 *     }
 * });
 * </pre>
 *
 * @see com.loopj.android.http.AsyncHttpResponseHandler
 * @see com.loopj.android.http.ResponseHandlerInterface
 * @see com.loopj.android.http.RequestParams
 */
public class CachingAsyncHttpClient implements Closeable {

    public static final String LOG_TAG = "AsyncHttpClient";

    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_CONTENT_RANGE = "Content-Range";
    public static final String HEADER_CONTENT_ENCODING = "Content-Encoding";
    public static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";
    public static final String HEADER_ACCEPT_ENCODING = "Accept-Encoding";
    public static final String ENCODING_GZIP = "gzip";

    public static final int DEFAULT_MAX_CONNECTIONS = 10;
    public static final int DEFAULT_SOCKET_TIMEOUT = 10 * 1000;
    public static final int DEFAULT_MAX_RETRIES = 5;
    public static final int DEFAULT_RETRY_SLEEP_TIME_MILLIS = 1500;
    public static final int DEFAULT_SOCKET_BUFFER_SIZE = 8192;
    public static LogInterface log = new LogHandler();
    private final Map<Context, List<RequestHandle>> requestMap;
    private final Map<String, String> clientHeaderMap;
    private final SSLConnectionSocketFactory sslConnectionSocketFactory;
    private final HashMap<AuthScope, Credentials> credentialsMap;
    private HttpClient httpClient;
    private int retrySleep = DEFAULT_RETRY_SLEEP_TIME_MILLIS;
    private int maxRetries = DEFAULT_MAX_RETRIES;
    private int maxConnections = DEFAULT_MAX_CONNECTIONS;
    private int connectTimeout = DEFAULT_SOCKET_TIMEOUT;
    private int responseTimeout = DEFAULT_SOCKET_TIMEOUT;
    private ExecutorService threadPool;
    private boolean isUrlEncodingEnabled = true;
    private boolean enableRelativeRedirects = true;
    private boolean enableCircularRedirects = true;
    private boolean enableRedirects = true;
    private CookieStore cookieStore;
    private BasicCredentialsProvider credentialsProvider;
    private boolean usePreemptiveAuth;
    private long maxCachedObjectSizeBytes = 8192;
    private int maxCacheEntries = 1000;
    private File cacheFolder;
    private String userAgent;
    private RetryHandler retryHandler;
    private int maxConcurrentConnections = DEFAULT_MAX_CONNECTIONS;
    private int maxRedirects;

    /**
     * Creates a new CachingAsyncHttpClient.
     */
    public CachingAsyncHttpClient(SSLConnectionSocketFactory sslConnectionSocketFactory) {

        threadPool = getDefaultThreadPool();
        requestMap = Collections.synchronizedMap(new WeakHashMap<Context, List<RequestHandle>>());
        clientHeaderMap = new HashMap<>();
        credentialsMap = new HashMap<>();
        this.sslConnectionSocketFactory = sslConnectionSocketFactory;
    }

    public static void allowRetryExceptionClass(Class<?> cls) {
        if (cls != null) {
            RetryHandler.addClassToWhitelist(cls);
        }
    }

    public static void blockRetryExceptionClass(Class<?> cls) {
        if (cls != null) {
            RetryHandler.addClassToBlacklist(cls);
        }
    }

    /**
     * Will encode url, if not disabled, and adds params on the end of it
     *
     * @param url             String with URL, should be valid URL without params
     * @param params          RequestParams to be appended on the end of URL
     * @param shouldEncodeUrl whether url should be encoded (replaces spaces with %20)
     * @return encoded url if requested with params appended if any available
     */
    public static String getUrlWithQueryString(boolean shouldEncodeUrl, String url, RequestParams params) {
        if (url == null)
            return null;

        if (shouldEncodeUrl) {
            try {
                String decodedURL = URLDecoder.decode(url, "UTF-8");
                URL _url = new URL(decodedURL);
                URI _uri = new URI(_url.getProtocol(), _url.getUserInfo(), _url.getHost(), _url.getPort(), _url.getPath(), _url.getQuery(), _url.getRef());
                url = _uri.toASCIIString();
            } catch (Exception ex) {
                // Should not really happen, added just for sake of validity
                log.e(LOG_TAG, "getUrlWithQueryString encoding URL", ex);
            }
        }

        if (params != null) {
            // Construct the query string and trim it, in case it
            // includes any excessive white spaces.
            String paramString = params.getParamString().trim();

            // Only add the query string if it isn't empty and it
            // isn't equal to '?'.
            if (!paramString.equals("") && !paramString.equals("?")) {
                url += url.contains("?") ? "&" : "?";
                url += paramString;
            }
        }

        return url;
    }

    /**
     * Checks the InputStream if it contains  GZIP compressed data
     *
     * @param inputStream InputStream to be checked
     * @return true or false if the stream contains GZIP compressed data
     * @throws java.io.IOException if read from inputStream fails
     */
    public static boolean isInputStreamGZIPCompressed(final PushbackInputStream inputStream) throws IOException {
        if (inputStream == null)
            return false;

        byte[] signature = new byte[2];
        int count = 0;
        try {
            while (count < 2) {
                int readCount = inputStream.read(signature, count, 2 - count);
                if (readCount < 0) return false;
                count = count + readCount;
            }
        } finally {
            inputStream.unread(signature, 0, count);
        }
        int streamHeader = ((int) signature[0] & 0xff) | ((signature[1] << 8) & 0xff00);
        return GZIPInputStream.GZIP_MAGIC == streamHeader;
    }

    /**
     * A utility function to close an input stream without raising an exception.
     *
     * @param is input stream to close safely
     */
    public static void silentCloseInputStream(InputStream is) {
        try {
            if (is != null) {
                is.close();
            }
        } catch (IOException e) {
            log.w(LOG_TAG, "Cannot close input stream", e);
        }
    }

    /**
     * A utility function to close an output stream without raising an exception.
     *
     * @param os output stream to close safely
     */
    public static void silentCloseOutputStream(OutputStream os) {
        try {
            if (os != null) {
                os.close();
            }
        } catch (IOException e) {
            log.w(LOG_TAG, "Cannot close output stream", e);
        }
    }

    /**
     * This horrible hack is required on Android, due to implementation of BasicManagedEntity, which
     * doesn't chain call consumeContent on underlying wrapped HttpEntity
     *
     * @param entity HttpEntity, may be null
     */
    public static void endEntityViaReflection(HttpEntity entity) {
        if (entity instanceof HttpEntityWrapper) {
            try {
                Field f = null;
                Field[] fields = HttpEntityWrapper.class.getDeclaredFields();
                for (Field ff : fields) {
                    if (ff.getName().equals("wrappedEntity")) {
                        f = ff;
                        break;
                    }
                }
                if (f != null) {
                    f.setAccessible(true);
                    HttpEntity wrapped = (HttpEntity) f.get(entity);
                    if (wrapped != null) {
                        wrapped.getContent().close();
                    }
                }
            } catch (Throwable t) {
                log.e(LOG_TAG, "wrappedEntity consume", t);
            }
        }
    }

    protected HttpClientConnectionManager createConnectionManager() {

        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", sslConnectionSocketFactory)
                .build();

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
        cm.setMaxTotal(maxConcurrentConnections);
        cm.setDefaultMaxPerRoute(maxConcurrentConnections);
        SocketConfig.Builder socketBuilder = cm.getDefaultSocketConfig() != null ? SocketConfig.copy(cm.getDefaultSocketConfig()) : SocketConfig.custom();
        SocketConfig socketConfig = socketBuilder.setTcpNoDelay(true).setSoTimeout(responseTimeout).build();
        cm.setDefaultSocketConfig(socketConfig);
        ConnectionConfig.Builder connectionBuilder = cm.getDefaultConnectionConfig() != null ? ConnectionConfig.copy(cm.getDefaultConnectionConfig()) : ConnectionConfig.custom();
        ConnectionConfig connConfig = connectionBuilder.setBufferSize(DEFAULT_SOCKET_BUFFER_SIZE).build();
        cm.setDefaultConnectionConfig(connConfig);
        return cm;
    }

    /**
     * Catch all in case you forget to close it!
     *
     * @throws Throwable
     */
    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }

    @Override
    public void close() throws IOException {
        if (httpClient != null && httpClient instanceof Closeable) {
            ((Closeable) httpClient).close();
        }
    }

    public void setCacheSettings(File cacheFolder, int maxCacheEntries, int maxCachedObjectSizeBytes) {
        this.cacheFolder = cacheFolder;
        this.maxCacheEntries = maxCacheEntries;
        this.maxCachedObjectSizeBytes = maxCachedObjectSizeBytes;
    }

    private synchronized HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = buildHttpClient();
        }
        return httpClient;
    }

    public int getMaxConcurrentConnections() {
        return maxConcurrentConnections;
    }

    public void setMaxConcurrentConnections(int maxConcurrentConnections) {
        this.maxConcurrentConnections = maxConcurrentConnections;
    }

    protected HttpClient buildHttpClient() {
        CacheConfig cacheConfig = CacheConfig.custom()
                .setMaxCacheEntries(maxCacheEntries)
                .setSharedCache(false)
                .setMaxObjectSize(maxCachedObjectSizeBytes)
                .build();
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(connectTimeout)
                .setSocketTimeout(responseTimeout)
                .setConnectionRequestTimeout(connectTimeout)
                .setRedirectsEnabled(enableRedirects)
                .setMaxRedirects(maxRedirects)
                .setRelativeRedirectsAllowed(enableRelativeRedirects)
                .setCircularRedirectsAllowed(enableCircularRedirects)
                .build();

        this.retryHandler = new RetryHandler(maxRetries, retrySleep);
// These are the defaults
//        Registry<AuthSchemeProvider> authRegistry = RegistryBuilder.<AuthSchemeProvider>create()
//                .register(AuthSchemes.BASIC, new BasicSchemeFactory())
//                .register(AuthSchemes.DIGEST, new DigestSchemeFactory())
//                .register(AuthSchemes.NTLM, new NTLMSchemeFactory()).build();

        final HttpClientConnectionManager cm = createConnectionManager();
        Utils.asserts(cm != null, "Custom implementation of HttpClientConnectionManager returned null");
        CloseableHttpClient cachingClient = CachingHttpClients.custom()
                .setCacheConfig(cacheConfig)
                .setCacheDir(cacheFolder)
//TODO custom cache control - offline access etc                .setHttpCacheInvalidator()
//                .setDefaultAuthSchemeRegistry(authRegistry)
                .setConnectionManager(cm)
                .setSSLSocketFactory(sslConnectionSocketFactory)
                .setDefaultRequestConfig(requestConfig)
                .setUserAgent(userAgent)
                .setDefaultCredentialsProvider(credentialsProvider)
                .setRedirectStrategy(new MyRedirectStrategy(enableRedirects))
                .setRetryHandler(retryHandler)
                .addInterceptorFirst(new HttpRequestInterceptor() {
                    @Override
                    public void process(HttpRequest request, HttpContext context) {
                        if (!request.containsHeader(HEADER_ACCEPT_ENCODING)) {
                            request.addHeader(HEADER_ACCEPT_ENCODING, ENCODING_GZIP);
                        }
                        for (String header : clientHeaderMap.keySet()) {
                            if (request.containsHeader(header)) {
                                Header overwritten = request.getFirstHeader(header);
                                log.d(LOG_TAG,
                                        String.format("Headers were overwritten! (%s | %s) overwrites (%s | %s)",
                                                header, clientHeaderMap.get(header),
                                                overwritten.getName(), overwritten.getValue())
                                );

                                //remove the overwritten header
                                request.removeHeader(overwritten);
                            }
                            request.addHeader(header, clientHeaderMap.get(header));
                        }
                    }
                })
                // This seems to double unzip the response.
                /*.addInterceptorFirst(new HttpResponseInterceptor() {
                    @Override
                    public void process(HttpResponse response, HttpContext context) {
                        final HttpEntity entity = response.getEntity();
                        if (entity == null) {
                            return;
                        }
                        final Header encoding = entity.getContentEncoding();
                        if (encoding != null) {
                            for (HeaderElement element : encoding.getElements()) {
                                if (element.getName().equalsIgnoreCase(ENCODING_GZIP)) {
                                    response.setEntity(new InflatingEntity(entity));
                                    break;
                                }
                            }
                        }
                    }
                })*/.build();
        return cachingClient;
    }

    protected HttpClientContext buildHttpClientContext() {
        HttpClientContext httpContext = HttpClientContext.create();
        httpContext.setCookieStore(cookieStore);
        RequestConfig rc = httpContext.getRequestConfig();
        RequestConfig.Builder rcBuilder = RequestConfig.copy(rc);
        rc = rcBuilder.setConnectTimeout(connectTimeout)
                .setSocketTimeout(responseTimeout)
                .setConnectionRequestTimeout(connectTimeout)
                .setRedirectsEnabled(enableRedirects)
                .setRelativeRedirectsAllowed(enableRelativeRedirects)
                .setCircularRedirectsAllowed(enableCircularRedirects).build();
        httpContext.setRequestConfig(rc);
        httpContext.setCredentialsProvider(credentialsProvider);
        httpContext.setAuthCache(new BasicAuthCache());
        if (usePreemptiveAuth) {
            for (Map.Entry<AuthScope, Credentials> credentialsEntry : credentialsMap.entrySet()) {
                AuthScope scope = credentialsEntry.getKey();
                HttpHost host = new HttpHost(scope.getHost(), scope.getPort(), scope.getScheme());
                httpContext.getAuthCache().put(host, new BasicScheme());
            }
        }
        return httpContext;
    }

    /**
     * Returns logging enabled flag from underlying LogInterface instance
     * Default setting is logging enabled.
     *
     * @return boolean whether is logging across the library currently enabled
     */
    public boolean isLoggingEnabled() {
        return log.isLoggingEnabled();
    }

    /**
     * Will set logging enabled flag on underlying LogInterface instance.
     * Default setting is logging enabled.
     *
     * @param loggingEnabled whether the logging should be enabled or not
     */
    public void setLoggingEnabled(boolean loggingEnabled) {
        log.setLoggingEnabled(loggingEnabled);
    }

    /**
     * Retrieves current log level from underlying LogInterface instance.
     * Default setting is VERBOSE log level.
     *
     * @return int log level currently in effect
     */
    public int getLoggingLevel() {
        return log.getLoggingLevel();
    }

    /**
     * Sets log level to be used across all library default implementation
     * Default setting is VERBOSE log level.
     *
     * @param logLevel int log level, either from LogInterface interface or from {@link android.util.Log}
     */
    public void setLoggingLevel(int logLevel) {
        log.setLoggingLevel(logLevel);
    }

    /**
     * Will return current LogInterface used in CachingAsyncHttpClient instance
     *
     * @return LogInterface currently used by CachingAsyncHttpClient instance
     */
    public LogInterface getLogInterface() {
        return log;
    }

    /**
     * Sets default LogInterface (similar to std Android Log util class) instance,
     * to be used in CachingAsyncHttpClient instance
     *
     * @param logInterfaceInstance LogInterface instance, if null, nothing is done
     */
    public void setLogInterface(LogInterface logInterfaceInstance) {
        if (logInterfaceInstance != null) {
            log = logInterfaceInstance;
        }
    }

    /**
     * Sets an optional CookieStore to use when making requests
     *
     * @param cookieStore The CookieStore implementation to use, usually an instance of {@link
     *                    PersistentCookieStore}
     */
    public void setCookieStore(CookieStore cookieStore) {
        this.cookieStore = cookieStore;
    }

    /**
     * Returns the current executor service used. By default, Executors.newCachedThreadPool() is
     * used.
     *
     * @return current executor service used
     */
    public ExecutorService getThreadPool() {
        return threadPool;
    }

    /**
     * Overrides the threadpool implementation used when queuing/pooling requests. By default,
     * Executors.newCachedThreadPool() is used.
     *
     * @param threadPool an instance of {@link ExecutorService} to use for queuing/pooling
     *                   requests.
     */
    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }

    /**
     * Get the default threading pool to be used for this HTTP client.
     *
     * @return The default threading pool to be used
     */
    protected ExecutorService getDefaultThreadPool() {
        return Executors.newCachedThreadPool();
    }

    /**
     * Simple interface method, to enable or disable redirects. If you set manually RedirectHandler
     * on underlying HttpClient, effects of this method will be canceled. <p>&nbsp;</p> Default
     * setting is to disallow redirects.
     *
     * @param enableRedirects         boolean
     * @param enableRelativeRedirects boolean
     * @param enableCircularRedirects boolean
     * @param maxRedirects            int
     */
    public void setEnableRedirects(final boolean enableRedirects, final boolean enableRelativeRedirects, final boolean enableCircularRedirects, final int maxRedirects) {
        this.enableRedirects = enableRedirects;
        this.maxRedirects = maxRedirects;
        this.enableCircularRedirects = enableCircularRedirects;
        this.enableRelativeRedirects = enableRelativeRedirects;
    }

    /**
     * Circular redirects are enabled by default
     *
     * @param enableRedirects         boolean
     * @param enableRelativeRedirects boolean
     * @see #setEnableRedirects(boolean, boolean, boolean, int)
     */
    public void setEnableRedirects(final boolean enableRedirects, final boolean enableRelativeRedirects, final int maxRedirects) {
        setEnableRedirects(enableRedirects, enableRelativeRedirects, true, maxRedirects);
    }

    /**
     * @param enableRedirects boolean
     * @see #setEnableRedirects(boolean, boolean, boolean, int)
     */
    public void setEnableRedirects(final boolean enableRedirects, final int maxRedirects) {
        setEnableRedirects(enableRedirects, enableRedirects, enableRedirects, maxRedirects);
    }

    /**
     * Sets the User-Agent header to be sent with each request. By default, "Android Asynchronous
     * Http Client/VERSION (https://loopj.com/android-async-http/)" is used.
     *
     * @param userAgent the string to use in the User-Agent header.
     */
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    /**
     * Returns current limit of parallel connections
     *
     * @return maximum limit of parallel connections, default is 10
     */
    public int getMaxConnections() {
        return maxConnections;
    }

    /**
     * Sets maximum limit of parallel connections
     *
     * @param maxConnections maximum parallel connections, must be at least 1
     */
    public void setMaxConnections(int maxConnections) {
        if (maxConnections < 1)
            maxConnections = DEFAULT_MAX_CONNECTIONS;
        this.maxConnections = maxConnections;
    }

    /**
     * Set both the connection and socket timeouts. By default, both are set to
     * 10 seconds.
     *
     * @param value the connect/socket timeout in milliseconds, at least 1 second
     * @see #setConnectTimeout(int)
     * @see #setResponseTimeout(int)
     */
    public void setTimeout(int value) {
        value = value < 1000 ? DEFAULT_SOCKET_TIMEOUT : value;
        setConnectTimeout(value);
        setResponseTimeout(value);
    }

    /**
     * Returns current connection timeout limit (milliseconds). By default, this
     * is set to 10 seconds.
     *
     * @return Connection timeout limit in milliseconds
     */
    public int getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Set connection timeout limit (milliseconds). By default, this is set to
     * 10 seconds.
     *
     * @param value Connection timeout in milliseconds, minimal value is 1000 (1 second).
     */
    public void setConnectTimeout(int value) {
        connectTimeout = value < 1000 ? DEFAULT_SOCKET_TIMEOUT : value;
    }

    /**
     * Returns current response timeout limit (milliseconds). By default, this
     * is set to 10 seconds.
     *
     * @return Response timeout limit in milliseconds
     */
    public int getResponseTimeout() {
        return responseTimeout;
    }

    /**
     * Set response timeout limit (milliseconds). By default, this is set to
     * 10 seconds.
     *
     * @param value Response timeout in milliseconds, minimal value is 1000 (1 second).
     */
    public void setResponseTimeout(int value) {
        responseTimeout = value < 1000 ? DEFAULT_SOCKET_TIMEOUT : value;
    }

    /**
     * Sets the maximum number of retries and timeout for a particular Request.
     *
     * @param maxRetries maximum number of retries per request
     * @param retrySleep sleep between retries in milliseconds
     */
    public void setMaxRetriesAndTimeout(int maxRetries, int retrySleep) {
        this.maxRetries = maxRetries;
        this.retrySleep = retrySleep;
    }

    /**
     * Will, before sending, remove all headers currently present in CachingAsyncHttpClient instance, which
     * applies on all requests this client makes
     */
    public void removeAllHeaders() {
        clientHeaderMap.clear();
    }

    /**
     * Sets headers that will be added to all requests this client makes (before sending).
     *
     * @param header the name of the header
     * @param value  the contents of the header
     */
    public void addHeader(String header, String value) {
        clientHeaderMap.put(header, value);
    }

    /**
     * Remove header from all requests this client makes (before sending).
     *
     * @param header the name of the header
     */
    public void removeHeader(String header) {
        clientHeaderMap.remove(header);
    }

    /**
     * Sets basic authentication for the request. Uses AuthScope.ANY. This is the same as
     * setBasicAuth('username','password',AuthScope.ANY)
     *
     * @param username Basic Auth username
     * @param password Basic Auth password
     */
    public void setBasicAuth(String username, String password) {
        setBasicAuth(username, password, false);
    }

    /**
     * Sets basic authentication for the request. Uses AuthScope.ANY. This is the same as
     * setBasicAuth('username','password',AuthScope.ANY)
     *
     * @param username   Basic Auth username
     * @param password   Basic Auth password
     * @param preemptive sets authorization in preemptive manner
     */
    public void setBasicAuth(String username, String password, boolean preemptive) {
        setBasicAuth(username, password, null, preemptive);
    }

    /**
     * Sets basic authentication for the request. You should pass in your AuthScope for security. It
     * should be like this setBasicAuth("username","password", new AuthScope("host",port,AuthScope.ANY_REALM))
     *
     * @param username Basic Auth username
     * @param password Basic Auth password
     * @param scope    - an AuthScope object
     */
    public void setBasicAuth(String username, String password, AuthScope scope) {
        setBasicAuth(username, password, scope, false);
    }

    /**
     * Sets basic authentication for the request. You should pass in your AuthScope for security. It
     * should be like this setBasicAuth("username","password", new AuthScope("host",port,AuthScope.ANY_REALM))
     *
     * @param username   Basic Auth username
     * @param password   Basic Auth password
     * @param scope      an AuthScope object
     * @param preemptive sets authorization in preemptive manner
     */
    public void setBasicAuth(String username, String password, AuthScope scope, boolean preemptive) {
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(username, password);
        setCredentials(scope, credentials);
        setAuthenticationPreemptive(preemptive);
    }

    public void setCredentials(AuthScope authScope, Credentials credentials) {
        if (credentials == null) {
            log.d(LOG_TAG, "Provided credentials are null, not setting");
            return;
        }
        if (credentialsProvider == null) {
            credentialsProvider = new BasicCredentialsProvider();
        }
        credentialsMap.put(authScope, credentials);
        credentialsProvider.setCredentials(authScope == null ? AuthScope.ANY : authScope, credentials);
    }

    /**
     * Sets HttpRequestInterceptor which handles authorization in preemptive way, as workaround you
     * can use call `AsyncHttpClient.addHeader("Authorization","Basic base64OfUsernameAndPassword==")`
     *
     * @param isPreemptive whether the authorization is processed in preemptive way
     */
    public void setAuthenticationPreemptive(boolean isPreemptive) {
        usePreemptiveAuth = isPreemptive;
    }

    // [+] HTTP HEAD

    /**
     * Removes previously set auth credentials
     */
    public void clearCredentialsProvider() {
        credentialsMap.clear();
        this.credentialsProvider.clear();
    }

    /**
     * Cancels any pending (or potentially active) requests associated with the passed Context.
     * <p>&nbsp;</p> <b>Note:</b> This will only affect requests which were created with a non-null
     * android Context. This method is intended to be used in the onDestroy method of your android
     * activities to destroy all requests which are no longer required.
     *
     * @param context               the android Context instance associated to the request.
     * @param mayInterruptIfRunning specifies if active requests should be cancelled along with
     *                              pending requests.
     */
    public void cancelRequests(final Context context, final boolean mayInterruptIfRunning) {
        if (context == null) {
            log.e(LOG_TAG, "Passed null Context to cancelRequests");
            return;
        }

        final List<RequestHandle> requestList = requestMap.get(context);
        requestMap.remove(context);

        if (Looper.myLooper() == Looper.getMainLooper()) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    cancelRequests(requestList, mayInterruptIfRunning);
                }
            };
            threadPool.submit(runnable);
        } else {
            cancelRequests(requestList, mayInterruptIfRunning);
        }
    }

    private void cancelRequests(final List<RequestHandle> requestList, final boolean mayInterruptIfRunning) {
        if (requestList != null) {
            for (RequestHandle requestHandle : requestList) {
                requestHandle.cancel(mayInterruptIfRunning);
            }
        }
    }

    /**
     * Cancels all pending (or potentially active) requests. <p>&nbsp;</p> <b>Note:</b> This will
     * only affect requests which were created with a non-null android Context. This method is
     * intended to be used in the onDestroy method of your android activities to destroy all
     * requests which are no longer required.
     *
     * @param mayInterruptIfRunning specifies if active requests should be cancelled along with
     *                              pending requests.
     */
    public void cancelAllRequests(boolean mayInterruptIfRunning) {
        for (List<RequestHandle> requestList : requestMap.values()) {
            if (requestList != null) {
                for (RequestHandle requestHandle : requestList) {
                    requestHandle.cancel(mayInterruptIfRunning);
                }
            }
        }
        requestMap.clear();
    }

    /**
     * Allows you to cancel all requests currently in queue or running, by set TAG,
     * if passed TAG is null, will not attempt to cancel any requests, if TAG is null
     * on RequestHandle, it cannot be canceled by this call
     *
     * @param TAG                   TAG to be matched in RequestHandle
     * @param mayInterruptIfRunning specifies if active requests should be cancelled along with
     *                              pending requests.
     */
    public void cancelRequestsByTAG(Object TAG, boolean mayInterruptIfRunning) {
        if (TAG == null) {
            log.d(LOG_TAG, "cancelRequestsByTAG, passed TAG is null, cannot proceed");
            return;
        }
        for (List<RequestHandle> requestList : requestMap.values()) {
            if (requestList != null) {
                for (RequestHandle requestHandle : requestList) {
                    if (TAG.equals(requestHandle.getTag()))
                        requestHandle.cancel(mayInterruptIfRunning);
                }
            }
        }
    }

    // [-] HTTP HEAD
    // [+] HTTP GET

    /**
     * Perform a HTTP HEAD request, without any parameters.
     *
     * @param url             the URL to send the request to.
     * @param responseHandler the response handler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle head(String url, ResponseHandlerInterface responseHandler) {
        return head(null, url, null, responseHandler);
    }

    /**
     * Perform a HTTP HEAD request with parameters.
     *
     * @param url             the URL to send the request to.
     * @param params          additional HEAD parameters to send with the request.
     * @param responseHandler the response handler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle head(String url, RequestParams params, ResponseHandlerInterface responseHandler) {
        return head(null, url, params, responseHandler);
    }

    /**
     * Perform a HTTP HEAD request without any parameters and track the Android Context which
     * initiated the request.
     *
     * @param context         the Android Context which initiated the request.
     * @param url             the URL to send the request to.
     * @param responseHandler the response handler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle head(Context context, String url, ResponseHandlerInterface responseHandler) {
        return head(context, url, null, responseHandler);
    }

    /**
     * Perform a HTTP HEAD request and track the Android Context which initiated the request.
     *
     * @param context         the Android Context which initiated the request.
     * @param url             the URL to send the request to.
     * @param params          additional HEAD parameters to send with the request.
     * @param responseHandler the response handler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle head(Context context, String url, RequestParams params, ResponseHandlerInterface responseHandler) {
        return sendRequest(getHttpClient(), buildHttpClientContext(), new HttpHead(getUrlWithQueryString(isUrlEncodingEnabled, url, params)), null, responseHandler, context);
    }

    /**
     * Perform a HTTP HEAD request and track the Android Context which initiated the request with
     * customized headers
     *
     * @param context         Context to execute request against
     * @param url             the URL to send the request to.
     * @param headers         set headers only for this request
     * @param params          additional HEAD parameters to send with the request.
     * @param responseHandler the response handler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle head(Context context, String url, Header[] headers, RequestParams params, ResponseHandlerInterface responseHandler) {
        HttpUriRequest request = new HttpHead(getUrlWithQueryString(isUrlEncodingEnabled, url, params));
        if (headers != null) request.setHeaders(headers);

        return sendRequest(getHttpClient(), buildHttpClientContext(), request, null, responseHandler,
                context);
    }

    /**
     * Perform a HTTP GET request, without any parameters.
     *
     * @param url             the URL to send the request to.
     * @param responseHandler the response handler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle get(String url, ResponseHandlerInterface responseHandler) {
        return get(null, url, null, responseHandler);
    }

    // [-] HTTP GET
    // [+] HTTP POST

    /**
     * Perform a HTTP GET request with parameters.
     *
     * @param url             the URL to send the request to.
     * @param params          additional GET parameters to send with the request.
     * @param responseHandler the response handler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle get(String url, RequestParams params, ResponseHandlerInterface responseHandler) {
        return get(null, url, params, responseHandler);
    }

    /**
     * Perform a HTTP GET request without any parameters and track the Android Context which
     * initiated the request.
     *
     * @param context         the Android Context which initiated the request.
     * @param url             the URL to send the request to.
     * @param responseHandler the response handler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle get(Context context, String url, ResponseHandlerInterface responseHandler) {
        return get(context, url, null, responseHandler);
    }

    /**
     * Perform a HTTP GET request and track the Android Context which initiated the request.
     *
     * @param context         the Android Context which initiated the request.
     * @param url             the URL to send the request to.
     * @param params          additional GET parameters to send with the request.
     * @param responseHandler the response handler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle get(Context context, String url, RequestParams params, ResponseHandlerInterface responseHandler) {
        return sendRequest(getHttpClient(), buildHttpClientContext(), new HttpGet(getUrlWithQueryString(isUrlEncodingEnabled, url, params)), null, responseHandler, context);
    }

    /**
     * Perform a HTTP GET request and track the Android Context which initiated the request with
     * customized headers
     *
     * @param context         Context to execute request against
     * @param url             the URL to send the request to.
     * @param headers         set headers only for this request
     * @param params          additional GET parameters to send with the request.
     * @param responseHandler the response handler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle get(Context context, String url, Header[] headers, RequestParams params, ResponseHandlerInterface responseHandler) {
        HttpUriRequest request = new HttpGet(getUrlWithQueryString(isUrlEncodingEnabled, url, params));
        if (headers != null) request.setHeaders(headers);
        return sendRequest(getHttpClient(), buildHttpClientContext(), request, null, responseHandler,
                context);
    }

    /**
     * Perform a HTTP GET request and track the Android Context which initiated the request.
     *
     * @param context         the Android Context which initiated the request.
     * @param url             the URL to send the request to.
     * @param entity          a raw {@link cz.msebera.android.httpclient.HttpEntity} to send with the request, for
     *                        example, use this to send string/json/xml payloads to a server by
     *                        passing a {@link cz.msebera.android.httpclient.entity.StringEntity}.
     * @param contentType     the content type of the payload you are sending, for example
     *                        application/json if sending a json payload.
     * @param responseHandler the response ha   ndler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle get(Context context, String url, HttpEntity entity, String contentType, ResponseHandlerInterface responseHandler) {
        return sendRequest(getHttpClient(), buildHttpClientContext(), addEntityToRequestBase(new HttpGet(URI.create(url).normalize()), entity), contentType, responseHandler, context);
    }

    /**
     * Perform a HTTP POST request, without any parameters.
     *
     * @param url             the URL to send the request to.
     * @param responseHandler the response handler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle post(String url, ResponseHandlerInterface responseHandler) {
        return post(null, url, null, responseHandler);
    }

    // [-] HTTP POST
    // [+] HTTP PUT

    /**
     * Perform a HTTP POST request with parameters.
     *
     * @param url             the URL to send the request to.
     * @param params          additional POST parameters or files to send with the request.
     * @param responseHandler the response handler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle post(String url, RequestParams params, ResponseHandlerInterface responseHandler) {
        return post(null, url, params, responseHandler);
    }

    /**
     * Perform a HTTP POST request and track the Android Context which initiated the request.
     *
     * @param context         the Android Context which initiated the request.
     * @param url             the URL to send the request to.
     * @param params          additional POST parameters or files to send with the request.
     * @param responseHandler the response handler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle post(Context context, String url, RequestParams params, ResponseHandlerInterface responseHandler) {
        return post(context, url, paramsToEntity(params, responseHandler), null, responseHandler);
    }

    /**
     * Perform a HTTP POST request and track the Android Context which initiated the request.
     *
     * @param context         the Android Context which initiated the request.
     * @param url             the URL to send the request to.
     * @param entity          a raw {@link cz.msebera.android.httpclient.HttpEntity} to send with the request, for
     *                        example, use this to send string/json/xml payloads to a server by
     *                        passing a {@link cz.msebera.android.httpclient.entity.StringEntity}.
     * @param contentType     the content type of the payload you are sending, for example
     *                        application/json if sending a json payload.
     * @param responseHandler the response ha   ndler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle post(Context context, String url, HttpEntity entity, String contentType, ResponseHandlerInterface responseHandler) {
        return sendRequest(getHttpClient(), buildHttpClientContext(), addEntityToRequestBase(new HttpPost(getURI(url)), entity), contentType, responseHandler, context);
    }

    /**
     * Perform a HTTP POST request and track the Android Context which initiated the request. Set
     * headers only for this request
     *
     * @param context         the Android Context which initiated the request.
     * @param url             the URL to send the request to.
     * @param headers         set headers only for this request
     * @param params          additional POST parameters to send with the request.
     * @param contentType     the content type of the payload you are sending, for example
     *                        application/json if sending a json payload.
     * @param responseHandler the response handler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle post(Context context, String url, Header[] headers, RequestParams params, String contentType,
                              ResponseHandlerInterface responseHandler) {
        HttpEntityEnclosingRequestBase request = new HttpPost(getURI(url));
        if (params != null) request.setEntity(paramsToEntity(params, responseHandler));
        if (headers != null) request.setHeaders(headers);
        return sendRequest(getHttpClient(), buildHttpClientContext(), request, contentType,
                responseHandler, context);
    }

    /**
     * Perform a HTTP POST request and track the Android Context which initiated the request. Set
     * headers only for this request
     *
     * @param context         the Android Context which initiated the request.
     * @param url             the URL to send the request to.
     * @param headers         set headers only for this request
     * @param entity          a raw {@link HttpEntity} to send with the request, for example, use
     *                        this to send string/json/xml payloads to a server by passing a {@link
     *                        cz.msebera.android.httpclient.entity.StringEntity}.
     * @param contentType     the content type of the payload you are sending, for example
     *                        application/json if sending a json payload.
     * @param responseHandler the response handler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle post(Context context, String url, Header[] headers, HttpEntity entity, String contentType,
                              ResponseHandlerInterface responseHandler) {
        HttpEntityEnclosingRequestBase request = addEntityToRequestBase(new HttpPost(getURI(url)), entity);
        if (headers != null) request.setHeaders(headers);
        return sendRequest(getHttpClient(), buildHttpClientContext(), request, contentType, responseHandler, context);
    }

    /**
     * Perform a HTTP PUT request, without any parameters.
     *
     * @param url             the URL to send the request to.
     * @param responseHandler the response handler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle put(String url, ResponseHandlerInterface responseHandler) {
        return put(null, url, null, responseHandler);
    }

    /**
     * Perform a HTTP PUT request with parameters.
     *
     * @param url             the URL to send the request to.
     * @param params          additional PUT parameters or files to send with the request.
     * @param responseHandler the response handler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle put(String url, RequestParams params, ResponseHandlerInterface responseHandler) {
        return put(null, url, params, responseHandler);
    }

    /**
     * Perform a HTTP PUT request and track the Android Context which initiated the request.
     *
     * @param context         the Android Context which initiated the request.
     * @param url             the URL to send the request to.
     * @param params          additional PUT parameters or files to send with the request.
     * @param responseHandler the response handler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle put(Context context, String url, RequestParams params, ResponseHandlerInterface responseHandler) {
        return put(context, url, paramsToEntity(params, responseHandler), null, responseHandler);
    }

    /**
     * Perform a HTTP PUT request and track the Android Context which initiated the request. And set
     * one-time headers for the request
     *
     * @param context         the Android Context which initiated the request.
     * @param url             the URL to send the request to.
     * @param entity          a raw {@link HttpEntity} to send with the request, for example, use
     *                        this to send string/json/xml payloads to a server by passing a {@link
     *                        cz.msebera.android.httpclient.entity.StringEntity}.
     * @param contentType     the content type of the payload you are sending, for example
     *                        application/json if sending a json payload.
     * @param responseHandler the response handler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle put(Context context, String url, HttpEntity entity, String contentType, ResponseHandlerInterface responseHandler) {
        return sendRequest(getHttpClient(), buildHttpClientContext(), addEntityToRequestBase(new HttpPut(getURI(url)), entity), contentType, responseHandler, context);
    }

    /**
     * Perform a HTTP PUT request and track the Android Context which initiated the request. And set
     * one-time headers for the request
     *
     * @param context         the Android Context which initiated the request.
     * @param url             the URL to send the request to.
     * @param headers         set one-time headers for this request
     * @param entity          a raw {@link HttpEntity} to send with the request, for example, use
     *                        this to send string/json/xml payloads to a server by passing a {@link
     *                        cz.msebera.android.httpclient.entity.StringEntity}.
     * @param contentType     the content type of the payload you are sending, for example
     *                        application/json if sending a json payload.
     * @param responseHandler the response handler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle put(Context context, String url, Header[] headers, HttpEntity entity, String contentType, ResponseHandlerInterface responseHandler) {
        HttpEntityEnclosingRequestBase request = addEntityToRequestBase(new HttpPut(getURI(url)), entity);
        if (headers != null) request.setHeaders(headers);
        return sendRequest(getHttpClient(), buildHttpClientContext(), request, contentType, responseHandler, context);
    }

    // [-] HTTP PUT
    // [+] HTTP DELETE

    /**
     * Perform a HTTP
     * request, without any parameters.
     *
     * @param url             the URL to send the request to.
     * @param responseHandler the response handler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle patch(String url, ResponseHandlerInterface responseHandler) {
        return patch(null, url, null, responseHandler);
    }

    /**
     * Perform a HTTP PATCH request with parameters.
     *
     * @param url             the URL to send the request to.
     * @param params          additional PUT parameters or files to send with the request.
     * @param responseHandler the response handler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle patch(String url, RequestParams params, ResponseHandlerInterface responseHandler) {
        return patch(null, url, params, responseHandler);
    }

    /**
     * Perform a HTTP PATCH request and track the Android Context which initiated the request.
     *
     * @param context         the Android Context which initiated the request.
     * @param url             the URL to send the request to.
     * @param params          additional PUT parameters or files to send with the request.
     * @param responseHandler the response handler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle patch(Context context, String url, RequestParams params, ResponseHandlerInterface responseHandler) {
        return patch(context, url, paramsToEntity(params, responseHandler), null, responseHandler);
    }

    /**
     * Perform a HTTP PATCH request and track the Android Context which initiated the request.
     *
     * @param context         the Android Context which initiated the request.
     * @param url             the URL to send the request to.
     * @param responseHandler the response handler instance that should handle the response.
     * @param entity          a raw {@link HttpEntity} to send with the request, for example, use
     *                        this to send string/json/xml payloads to a server by passing a {@link
     *                        cz.msebera.android.httpclient.entity.StringEntity}
     * @param contentType     the content type of the payload you are sending, for example
     *                        "application/json" if sending a json payload.
     * @return RequestHandle of future request process
     */
    public RequestHandle patch(Context context, String url, HttpEntity entity, String contentType, ResponseHandlerInterface responseHandler) {
        return sendRequest(getHttpClient(), buildHttpClientContext(), addEntityToRequestBase(new HttpPatch(getURI(url)), entity), contentType, responseHandler, context);
    }

    /**
     * Perform a HTTP PATCH request and track the Android Context which initiated the request. And set
     * one-time headers for the request
     *
     * @param context         the Android Context which initiated the request.
     * @param url             the URL to send the request to.
     * @param headers         set one-time headers for this request
     * @param entity          a raw {@link HttpEntity} to send with the request, for example, use
     *                        this to send string/json/xml payloads to a server by passing a {@link
     *                        cz.msebera.android.httpclient.entity.StringEntity}.
     * @param contentType     the content type of the payload you are sending, for example
     *                        application/json if sending a json payload.
     * @param responseHandler the response handler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle patch(Context context, String url, Header[] headers, HttpEntity entity, String contentType, ResponseHandlerInterface responseHandler) {
        HttpEntityEnclosingRequestBase request = addEntityToRequestBase(new HttpPatch(getURI(url)), entity);
        if (headers != null) request.setHeaders(headers);
        return sendRequest(getHttpClient(), buildHttpClientContext(), request, contentType, responseHandler, context);
    }

    /**
     * Perform a HTTP DELETE request.
     *
     * @param url             the URL to send the request to.
     * @param responseHandler the response handler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle delete(String url, ResponseHandlerInterface responseHandler) {
        return delete(null, url, responseHandler);
    }

    // [-] HTTP DELETE

    /**
     * Perform a HTTP DELETE request.
     *
     * @param context         the Android Context which initiated the request.
     * @param url             the URL to send the request to.
     * @param responseHandler the response handler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle delete(Context context, String url, ResponseHandlerInterface responseHandler) {
        final HttpDelete delete = new HttpDelete(getURI(url));
        return sendRequest(getHttpClient(), buildHttpClientContext(), delete, null, responseHandler, context);
    }

    /**
     * Perform a HTTP DELETE request.
     *
     * @param context         the Android Context which initiated the request.
     * @param url             the URL to send the request to.
     * @param headers         set one-time headers for this request
     * @param responseHandler the response handler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle delete(Context context, String url, Header[] headers, ResponseHandlerInterface responseHandler) {
        final HttpDelete delete = new HttpDelete(getURI(url));
        if (headers != null) delete.setHeaders(headers);
        return sendRequest(getHttpClient(), buildHttpClientContext(), delete, null, responseHandler, context);
    }

    /**
     * Perform a HTTP DELETE request.
     *
     * @param url             the URL to send the request to.
     * @param params          additional DELETE parameters or files to send with the request.
     * @param responseHandler the response handler instance that should handle the response.
     */
    public void delete(String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
        final HttpDelete delete = new HttpDelete(getUrlWithQueryString(isUrlEncodingEnabled, url, params));
        sendRequest(getHttpClient(), buildHttpClientContext(), delete, null, responseHandler, null);
    }

    /**
     * Perform a HTTP DELETE request.
     *
     * @param context         the Android Context which initiated the request.
     * @param url             the URL to send the request to.
     * @param headers         set one-time headers for this request
     * @param params          additional DELETE parameters or files to send along with request
     * @param responseHandler the response handler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle delete(Context context, String url, Header[] headers, RequestParams params, ResponseHandlerInterface responseHandler) {
        HttpDelete httpDelete = new HttpDelete(getUrlWithQueryString(isUrlEncodingEnabled, url, params));
        if (headers != null) httpDelete.setHeaders(headers);
        return sendRequest(getHttpClient(), buildHttpClientContext(), httpDelete, null, responseHandler, context);
    }

    /**
     * Perform a HTTP DELETE request and track the Android Context which initiated the request.
     *
     * @param context         the Android Context which initiated the request.
     * @param url             the URL to send the request to.
     * @param entity          a raw {@link cz.msebera.android.httpclient.HttpEntity} to send with the request, for
     *                        example, use this to send string/json/xml payloads to a server by
     *                        passing a {@link cz.msebera.android.httpclient.entity.StringEntity}.
     * @param contentType     the content type of the payload you are sending, for example
     *                        application/json if sending a json payload.
     * @param responseHandler the response ha   ndler instance that should handle the response.
     * @return RequestHandle of future request process
     */
    public RequestHandle delete(Context context, String url, HttpEntity entity, String contentType, ResponseHandlerInterface responseHandler) {
        return sendRequest(getHttpClient(), buildHttpClientContext(), addEntityToRequestBase(new HttpDelete(URI.create(url).normalize()), entity), contentType, responseHandler, context);
    }

    /**
     * Instantiate a new asynchronous HTTP request for the passed parameters.
     *
     * @param client          HttpClient to be used for request, can differ in single requests
     * @param contentType     MIME body type, for POST and PUT requests, may be null
     * @param context         Context of Android application, to hold the reference of request
     * @param httpContext     HttpContext in which the request will be executed
     * @param responseHandler ResponseHandler or its subclass to put the response into
     * @param uriRequest      instance of HttpUriRequest, which means it must be of HttpDelete,
     *                        HttpPost, HttpGet, HttpPut, etc.
     * @return AsyncHttpRequest ready to be dispatched
     */
    protected AsyncHttpRequest newAsyncHttpRequest(HttpClient client, HttpContext httpContext, HttpUriRequest uriRequest, String contentType, ResponseHandlerInterface responseHandler, Context context) {
        return new AsyncHttpRequest(client, httpContext, uriRequest, retryHandler, responseHandler);
    }

    /**
     * Puts a new request in queue as a new thread in pool to be executed
     *
     * @param client          HttpClient to be used for request, can differ in single requests
     * @param contentType     MIME body type, for POST and PUT requests, may be null
     * @param context         Context of Android application, to hold the reference of request
     * @param httpContext     HttpContext in which the request will be executed
     * @param responseHandler ResponseHandler or its subclass to put the response into
     * @param uriRequest      instance of HttpUriRequest, which means it must be of HttpDelete,
     *                        HttpPost, HttpGet, HttpPut, etc.
     * @return RequestHandle of future request process
     */
    protected RequestHandle sendRequest(HttpClient client, HttpContext httpContext, HttpUriRequest uriRequest, String contentType, ResponseHandlerInterface responseHandler, Context context) {
        if (uriRequest == null) {
            throw new IllegalArgumentException("HttpUriRequest must not be null");
        }

        if (responseHandler == null) {
            throw new IllegalArgumentException("ResponseHandler must not be null");
        }

        if (responseHandler.getUseSynchronousMode() && !responseHandler.getUsePoolThread()) {
            throw new IllegalArgumentException("Synchronous ResponseHandler used in CachingAsyncHttpClient. You should create your response handler in a looper thread or use SyncHttpClient instead.");
        }

        if (contentType != null) {
            if (uriRequest instanceof HttpEntityEnclosingRequestBase && ((HttpEntityEnclosingRequestBase) uriRequest).getEntity() != null && uriRequest.containsHeader(HEADER_CONTENT_TYPE)) {
                log.w(LOG_TAG, "Passed contentType will be ignored because HttpEntity sets content type");
            } else {
                uriRequest.setHeader(HEADER_CONTENT_TYPE, contentType);
            }
        }
        if (uriRequest instanceof HttpEntityEnclosingRequestBase) {
            ((HttpEntityEnclosingRequestBase) uriRequest).setProtocolVersion(HttpVersion.HTTP_1_1);
        }

        responseHandler.setRequestHeaders(uriRequest.getAllHeaders());
        responseHandler.setRequestURI(uriRequest.getURI());

        AsyncHttpRequest request = newAsyncHttpRequest(client, httpContext, uriRequest, contentType, responseHandler, context);
        threadPool.submit(request);
        RequestHandle requestHandle = new RequestHandle(request);

        if (context != null) {
            List<RequestHandle> requestList;
            // Add request to request map
            synchronized (requestMap) {
                requestList = requestMap.get(context);
                if (requestList == null) {
                    requestList = Collections.synchronizedList(new LinkedList<RequestHandle>());
                    requestMap.put(context, requestList);
                }
            }

            requestList.add(requestHandle);

            Iterator<RequestHandle> iterator = requestList.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().shouldBeGarbageCollected()) {
                    iterator.remove();
                }
            }
        }

        return requestHandle;
    }

    /**
     * Returns a {@link URI} instance for the specified, absolute URL string.
     *
     * @param url absolute URL string, containing scheme, host and path
     * @return URI instance for the URL string
     */
    protected URI getURI(String url) {
        return URI.create(url).normalize();
    }

    /**
     * Sets state of URL encoding feature, see bug #227, this method allows you to turn off and on
     * this auto-magic feature on-demand.
     *
     * @param enabled desired state of feature
     */
    public void setURLEncodingEnabled(boolean enabled) {
        this.isUrlEncodingEnabled = enabled;
    }

    /**
     * Returns HttpEntity containing data from RequestParams included with request declaration.
     * Allows also passing progress from upload via provided ResponseHandler
     *
     * @param params          additional request params
     * @param responseHandler ResponseHandlerInterface or its subclass to be notified on progress
     */
    private HttpEntity paramsToEntity(RequestParams params, ResponseHandlerInterface responseHandler) {
        HttpEntity entity = null;

        try {
            if (params != null) {
                entity = params.getEntity(responseHandler);
            }
        } catch (IOException e) {
            if (responseHandler != null) {
                responseHandler.sendFailureMessage(0, null, null, e);
            } else {
                e.printStackTrace();
            }
        }

        return entity;
    }

    public boolean isUrlEncodingEnabled() {
        return isUrlEncodingEnabled;
    }

    /**
     * Applicable only to HttpRequest methods extending HttpEntityEnclosingRequestBase, which is for
     * example not DELETE
     *
     * @param entity      entity to be included within the request
     * @param requestBase HttpRequest instance, must not be null
     */
    private HttpEntityEnclosingRequestBase addEntityToRequestBase(HttpEntityEnclosingRequestBase requestBase, HttpEntity entity) {
        if (entity != null) {
            requestBase.setEntity(entity);
        }

        return requestBase;
    }

    /**
     * Enclosing entity to hold stream of gzip decoded data for accessing HttpEntity contents
     */
//    private static class InflatingEntity extends HttpEntityWrapper {
//
//        InputStream wrappedStream;
//
//        public InflatingEntity(HttpEntity wrapped) {
//            super(wrapped);
//        }
//
//        @Override
//        public InputStream getContent() throws IOException {
//            if(wrappedStream == null) {
//                wrappedStream = wrappedEntity.getContent();
//                PushbackInputStream pushbackStream = new PushbackInputStream(wrappedStream, 2);
//                if (isInputStreamGZIPCompressed(pushbackStream)) {
//                    wrappedStream = new GZIPInputStream(pushbackStream);
//                } else {
//                    wrappedStream = pushbackStream;
//                }
//            }
//            return wrappedStream;
//        }
//
//        @Override
//        public long getContentLength() {
//            return wrappedEntity == null ? 0 : wrappedEntity.getContentLength();
//        }
//
//        @Override
//        public void consumeContent() throws IOException {
//            silentCloseInputStream(wrappedStream);
//            super.consumeContent();
//        }
//    }
    private static class InflatingEntity extends HttpEntityWrapper {

        InputStream wrappedStream;
        PushbackInputStream pushbackStream;
        GZIPInputStream gzippedStream;

        public InflatingEntity(HttpEntity wrapped) {
            super(wrapped);
        }

        @Override
        public InputStream getContent() throws IOException {
            wrappedStream = wrappedEntity.getContent();
            pushbackStream = new PushbackInputStream(wrappedStream, 2);
            if (isInputStreamGZIPCompressed(pushbackStream)) {
                gzippedStream = new GZIPInputStream(pushbackStream);
                return gzippedStream;
            } else {
                return pushbackStream;
            }
        }

        @Override
        public long getContentLength() {
            return wrappedEntity == null ? 0 : wrappedEntity.getContentLength();
        }

        @Override
        public void consumeContent() throws IOException {
            AsyncHttpClient.silentCloseInputStream(wrappedStream);
            AsyncHttpClient.silentCloseInputStream(pushbackStream);
            AsyncHttpClient.silentCloseInputStream(gzippedStream);
            super.consumeContent();
        }
    }
}
