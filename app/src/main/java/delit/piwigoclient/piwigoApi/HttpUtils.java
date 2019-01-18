package delit.piwigoclient.piwigoApi;

import android.content.Context;
import android.os.Build;

import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLHandshakeException;

import cz.msebera.android.httpclient.Header;
import delit.piwigoclient.R;
import delit.piwigoclient.util.X509Utils;

/**
 * Created by gareth on 25/06/17.
 */

public class HttpUtils {
    public static String[] getHttpErrorMessage(Context c, int statusCode, Throwable error) {
        String[] retVal = new String[2];
        retVal[1] = ""; // default to no extra detail.

        String errorMessage = "";
        String errorDetail = "";
        if (error != null) {
            errorMessage = error.getMessage() != null ? error.getMessage() : c.getString(R.string.undefined_error_msg);
        }
        Throwable cause;
        if (error instanceof SSLHandshakeException) {
            cause = error.getCause();
            if (cause instanceof CertificateException) {
                cause = cause.getCause();
                if (cause instanceof CertPathValidatorException) {
                    errorMessage = "";
                    CertPathValidatorException certPathException = (CertPathValidatorException) cause;
                    cause = certPathException.getCause();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        CertPathValidatorException.Reason r = certPathException.getReason();
                        if (r == CertPathValidatorException.BasicReason.EXPIRED) {
                            X509Certificate cert = X509Utils.findFirstExpiredCert(certPathException.getCertPath().getCertificates());
                            String certName = cert.getSubjectX500Principal().getName();
                            errorDetail = "Certificate : " + certName + "\n\n";
                            // test expiry date
                        } else if (r == CertPathValidatorException.BasicReason.NOT_YET_VALID) {
                            // test expiry date
                            X509Certificate cert = X509Utils.findFirstCertNotYetValid(certPathException.getCertPath().getCertificates());
                            String certName = cert.getSubjectX500Principal().getName();
                            errorDetail = "Certificate : " + certName + "\n\n";
                        }
                    }
                    if(cause != null) {
                        errorMessage += cause.getMessage();
                    }
                }
            }
        }
        if (statusCode >= 400 && statusCode < 500) {
            errorMessage = c.getString(R.string.resource_unavailable_error_msg_pattern, statusCode);
        }
        // When Http response code is '500'
        else if (statusCode >= 500 && statusCode < 600) {
            errorMessage = c.getString(R.string.server_error_msg_pattern, statusCode);
        }
        // When Http response code other than 4xx and 5xx
        else if (statusCode >= 300 && statusCode < 400) {
            errorMessage = c.getString(R.string.redirect_error_msg_pattern, statusCode);
            errorDetail = c.getString(R.string.http_error_msg_pattern, errorMessage);
        } else if (statusCode >= 100 && statusCode < 200) {
            errorMessage = c.getString(R.string.unexpected_response_error_msg_pattern, statusCode);
            errorDetail = c.getString(R.string.http_error_msg_pattern, errorMessage);
        } else if (statusCode == 0) {
            if (error != null && error.getCause() != null && error.getCause().getMessage() != null) {
                String detail = error.getCause().getMessage();
                if(detail.contains("Trust anchor for certification path not found")) {
                    errorMessage += c.getString(R.string.certificate_chain_not_verifiable_error);
                    errorDetail += c.getString(R.string.certificate_chain_not_verifiable_error_detail);
                } else {
                    String moreInfo = "Error : \n" + detail;
                    if(errorMessage.isEmpty()) {
                        errorMessage += moreInfo;
                    } else {
                        errorDetail += moreInfo;
                    }
                }
            } else {
                errorMessage = c.getString(R.string.connection_error_msg_pattern);
                errorDetail = c.getString(R.string.http_error_msg_pattern, errorMessage);
            }
        } else if (statusCode < 0) {
            if (error != null && error.getCause() != null && error.getCause().getMessage() != null) {
                String detail = error.getCause().getMessage();
                if(detail.contains("Trust anchor for certification path not found")) {
                    errorMessage += c.getString(R.string.certificate_chain_not_verifiable_error);
                    errorDetail += c.getString(R.string.certificate_chain_not_verifiable_error_detail);
                } else {
                    String moreInfo = "Error : \n" + detail;
                    if(errorMessage.isEmpty()) {
                        errorMessage += moreInfo;
                    } else {
                        errorDetail += moreInfo;
                    }
                }
            }
        } else {
            errorMessage = c.getString(R.string.unexpected_response_error_msg_pattern, statusCode);
            errorDetail = c.getString(R.string.http_error_msg_pattern, errorMessage);
        }
        retVal[0] = errorMessage;
        retVal[1] = errorDetail;
        return retVal;
    }

    public static Header getContentTypeHeader(Header[] headers) {
        if(headers != null) {
            for(Header h : headers) {
                if(h.getName().equalsIgnoreCase("Content-Type")) {
                    return h;
                }
            }
        }
        return null;
    }
}
