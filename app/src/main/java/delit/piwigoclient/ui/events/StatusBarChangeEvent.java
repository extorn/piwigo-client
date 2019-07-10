package delit.piwigoclient.ui.events;

public class StatusBarChangeEvent {
    private final boolean isVisible;

    public StatusBarChangeEvent(boolean isVisible) {
        this.isVisible = isVisible;
    }

    public boolean isVisible() {
        return isVisible;
    }
}
