package delit.piwigoclient.ui.events;

public class ServerUpdatesAvailableEvent {
    private boolean serverUpdateAvailable;
    private boolean pluginUpdateAvailable;


    public ServerUpdatesAvailableEvent(boolean serverUpdateAvailable, boolean pluginUpdateAvailable) {
        this.serverUpdateAvailable = serverUpdateAvailable;
        this.pluginUpdateAvailable = pluginUpdateAvailable;
    }

    public boolean isServerUpdateAvailable() {
        return serverUpdateAvailable;
    }

    public boolean isPluginUpdateAvailable() {
        return pluginUpdateAvailable;
    }
}
