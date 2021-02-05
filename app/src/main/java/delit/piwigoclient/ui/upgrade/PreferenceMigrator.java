package delit.piwigoclient.ui.upgrade;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.BoolRes;
import androidx.annotation.IntegerRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.ui.preferences.SecurePrefsUtil;

public abstract class PreferenceMigrator implements Comparable<PreferenceMigrator> {

    private static final String TAG = "PrefMigrator";
    private static int idGen = 0;
    private final int id;
    private final int prefsVersion;

    public PreferenceMigrator(int prefsVersion) {
        this.id = idGen++;
        this.prefsVersion = prefsVersion;
    }

    @Override
    public int compareTo(PreferenceMigrator o) {
        return Integer.compare(prefsVersion, o.prefsVersion);
    }

    public final void execute(Context context, SharedPreferences prefs, int currentPrefsVersion) {
        if (currentPrefsVersion < prefsVersion) {
            Logging.log(Log.DEBUG, TAG, "Upgrading app Preferences from " + currentPrefsVersion +" to " + prefsVersion);
            SharedPreferences.Editor editor = prefs.edit();
            upgradePreferences(context, prefs, editor);
            editor.putInt(context.getString(R.string.preference_app_prefs_version_key), prefsVersion);
            editor.commit(); // need to wait for it - make sure they're written to disk in order
            Logging.log(Log.DEBUG, TAG, "Upgraded app Preferences to " + prefsVersion);
        }
    }

    public String getLogTag() {
        return TAG + prefsVersion;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof PreferenceMigrator)) {
            return false;
        }
        return id == ((PreferenceMigrator) obj).id;
    }

    protected void encryptAndSaveValue(Context context, SharedPreferences prefs, SharedPreferences.Editor editor, int keyId, String defaultVal) {
        String key = context.getString(keyId);
        try {
            String currentVal = prefs.getString(key, defaultVal);
            if (currentVal != null && !currentVal.equals(defaultVal)) {
                String encryptedVal = SecurePrefsUtil.getInstance(context, BuildConfig.APPLICATION_ID).encryptValue(key, currentVal);
                editor.putString(key, encryptedVal);
            }
        } catch (RuntimeException e) {
            Logging.log(Log.ERROR, TAG, "Error handling preference with key %1$s", key);
            throw e;
        }
    }

    protected void copyStringSetPreferenceToConnectionSettingsProfiles(Context context, SharedPreferences prefs, SharedPreferences.Editor editor, @StringRes int prefId) {
        // move into all profiles
        String prefKey = context.getString(prefId);
        try {
            if (prefs.contains(prefKey)) {
                Set<String> value = prefs.getStringSet(prefKey, null); // default value is NEVER used
                if (value != null) {
                    value = new HashSet<>(value);
                }
                Set<String> connectionProfiles = ConnectionPreferences.getConnectionProfileList(prefs, context);
                Set<String> finalValue = value;
                for (String profileId : connectionProfiles) {

                    upgradeConnectionProfilePreference(context, profileId, prefId, actor -> {

                        if (!actor.isForActiveProfile()) { // because its already in the active profile.
                            actor.writeStringSet(editor, context, finalValue);
                        }
                    });
                }
            }
        } catch (RuntimeException e) {
            Logging.log(Log.ERROR, TAG, "Error handling preference with key %1$s", prefKey);
            throw e;
        }
    }

    protected void copyStringPreferenceToConnectionSettingsProfiles(Context context, SharedPreferences prefs, SharedPreferences.Editor editor, @StringRes int prefId) {
        // delete and move into all profiles
        String prefKey = context.getString(prefId);
        try {
            if(prefs.contains(prefKey)) {
                String value = prefs.getString(prefKey, null); // default value is NEVER used
                Set<String> connectionProfiles = ConnectionPreferences.getConnectionProfileList(prefs, context);
                for (String profileId : connectionProfiles) {
                    upgradeConnectionProfilePreference(context, profileId, prefId, actor -> {

                        if(!actor.isForActiveProfile()) { // because its already in the active profile.
                            actor.writeString(editor, context, value);
                        }
                    });
                }
            }
        } catch (RuntimeException e) {
            Logging.log(Log.ERROR, TAG, "Error handling preference with key %1$s", prefKey);
            throw e;
        }
    }

    public boolean rekeyBooleanPref(Context context, SharedPreferences prefs, SharedPreferences.Editor editor, String fromOldKey, @StringRes int toNewKey, @BoolRes int defaultValRes) {
        try {
            boolean defaultVal = context.getResources().getBoolean(defaultValRes);
            if (prefs.contains(fromOldKey)) {
                // this is the old generic preference, now split into two
                boolean val = prefs.getBoolean(fromOldKey, defaultVal);
                editor.remove(fromOldKey);
                if(val != defaultVal) {
                    editor.putBoolean(context.getString(toNewKey), val);
                }
                return true;
            }
            return false;
        } catch (RuntimeException e) {
            Logging.log(Log.ERROR, TAG, "Error handling preference with key %1$s -> %2$s", fromOldKey, toNewKey);
            throw e;
        }
    }

    public boolean rekeyIntPref(Context context, SharedPreferences prefs, SharedPreferences.Editor editor, String fromOldKey, @StringRes int toNewKey, @IntegerRes int defaultValRes) {
        try {
            int defaultVal = context.getResources().getInteger(defaultValRes);
            if (prefs.contains(fromOldKey)) {
                // this is the old generic preference, now split into two
                int val = prefs.getInt(fromOldKey, defaultVal);
                editor.remove(fromOldKey);
                if(val != defaultVal) {
                    editor.putInt(context.getString(toNewKey), val);
                }
                return true;
            }
            return false;
        } catch (RuntimeException e) {
            Logging.log(Log.ERROR, TAG, "Error handling preference with key %1$s -> %2$s", fromOldKey, toNewKey);
            throw e;
        }
    }

    public boolean rekeyStringPref(Context context, SharedPreferences prefs, SharedPreferences.Editor editor, String fromOldKey, @StringRes int toNewKey, @StringRes int defaultValRes) {
        try {
            String defaultVal = context.getString(defaultValRes);
            if (prefs.contains(fromOldKey)) {
                // this is the old generic preference, now split into two
                String val = prefs.getString(fromOldKey, defaultVal);
                editor.remove(fromOldKey);
                if(!Objects.equals(val,defaultVal)) {
                    editor.putString(context.getString(toNewKey), val);
                }
                return true;
            }
            return false;
        } catch (RuntimeException e) {
            Logging.log(Log.ERROR, TAG, "Error handling preference with key %1$s -> %2$s", fromOldKey, toNewKey);
            throw e;
        }
    }

    protected interface ConnectionPreferenceUpgradeAction {
        void upgrade(ConnectionPreferences.PreferenceActor actor);
    }

    protected void upgradeConnectionProfilePreference(Context context, String profileId, @StringRes int profileKey, ConnectionPreferenceUpgradeAction action) {
        ConnectionPreferences.PreferenceActor actor = ConnectionPreferences.getPreferenceActor(context, profileId, profileKey);
        action.upgrade(actor);
    }

    @Override
    public int hashCode() {
        return id;
    }

    protected abstract void upgradePreferences(Context context, SharedPreferences prefs, SharedPreferences.Editor editor);
}
