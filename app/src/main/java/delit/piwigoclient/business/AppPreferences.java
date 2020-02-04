package delit.piwigoclient.business;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.core.os.ConfigurationCompat;

import delit.piwigoclient.R;

public class AppPreferences {
    public static boolean isAlwaysShowNavButtons(SharedPreferences prefs, Context context) {
        return prefs.getBoolean(context.getString(R.string.preference_app_always_show_nav_buttons_key), context.getResources().getBoolean(R.bool.preference_app_always_show_nav_buttons_default));
    }

    public static boolean isAlwaysShowStatusBar(SharedPreferences prefs, Context context) {
        return prefs.getBoolean(context.getString(R.string.preference_app_always_show_status_bar_key), context.getResources().getBoolean(R.bool.preference_app_always_show_status_bar_default));
    }

    public static String getDesiredLanguage(SharedPreferences prefs, Context context) {
        String val = prefs.getString(context.getString(R.string.preference_app_desired_language_key), null);
        if (val == null) {
            return ConfigurationCompat.getLocales(context.getResources().getConfiguration()).get(0).toString();
        }
        return val;
    }
}
