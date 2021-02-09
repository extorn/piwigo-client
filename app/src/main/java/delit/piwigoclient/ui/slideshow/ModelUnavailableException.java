package delit.piwigoclient.ui.slideshow;

public class ModelUnavailableException extends RuntimeException {
    private static final long serialVersionUID = -6062767738564172113L;

    public ModelUnavailableException(String errorMsg) {
        super(errorMsg);
    }

    public ModelUnavailableException(String errorMsg, Throwable e) {
        super(errorMsg, e);
    }

    public ModelUnavailableException() {

    }
}
