package delit.piwigoclient.piwigoApi.http;

import cz.msebera.android.httpclient.impl.client.cache.CachingHttpClientBuilder;
import cz.msebera.android.httpclient.impl.execchain.ClientExecChain;
import delit.piwigoclient.business.ConnectionPreferences;

class MyCachingHttpClientBuilder extends CachingHttpClientBuilder {

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

}