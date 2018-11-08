package delit.piwigoclient.ui.common.util;

import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import java.util.Set;

import delit.piwigoclient.ui.common.MyActivity;

/**
 * Created by gareth on 07/04/18.
 */

public class PreferenceUtils {
    public static void wipeAppPreferences(MyActivity activity) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());
        Set<String> allPreEntries = prefs.getAll().keySet();
        SharedPreferences.Editor editor = prefs.edit();
        for (String pref : allPreEntries) {
            editor.remove(pref);
        }
        editor.commit();
    }
}
