package delit.libs.ui.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import java.util.HashSet;
import java.util.Set;

import delit.libs.BuildConfig;

/**
 * Created by gareth on 07/04/18.
 */

public class PreferenceUtils {
    public static void wipeAppPreferences(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        Set<String> allPreEntries = prefs.getAll().keySet();
        SharedPreferences.Editor editor = prefs.edit();
        for (String pref : allPreEntries) {
            editor.remove(pref);
        }
        editor.commit();
    }

    public static Object getIntDefaultValue(Preference preference, TypedArray a, int index, int defaultVal) {
        Object defaultValue;
        int resourceId = a.getResourceId(index, -1);
        if (resourceId >= 0) {
            String resourceType = preference.getContext().getResources().getResourceTypeName(resourceId);
            if ("integer".equals(resourceType)) {
                defaultValue = a.getInteger(index, defaultVal);
            } else if ("string".equals(resourceType)) {
                defaultValue = a.getInteger(index, defaultVal);
            } else {
                if (BuildConfig.DEBUG) {
                    throw new RuntimeException("Unhandled preference default value resource type " + resourceType);
                } else {
                    Bundle b = new Bundle();
                    b.putString("error", "unexpected_resource_type");
                    b.putString("prefKey", preference.getKey());
                    b.putString("resType", resourceType);
                    Logging.logAnalyticEvent(preference.getContext(),"pref_init", b);
                    defaultValue = null;
                }
            }
        } else {
            defaultValue = a.getInteger(index, defaultVal);
        }
        return defaultValue;
    }

    public static Object getMultiTypeDefaultValue(Preference preference, TypedArray a, int index) {
        Object defaultValue;
        int resourceId = a.getResourceId(index, -1);
        if (resourceId >= 0) {
            String resourceType = preference.getContext().getResources().getResourceTypeName(resourceId);
            if ("string".equals(resourceType)) {
                defaultValue = a.getString(index);
            } else if ("array".equals(resourceType)) {
                CharSequence[] val = a.getTextArray(index);
                HashSet<String> defVal = new HashSet<>(val != null ? val.length : 0);
                for (CharSequence v : val) {
                    defVal.add(v.toString());
                }
                defaultValue = defVal;
            } else {
                if (BuildConfig.DEBUG) {
                    throw new RuntimeException("Unhandled preference default value resource type " + resourceType);
                } else {
                    Bundle b = new Bundle();
                    b.putString("error", "unexpected_resource_type");
                    b.putString("prefKey", preference.getKey());
                    b.putString("resType", resourceType);
                    Logging.logAnalyticEvent(preference.getContext(),"pref_init", b);
                    defaultValue = null;
                }
            }
        } else {
            defaultValue = a.getString(index);
        }
        return defaultValue;
    }
}
