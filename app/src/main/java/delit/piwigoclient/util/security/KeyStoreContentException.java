package delit.piwigoclient.util.security;

import android.os.Build;
import android.support.annotation.RequiresApi;

import delit.piwigoclient.BuildConfig;

/**
 * Created by gareth on 24/02/18.
 */

public class KeyStoreContentException extends SecurityOperationException {
    private final String alias;

    public KeyStoreContentException(String alias, String message, Throwable cause) {
        super(message, cause);
        this.alias = alias;
    }

    public KeyStoreContentException(String alias, Throwable cause) {
        super(cause);
        this.alias = alias;
    }

    @RequiresApi(Build.VERSION_CODES.N)
    public KeyStoreContentException(String alias, String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.alias = alias;
    }

    public String getAlias() {
        return alias;
    }
}