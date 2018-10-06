package delit.piwigoclient.ui.events;

public class ServerConnectionWarningEvent {
    private final String message;

    public ServerConnectionWarningEvent(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
