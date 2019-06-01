package delit.piwigoclient.piwigoApi.http;

import java.io.Closeable;

import cz.msebera.android.httpclient.client.cache.HttpCacheStorage;
import cz.msebera.android.httpclient.impl.client.cache.CachingHttpClientBuilder;
import cz.msebera.android.httpclient.impl.execchain.ClientExecChain;

public class MyCachingHttpClientBuilder extends CachingHttpClientBuilder {

    private final boolean ignoreServerCacheDirectives;

    public MyCachingHttpClientBuilder(boolean ignoreServerCacheDirectives) {
        this.ignoreServerCacheDirectives = ignoreServerCacheDirectives;
    }

    @Override
    protected ClientExecChain decorateMainExec(final ClientExecChain mainExec) {
        if(ignoreServerCacheDirectives) {
            return super.decorateMainExec(new PiwigoCachingExec(mainExec));
        } else {
            return super.decorateMainExec(mainExec);
        }
    }

    public <T extends HttpCacheStorage & Closeable> MyCachingHttpClientBuilder setCloseableHttpCacheStorage(T cacheStorage) {
        setHttpCacheStorage(cacheStorage);
        addCloseable(cacheStorage);
        return this;
    }

}