package delit.libs.util;

import android.content.SharedPreferences;

import androidx.preference.Preference;

public abstract class SharedPreferencesPreferenceChangedListener implements SharedPreferences.OnSharedPreferenceChangeListener {

    private final String preferenceKey;
    private final Preference preference;

    public SharedPreferencesPreferenceChangedListener(Preference preference) {
        this.preference = preference;
        this.preferenceKey = preference.getKey();
    }
    @Override
    public final void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(key.equals(preferenceKey)) {
            onMonitoredPreferenceChanged(sharedPreferences, preference, key);
        }
    }

    public abstract void onMonitoredPreferenceChanged(SharedPreferences sharedPreferences, Preference preference, String key);
}
