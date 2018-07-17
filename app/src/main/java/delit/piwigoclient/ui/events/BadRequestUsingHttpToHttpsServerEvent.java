package delit.piwigoclient.ui.events;

import delit.piwigoclient.business.ConnectionPreferences;

public class BadRequestUsingHttpToHttpsServerEvent {
    private ConnectionPreferences.ProfilePreferences connectionPreferences;

    public BadRequestUsingHttpToHttpsServerEvent(ConnectionPreferences.ProfilePreferences connectionPreferences) {
        this.connectionPreferences = connectionPreferences;
    }

    public ConnectionPreferences.ProfilePreferences getConnectionPreferences() {
        return connectionPreferences;
    }
}
