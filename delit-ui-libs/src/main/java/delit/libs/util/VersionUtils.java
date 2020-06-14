package delit.libs.util;

import java.util.Arrays;
import java.util.StringTokenizer;

public class VersionUtils {
    public static int[] parseVersionString(String versionStr) {
        int[] version = new int[3];
        if(versionStr == null || versionStr.isEmpty()) {
            Arrays.fill(version, 0);
        } else {
            StringTokenizer st = new StringTokenizer(versionStr, ".");
            int i = 0;
            while (st.hasMoreTokens()) {
                int versionNum = Integer.parseInt(st.nextToken());
                if (i < version.length) {
                    version[i] = versionNum;
                }
                i++;
            }
        }
        return version;
    }

    public static boolean versionExceeds(int[] minimumVersion, int[] version) {
        if (version[0] < minimumVersion[0]) {
            return false;
        }
        if (version[0] == minimumVersion[0]) {
            if (version[1] < minimumVersion[1]) {
                return false;
            }
            return version[1] != minimumVersion[1]
                    || version[2] >= minimumVersion[2];
        }
        return true;
    }
}
