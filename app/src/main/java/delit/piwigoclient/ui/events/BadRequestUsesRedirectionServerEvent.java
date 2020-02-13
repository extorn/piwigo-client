package delit.piwigoclient.ui.events;

import android.net.Uri;

import delit.piwigoclient.business.ConnectionPreferences;

public class BadRequestUsesRedirectionServerEvent {
    private final Uri failedUri;
    private ConnectionPreferences.ProfilePreferences connectionPreferences;

    public BadRequestUsesRedirectionServerEvent(ConnectionPreferences.ProfilePreferences connectionPreferences, Uri failedUri) {
        this.connectionPreferences = connectionPreferences;
        this.failedUri = failedUri;
    }

    public ConnectionPreferences.ProfilePreferences getConnectionPreferences() {
        return connectionPreferences;
    }

    public Uri getFailedUri() {
        return failedUri;
    }
}
