/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package delit.piwigoclient.business.video;

import android.content.Context;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource.BaseFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource.Factory;
import com.google.android.exoplayer2.upstream.TransferListener;

/**
 * A {@link Factory} that produces {@link HttpClientBasedHttpDataSource} instances.
 */
public final class CustomHttpDataSourceFactory extends BaseFactory {

    private final String userAgent;
    private final TransferListener<? super DataSource> listener;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final boolean allowCrossProtocolRedirects;
    private final Context context;
    private HttpClientBasedHttpDataSource.CacheListener cacheListener;
    private boolean cachingEnabled;
    private boolean notifyCacheListenerImmediatelyIfCached = true;
    private boolean redirectsAllowed;
    private int maxRedirects;

    /**
     * Constructs a CustomHttpDataSourceNewFactory. Sets {@link
     * HttpClientBasedHttpDataSource#DEFAULT_CONNECT_TIMEOUT_MILLIS} as the connection timeout, {@link
     * HttpClientBasedHttpDataSource#DEFAULT_READ_TIMEOUT_MILLIS} as the read timeout and disables
     * cross-protocol redirects.
     *
     * @param userAgent The User-Agent string that should be used.
     */
    public CustomHttpDataSourceFactory(Context context, String userAgent) {
        this(context, userAgent, null, null);
    }

    /**
     * Constructs a CustomHttpDataSourceNewFactory. Sets {@link
     * HttpClientBasedHttpDataSource#DEFAULT_CONNECT_TIMEOUT_MILLIS} as the connection timeout, {@link
     * HttpClientBasedHttpDataSource#DEFAULT_READ_TIMEOUT_MILLIS} as the read timeout and disables
     * cross-protocol redirects.
     *
     * @param userAgent The User-Agent string that should be used.
     * @param listener  An optional listener.
     * @see #CustomHttpDataSourceFactory(Context, String, TransferListener, delit.piwigoclient.business.video.HttpClientBasedHttpDataSource.CacheListener, int, int, boolean)
     */
    public CustomHttpDataSourceFactory(
            Context context, String userAgent, TransferListener<? super DataSource> listener, HttpClientBasedHttpDataSource.CacheListener cacheListener) {
        this(context, userAgent, listener, cacheListener, HttpClientBasedHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
                HttpClientBasedHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS, false);
    }

    /**
     * @param userAgent                   The User-Agent string that should be used.
     * @param listener                    An optional listener.
     * @param connectTimeoutMillis        The connection timeout that should be used when requesting remote
     *                                    data, in milliseconds. A timeout of zero is interpreted as an infinite timeout.
     * @param readTimeoutMillis           The read timeout that should be used when requesting remote data, in
     *                                    milliseconds. A timeout of zero is interpreted as an infinite timeout.
     * @param allowCrossProtocolRedirects Whether cross-protocol redirects (i.e. redirects from HTTP
     *                                    to HTTPS and vice versa) are enabled.
     */
    public CustomHttpDataSourceFactory(Context context, String userAgent,
                                       TransferListener<? super DataSource> listener, HttpClientBasedHttpDataSource.CacheListener cacheListener, int connectTimeoutMillis,
                                       int readTimeoutMillis, boolean allowCrossProtocolRedirects) {
        this.userAgent = userAgent;
        this.listener = listener;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.allowCrossProtocolRedirects = allowCrossProtocolRedirects;
        this.context = context;
        this.cacheListener = cacheListener;
    }

    @Override
    protected HttpClientBasedHttpDataSource createDataSourceInternal(
            HttpDataSource.RequestProperties defaultRequestProperties) {
        HttpClientBasedHttpDataSource ds = new HttpClientBasedHttpDataSource(context, userAgent, null, listener, connectTimeoutMillis,
                readTimeoutMillis, allowCrossProtocolRedirects, defaultRequestProperties, cachingEnabled, notifyCacheListenerImmediatelyIfCached);
        notifyCacheListenerImmediatelyIfCached = false;
        ds.setCacheListener(cacheListener);
        ds.setEnableRedirects(redirectsAllowed);
        ds.setMaxRedirects(maxRedirects);
        return ds;
    }

    public boolean isCachingEnabled() {
        return cachingEnabled;
    }

    public void setCachingEnabled(boolean cachingEnabled) {
        this.cachingEnabled = cachingEnabled;
    }

    public void setRedirectsAllowed(boolean redirectsAllowed) {
        this.redirectsAllowed = redirectsAllowed;
    }

    public void setMaxRedirects(int maxRedirects) {
        this.maxRedirects = maxRedirects;
    }
}
