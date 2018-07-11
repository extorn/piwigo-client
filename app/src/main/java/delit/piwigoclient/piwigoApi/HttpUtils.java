package delit.piwigoclient.piwigoApi;

import android.os.Build;

import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLHandshakeException;

import delit.piwigoclient.util.X509Utils;

/**
 * Created by gareth on 25/06/17.
 */

public class HttpUtils {
    public static String getHttpErrorMessage(int statusCode, Throwable error) {
        String errorMessage = null;
        if (error != null) {
            errorMessage = error.getMessage();
        }
        Throwable cause;
        if(error instanceof SSLHandshakeException) {
            cause = error.getCause();
            if(cause instanceof CertificateException) {
                cause = cause.getCause();
                if(cause instanceof CertPathValidatorException) {
                    errorMessage = "";
                    CertPathValidatorException certPathException = (CertPathValidatorException) cause;
                    cause = certPathException.getCause();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        CertPathValidatorException.Reason r = certPathException.getReason();
                        if(r == CertPathValidatorException.BasicReason.EXPIRED) {
                            X509Certificate cert = X509Utils.findFirstExpiredCert(certPathException.getCertPath().getCertificates());
                            String certName = cert.getSubjectX500Principal().getName();
                            errorMessage = "\nCertificate : " + certName + "\n\n";
                            // test expiry date
                        } else if(r == CertPathValidatorException.BasicReason.NOT_YET_VALID) {
                            // test expiry date
                            X509Certificate cert = X509Utils.findFirstCertNotYetValid(certPathException.getCertPath().getCertificates());
                            String certName = cert.getSubjectX500Principal().getName();
                            errorMessage = "\nCertificate : " + certName + "\n\n";
                        }
                    }
                    errorMessage += cause.getMessage();
                }
            }
        }
        String message;// When Http response code is '404'
        if (statusCode >= 400 && statusCode < 500) {
            message = "Requested resource could not be accessed.\nHTTP Status Code (" + statusCode + ")";
        }
        // When Http response code is '500'
        else if (statusCode >= 500 && statusCode < 600) {
            message = "Something went wrong in the server or between the server and your device.\nHTTP Status Code (" + statusCode + ")";
        }
        // When Http response code other than 4xx and 5xx
        else if (statusCode >= 300 && statusCode < 400) {
            message = "Request redirection error : \nHTTP Status Code (" + statusCode + ")" + "\nHTTP Error Message (" + errorMessage + ")";
        } else if (statusCode >= 100 && statusCode < 200) {
            message = "Unexpected response : \nHTTP Status Code (" + statusCode + ")" + "\nHTTP Error Message (" + errorMessage + ")";
        } else if (statusCode == 0) {
            message = "Unable to connect to server : \nHTTP Error Message (" + errorMessage + ")";
        } else if (statusCode < 0) {
            message = errorMessage;
            if (error != null && error.getCause() != null && error.getCause().getMessage() != null) {
                message += " : \n" + error.getCause().getMessage();
            }
        } else {
            message = "Unexpected response : \nHTTP Status Code (" + statusCode + ")" + "\nHTTP Error Message (" + errorMessage + ")";
        }
        return message;
    }
}
