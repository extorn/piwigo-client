package delit.libs.util;

import android.net.Uri;

import org.greenrobot.eventbus.EventBus;

import java.util.List;

import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.ui.events.BadRequestExposesInternalServerEvent;

public class UriUtils {

    public static String sanityCheckFixAndReportUri(String uri, String knownCorrectServerUri, boolean forceHttps, boolean testForExposingProxiedServer, ConnectionPreferences.ProfilePreferences connectionPrefs) {
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

        if (testForExposingProxiedServer) {
            if (piwigoServerAuthority != null && !piwigoServerAuthority.equalsIgnoreCase(resourceUri.getAuthority())) {
                if (builder == null) {
                    builder = resourceUri.buildUpon();
                }
                builder = builder.authority(piwigoServerUri.getAuthority());
                serverCorrected = true;
            }
        }
        Uri newUri = null;
        if (builder != null) {
            newUri = builder.build().normalizeScheme();
            resourceUrl = newUri.toString();
        } else {
            resourceUrl = resourceUri.toString();
        }

        if (serverCorrected && newUri != null) {
            EventBus.getDefault().post(new BadRequestExposesInternalServerEvent(connectionPrefs, resourceUri.getAuthority(), newUri.getAuthority()));
        }

        return resourceUrl;
    }

    public static String encodeUriSegments(Uri uri) {

        List<String> pathSegments = uri.getPathSegments();
        Uri.Builder builder = uri.buildUpon().encodedPath(null);

        boolean pathSegmentsPossiblyAlreadyEncoded = false;
        for (int i = 0; i < pathSegments.size(); i++) {
            builder.appendEncodedPath(Uri.encode(pathSegments.get(i)));
        }
        return builder.build().toString();
    }
}
