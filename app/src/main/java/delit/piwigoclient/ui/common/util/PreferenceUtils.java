package delit.piwigoclient.ui.common.util;

import android.content.SharedPreferences;
import android.preference.Preference;
import android.preference.PreferenceManager;

import java.lang.reflect.Method;
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
        for(String pref : allPreEntries) {
            editor.remove(pref);
        }
        editor.commit();
    }

    public static void refreshDisplayedPreference(Preference preference) {
        Class iterClass = preference.getClass();
        while(iterClass != Object.class) {
            try {
                Method m = iterClass.getDeclaredMethod("onSetInitialValue", boolean.class, Object.class);
                m.setAccessible(true);
                m.invoke(preference, true, null);
            } catch (Exception e) { }
            iterClass = iterClass.getSuperclass();
        }
    }
}
