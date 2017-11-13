package delit.piwigoclient.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

/**
 * Created by gareth on 06/07/17.
 */

public class ProjectUtils {
    public static int getVersionCode(Context c) {
        PackageInfo pInfo = null;
        try {
            pInfo = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException("Somehow the package in MyApplication cannot be found in the package...", e);
        }
        int verCode = pInfo.versionCode;
        return verCode;
    }

    public static String getVersionName(Context c) {
        PackageInfo pInfo = null;
        try {
            pInfo = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException("Somehow the package in MyApplication cannot be found in the package...", e);
        }
        return pInfo.versionName;
    }
}
