package delit.libs.util.security;

import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.File;

/**
 * Created by gareth on 24/02/18.
 */

public class CertificateLoadException extends SecurityOperationException {

    public CertificateLoadException(String keystoreSource, String message, Throwable cause) {
        super(keystoreSource, message, cause);
    }

    @RequiresApi(Build.VERSION_CODES.N)
    public CertificateLoadException(String keystoreSource, String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(keystoreSource, message, cause, enableSuppression, writableStackTrace);
    }
}
