package delit.piwigoclient.util.security;

import android.os.Build;
import androidx.annotation.RequiresApi;

import java.io.File;

/**
 * Created by gareth on 24/02/18.
 */

public class SecurityOperationException extends RuntimeException {
    private File file;

    public SecurityOperationException(String message, Throwable cause) {
        super(message, cause);
    }

    public SecurityOperationException(Throwable cause) {
        super(cause);
    }

    @RequiresApi(Build.VERSION_CODES.N)
    public SecurityOperationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public SecurityOperationException(File file, String message, Throwable cause) {
        super(message, cause);
        this.file = file;
    }

    public SecurityOperationException(File file, Throwable cause) {
        super(cause);
        this.file = file;
    }

    @RequiresApi(Build.VERSION_CODES.N)
    public SecurityOperationException(File file, String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }
}
