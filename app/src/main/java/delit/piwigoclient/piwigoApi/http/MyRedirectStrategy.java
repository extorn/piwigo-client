package delit.piwigoclient.piwigoApi.http;


import cz.msebera.android.httpclient.impl.client.LaxRedirectStrategy;

/**
 * Created by gareth on 12/10/17.
 */

public class MyRedirectStrategy extends LaxRedirectStrategy {

    final boolean enableRedirects;

    public MyRedirectStrategy(final boolean allowRedirects) {
        super();
        this.enableRedirects = allowRedirects;
    }

    @Override
    protected boolean isRedirectable(String method) {
        return enableRedirects && super.isRedirectable(method);
    }
}
