package delit.piwigoclient.ui.events;

public class PiwigoMethodNowUnavailableUsingFallback {
    private final String failedOriginalMethod;
    private final String fallbackMethod;

    public PiwigoMethodNowUnavailableUsingFallback(String failedOriginalMethod, String fallbackMethod ) {
        this.failedOriginalMethod = failedOriginalMethod;
        this.fallbackMethod = fallbackMethod;
    }

    public String getFailedOriginalMethod() {
        return failedOriginalMethod;
    }

    public String getFallbackMethod() {
        return fallbackMethod;
    }
}
