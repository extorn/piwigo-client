package delit.piwigoclient.model.piwigo;

import java.util.StringTokenizer;

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
        int[] serverVersion = getServerVersion();
        supportedVersion = versionExceedsMinimum(getMinimumTestedVersion());
        favoritesEnabled = versionExceedsMinimum(getMinimumVersionForFavorites());
    }

    private int[] getMinimumVersionForFavorites() {
        return new int[]{2, 9, 2};
    }

    private boolean versionExceedsMinimum(int[] minimumVersion) {
        int[] serverVersion = getServerVersion();

        if (serverVersion[0] < minimumVersion[0]) {
            return false;
        }
        if (serverVersion[0] == minimumVersion[0]) {
            if (serverVersion[1] < minimumVersion[1]) {
                return false;
            }
            return serverVersion[1] != minimumVersion[1]
                    || serverVersion[2] >= minimumVersion[2];
        }
        return true;
    }

    private int[] getMinimumTestedVersion() {

        if (PiwigoSessionDetails.isAdminUser()) {
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
        return PiwigoSessionDetails.getInstance().getPiwigoVersion();
    }

    private int[] getServerVersion() {
        if (serverVersion == null) {
            String serverVersionStr = PiwigoSessionDetails.getInstance().getPiwigoVersion();
            StringTokenizer st = new StringTokenizer(serverVersionStr, ".");
            int[] version = new int[3];
            int i = 0;
            while (st.hasMoreTokens()) {
                int versionNum = Integer.valueOf(st.nextToken());
                if (i < version.length) {
                    version[i] = versionNum;
                }
                i++;
            }
            serverVersion = version;
        }
        return serverVersion;
    }
}
