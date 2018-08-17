package delit.piwigoclient.ui.events;

import delit.piwigoclient.business.ConnectionPreferences;

public class BadRequestUsesRedirectionServerEvent {
    private ConnectionPreferences.ProfilePreferences connectionPreferences;

    public BadRequestUsesRedirectionServerEvent(ConnectionPreferences.ProfilePreferences connectionPreferences) {
        this.connectionPreferences = connectionPreferences;
    }

    public ConnectionPreferences.ProfilePreferences getConnectionPreferences() {
        return connectionPreferences;
    }
}
