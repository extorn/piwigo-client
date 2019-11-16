package delit.piwigoclient.ui.slideshow;

public class ModelUnavailableException extends RuntimeException {
    public ModelUnavailableException(String errorMsg) {
        super(errorMsg);
    }

    public ModelUnavailableException() {

    }
}
