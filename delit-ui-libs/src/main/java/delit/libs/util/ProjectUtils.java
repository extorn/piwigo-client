package delit.libs.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import delit.libs.core.util.Logging;

/**
 * Created by gareth on 06/07/17.
 */

public class ProjectUtils {
    public static int getVersionCode(Context c) {
        PackageInfo pInfo;
        try {
            pInfo = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            Logging.recordException(e);
            throw new RuntimeException("Somehow the package in MyApplication cannot be found in the package...", e);
        }
        return pInfo.versionCode;
    }

    public static String getVersionName(Context c) {
        PackageInfo pInfo;
        try {
            pInfo = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            Logging.recordException(e);
            throw new RuntimeException("Somehow the package in MyApplication cannot be found in the package...", e);
        }
        return pInfo.versionName;
    }

    public static List<Locale> listLocalesWithUniqueTranslationOf(Context c, @StringRes int... stringRes) {
        return listLocalesWithUniqueTranslationOf(c, Locale.UK, stringRes);
    }

    public static List<Locale> listLocalesWithUniqueTranslationOf(Context c, Locale appDefaultLocale, @StringRes int... stringRes) {

        Configuration oldConfig = c.getResources().getConfiguration();
        Configuration config = new Configuration(oldConfig);
        config.setLocale(appDefaultLocale);

        Resources r = new Resources(c.getResources().getAssets(), c.getResources().getDisplayMetrics(), config);

        String[] defaultVals = new String[stringRes.length];
        for (int i = 0; i < defaultVals.length; i++) {
            defaultVals[i] = r.getString(stringRes[i]);
        }

        List<Locale> translations = new ArrayList<>();

        translations.add(appDefaultLocale);


        String[] locales = c.getResources().getAssets().getLocales();

        for (int i = 1; i < locales.length; i++) {
            String localeStr = locales[i];

            Locale locale = new Locale(localeStr);

            config.setLocale(locale);
            r.updateConfiguration(config, c.getResources().getDisplayMetrics());
            for (int j = 0; j < defaultVals.length; j++) {
                if (!defaultVals[j].equals(r.getString(stringRes[j]))) {
                    translations.add(locale);
                    break;
                }
            }
        }
        return translations;
    }

}
