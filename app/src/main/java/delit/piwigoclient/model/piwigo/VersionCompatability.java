package delit.piwigoclient.model.piwigo;

import java.util.Arrays;

import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.util.VersionUtils;

/**
 * Created by gareth on 15/06/17.
 */

public enum VersionCompatability {

    INSTANCE;

    private int[] serverVersion = null;
    private boolean favoritesEnabled;
    private boolean supportedVersion;

    public boolean isFavoritesEnabled() {
        return favoritesEnabled;
    }

    public boolean isSupportedVersion() {
        return supportedVersion;
    }

    public void runTests() {
        serverVersion = getServerVersion();
        supportedVersion = versionExceedsMinimum(getMinimumTestedVersion());
        favoritesEnabled = isFavoritesSupported();
    }

    private boolean isFavoritesSupported() {
        boolean nativeSupport = versionExceedsMinimum(getMinimumVersionForFavorites());
        if(!nativeSupport) {
            nativeSupport = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile()).isPiwigoClientPluginInstalled();
        }
        return nativeSupport;
    }

    private int[] getMinimumVersionForFavorites() {
        //TODO add this in when it is known
        //return new int[]{2, 9, 5};
        return new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
    }

    private boolean versionExceedsMinimum(int[] minimumVersion) {
        return VersionUtils.versionExceeds(minimumVersion, serverVersion);
    }

    private int[] getMinimumTestedVersion() {

        if (PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) {
            return new int[]{2, 9, 1};
        } else {
            return new int[]{2, 9, 0};
        }
    }

    public String getMinimumTestedVersionString() {
        int[] minTestedVersion = getMinimumTestedVersion();
        return "" + minTestedVersion[0] + '.' + minTestedVersion[1] + '.' + minTestedVersion[2];
    }

    public String getServerVersionString() {
        return PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile()).getPiwigoVersion();
    }

    private int[] getServerVersion() {
        if (serverVersion == null) {
            String serverVersionStr = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile()).getPiwigoVersion();
            if(serverVersionStr.equals(PiwigoSessionDetails.UNKNOWN_VERSION)) {
                int[] version = new int[3];
                Arrays.fill(version, 0);
                serverVersion = version;
            } else {
                serverVersion = VersionUtils.parseVersionString(serverVersionStr);
            }
        }
        return serverVersion;
    }
}
