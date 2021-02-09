package delit.libs.util.security;

import android.os.Build;

import androidx.annotation.RequiresApi;

/**
 * Created by gareth on 24/02/18.
 */

public class KeyStoreOperationException extends SecurityOperationException {

    private static final long serialVersionUID = 2302145293715586789L;

    public KeyStoreOperationException(String dataSource, String message, Throwable cause) {
        super(dataSource, message, cause);
    }

    @RequiresApi(Build.VERSION_CODES.N)
    public KeyStoreOperationException(String dataSource, String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(dataSource, message, cause, enableSuppression, writableStackTrace);
    }
}
