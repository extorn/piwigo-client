package delit.libs.util.security;

import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.File;

/**
 * Created by gareth on 24/02/18.
 */

public class SecurityOperationException extends RuntimeException {
    private String dataSource;

    public SecurityOperationException(Throwable cause) {
        super(cause);
    }

    @RequiresApi(Build.VERSION_CODES.N)
    public SecurityOperationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public SecurityOperationException(String dataSource, String message, Throwable cause) {
        super(message, cause);
        this.dataSource = dataSource;
    }


    @RequiresApi(Build.VERSION_CODES.N)
    public SecurityOperationException(String dataSource, String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.dataSource = dataSource;
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }
}
