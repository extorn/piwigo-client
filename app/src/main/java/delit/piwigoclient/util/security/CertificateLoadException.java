package delit.piwigoclient.util.security;

import android.os.Build;
import android.support.annotation.RequiresApi;

import java.io.File;

/**
 * Created by gareth on 24/02/18.
 */

public class CertificateLoadException extends SecurityOperationException {

    public CertificateLoadException(File certificateFile, String message, Throwable cause) {
        super(certificateFile, message, cause);
    }

    public CertificateLoadException(File certificateFile, Throwable cause) {
        super(certificateFile, cause);
    }

    @RequiresApi(Build.VERSION_CODES.N)
    public CertificateLoadException(File certificateFile, String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(certificateFile, message, cause, enableSuppression, writableStackTrace);
    }
}
