package delit.libs.util.security;

import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.File;

/**
 * Created by gareth on 24/02/18.
 */

public class KeyStoreOperationException extends SecurityOperationException {

    public KeyStoreOperationException(File file, String message, Throwable cause) {
        super(file, message, cause);
    }

    public KeyStoreOperationException(File file, Throwable cause) {
        super(file, cause);
    }

    @RequiresApi(Build.VERSION_CODES.N)
    public KeyStoreOperationException(File file, String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(file, message, cause, enableSuppression, writableStackTrace);
    }
}
