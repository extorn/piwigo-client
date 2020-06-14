package delit.piwigoclient.ui.events;

import delit.libs.ui.events.SingleUseEvent;
import delit.piwigoclient.business.ConnectionPreferences;

public class BadRequestExposesInternalServerEvent extends SingleUseEvent {
    private final String oldAuthority;
    private final String newAuthority;
    private ConnectionPreferences.ProfilePreferences connectionPreferences;

    public BadRequestExposesInternalServerEvent(ConnectionPreferences.ProfilePreferences connectionPreferences, String oldAuthority, String newAuthority) {
        this.connectionPreferences = connectionPreferences;
        this.oldAuthority = oldAuthority;
        this.newAuthority = newAuthority;
    }

    public ConnectionPreferences.ProfilePreferences getConnectionPreferences() {
        return connectionPreferences;
    }

    public String getOldAuthority() {
        return oldAuthority;
    }

    public String getNewAuthority() {
        return newAuthority;
    }
}
