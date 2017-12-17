package delit.piwigoclient.piwigoApi;

/**
 * Created by gareth on 25/06/17.
 */

public class HttpUtils {
    public static String getHttpErrorMessage(int statusCode, Throwable error) {
        String errorMessage = null;
        if(error != null) {
            errorMessage = error.getMessage();
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
        }
        else if (statusCode >= 100 && statusCode < 200) {
            message = "Unexpected response : \nHTTP Status Code (" + statusCode + ")" + "\nHTTP Error Message (" + errorMessage + ")";
        }
        else if (statusCode == 0) {
            message = "Unable to connect to server : \nHTTP Error Message (" + errorMessage + ")";
        } else if (statusCode < 0) {
            message = errorMessage;
            if(error != null && error.getCause() != null && error.getCause().getMessage() != null) {
                message += " : \n" + error.getCause().getMessage();
            }
        } else {
            message = "Unexpected response : \nHTTP Status Code (" + statusCode + ")" + "\nHTTP Error Message (" + errorMessage + ")";
        }
        return message;
    }
}
