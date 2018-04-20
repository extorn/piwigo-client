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
        if(!enableRedirects) {
            return false;
        }
        return super.isRedirectable(method);
    }
}
