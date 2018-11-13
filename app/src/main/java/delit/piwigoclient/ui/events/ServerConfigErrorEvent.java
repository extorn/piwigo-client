package delit.piwigoclient.ui.events;

public class ServerConfigErrorEvent {
    private String message;

    public ServerConfigErrorEvent(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
