package delit.libs.util;

import android.net.Uri;

import org.greenrobot.eventbus.EventBus;

import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.ui.events.BadRequestExposesInternalServerEvent;

public class UriUtils {

    public static String sanityCheckFixAndReportUri(String uri, String knownCorrectServerUri, boolean forceHttps, ConnectionPreferences.ProfilePreferences connectionPrefs) {
        String resourceUrl = uri;

        Uri resourceUri = Uri.parse(resourceUrl).normalizeScheme();
        Uri piwigoServerUri = Uri.parse(knownCorrectServerUri);
        Uri.Builder builder = null;
        boolean serverCorrected = false;

        if (forceHttps && "http".equals(resourceUri.getScheme())) {
            builder = resourceUri.buildUpon();
            builder = builder.scheme("https");
        }
        String piwigoServerAuthority = piwigoServerUri.getAuthority();

        if (piwigoServerAuthority != null && !piwigoServerAuthority.equalsIgnoreCase(resourceUri.getAuthority())) {
            if (builder == null) {
                builder = resourceUri.buildUpon();
            }
            builder = builder.authority(piwigoServerUri.getAuthority());
            serverCorrected = true;
        }
        Uri newUri = null;
        if (builder != null) {
            newUri = builder.build().normalizeScheme();
            resourceUrl = newUri.toString();
        }

        if (serverCorrected && newUri != null) {
            EventBus.getDefault().post(new BadRequestExposesInternalServerEvent(connectionPrefs, resourceUri.getAuthority(), newUri.getAuthority()));
        }

        return resourceUrl;
    }
}
