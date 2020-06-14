package delit.piwigoclient.business;

import com.squareup.picasso.Downloader;

public class CustomResponseException extends Downloader.ResponseException {
    private static final long serialVersionUID = 8240260872477833970L;
    private final int responseCode;

    public CustomResponseException(String message, int networkPolicy, int responseCode) {
        super(message, networkPolicy, responseCode);
        this.responseCode = responseCode;
    }

    public int getResponseCode() {
        return responseCode;
    }
}
