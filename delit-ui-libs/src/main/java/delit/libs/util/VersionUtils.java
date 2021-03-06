package delit.libs.util;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.StringTokenizer;

import delit.libs.core.util.Logging;

public class VersionUtils {
    private static final String TAG = "VersionUtils";

    public static @NonNull int[] parseVersionString(@Nullable String versionStr) {
        int[] version;
        if(versionStr == null || versionStr.isEmpty()) {
            version = new int[3];
            Arrays.fill(version, 0);
        } else {
            StringTokenizer st = new StringTokenizer(versionStr, ".");
            version = new int[st.countTokens()];
            int i = 0;
            while (st.hasMoreTokens()) {
                String token = st.nextToken();
                if(i == version.length-1) {
                    token = token.replaceAll("-.*$", "");
                }
                int versionNum = 0;
                try {
                    versionNum = Integer.parseInt(token);
                } catch(NumberFormatException e) {
                    Logging.log(Log.ERROR, TAG, "Non-parsable version part. Using 0. %1$s (%2$s)", versionStr, token);
                }
                if (i < version.length) {
                    version[i] = versionNum;
                }
                i++;
            }
        }
        return version;
    }

    public static boolean versionExceeds(@NonNull int[] minimumVersion, @NonNull int[] version) {
        for(int i = 0; i < Math.min(minimumVersion.length, version.length); i++) {
            if (version[i] > minimumVersion[i]) {
                return true;
            }
        }
        return version.length > minimumVersion.length;
    }

    public static boolean versionAtLeast(int[] minimumVersion, int[] version) {
        for(int i = 0; i < Math.min(minimumVersion.length, version.length); i++) {
            if (version[i] > minimumVersion[i]) {
                return true;
            }
        }
        return version.length >= minimumVersion.length;
    }
}
