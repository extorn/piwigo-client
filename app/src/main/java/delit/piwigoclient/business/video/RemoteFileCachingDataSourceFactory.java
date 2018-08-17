package delit.piwigoclient.business.video;

import android.content.Context;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;

public class RemoteFileCachingDataSourceFactory extends HttpDataSource.BaseFactory {

    /**
     * The default connection timeout, in milliseconds.
     */
    public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 8 * 1000;
    /**
     * The default read timeout, in milliseconds.
     */
    public static final int DEFAULT_READ_TIMEOUT_MILLIS = 8 * 1000;

    private final TransferListener<? super DataSource> transferListener;
    private final String userAgent;
    private final Context context;
    private final RemoteAsyncFileCachingDataSource.CacheListener cacheListener;
    private boolean redirectsAllowed;
    private int maxRedirects;
    private int connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MILLIS;
    private int readTimeoutMillis = DEFAULT_READ_TIMEOUT_MILLIS;
    private boolean cachingEnabled;
    private CustomDatasourceLoadControlPauseListener loadControlPauseListener;
    private RemoteDirectHttpClientBasedHttpDataSource.DownloadListener directDownloadListener;


    public RemoteFileCachingDataSourceFactory(Context context, TransferListener<? super DataSource> transferListener, RemoteAsyncFileCachingDataSource.CacheListener cacheListener, RemoteDirectHttpClientBasedHttpDataSource.DownloadListener directDownloadListener, String userAgent) {
        this.transferListener = transferListener;
        this.context = context;
        this.userAgent = userAgent;
        this.cacheListener = cacheListener;
        this.directDownloadListener = directDownloadListener;
        loadControlPauseListener = new CustomDatasourceLoadControlPauseListener();
    }

    @Override
    protected HttpDataSource createDataSourceInternal(HttpDataSource.RequestProperties defaultRequestProperties) {
        if (cachingEnabled) {
            return createCachingDataSource(defaultRequestProperties);
        } else {
            return createDirectDataSource(defaultRequestProperties);
        }

    }

    private HttpDataSource createDirectDataSource(HttpDataSource.RequestProperties defaultRequestProperties) {
        RemoteDirectHttpClientBasedHttpDataSource ds = new RemoteDirectHttpClientBasedHttpDataSource(context, userAgent, null, transferListener, connectTimeoutMillis,
                readTimeoutMillis, defaultRequestProperties);
        ds.setEnableRedirects(redirectsAllowed);
        ds.setMaxRedirects(maxRedirects);
        ds.setDownloadListener(directDownloadListener);
        loadControlPauseListener.setDataSource(ds);
        return ds;
    }

    private HttpDataSource createCachingDataSource(HttpDataSource.RequestProperties defaultRequestProperties) {
        RemoteAsyncFileCachingDataSource ds = new RemoteAsyncFileCachingDataSource(context, transferListener, cacheListener, defaultRequestProperties, userAgent);
        ds.setEnableRedirects(redirectsAllowed);
        ds.setMaxRedirects(maxRedirects);
        ds.setConnectTimeoutMillis(connectTimeoutMillis);
        ds.setReadTimeoutMillis(readTimeoutMillis);
        loadControlPauseListener.setDataSource(ds);
        return ds;
    }

    public boolean setRedirectsAllowed(boolean redirectsAllowed) {
        boolean changed = false;
        if (this.redirectsAllowed != redirectsAllowed) {
            changed = true;
        }
        this.redirectsAllowed = redirectsAllowed;
        return changed;
    }

    public boolean setMaxRedirects(int maxRedirects) {
        boolean changed = false;
        if (this.maxRedirects != maxRedirects) {
            changed = true;
        }
        this.maxRedirects = maxRedirects;
        return changed;

    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public void setReadTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public boolean setCachingEnabled(boolean cachingEnabled) {
        boolean changed = false;
        if (this.cachingEnabled != cachingEnabled) {
            changed = true;
        }
        this.cachingEnabled = cachingEnabled;
        return changed;

    }

    public boolean isCachingEnabled() {
        return cachingEnabled;
    }

    public PausableLoadControl.Listener getLoadControlPauseListener() {
        return loadControlPauseListener;
    }

    private class CustomDatasourceLoadControlPauseListener implements PausableLoadControl.Listener {
        HttpDataSource dataSource;

        public void setDataSource(HttpDataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public void onPause() {
            if (dataSource instanceof RemoteAsyncFileCachingDataSource) {
                ((RemoteAsyncFileCachingDataSource) dataSource).pauseBackgroundLoad();
            }
        }

        @Override
        public void onResume() {
            if (dataSource instanceof RemoteAsyncFileCachingDataSource) {
                ((RemoteAsyncFileCachingDataSource) dataSource).resumeBackgroundLoad();
            }
        }
    }
}
