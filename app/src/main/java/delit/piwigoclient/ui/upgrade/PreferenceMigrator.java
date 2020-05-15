package delit.piwigoclient.ui.upgrade;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.crashlytics.android.Crashlytics;

import java.util.Set;

import delit.libs.ui.util.SecurePrefsUtil;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;

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
            Crashlytics.log(Log.DEBUG, TAG, "Upgrading app Preferences from " + currentPrefsVersion +" to " + prefsVersion);
            SharedPreferences.Editor editor = prefs.edit();
            upgradePreferences(context, prefs, editor);
            editor.putInt(context.getString(R.string.preference_app_prefs_version_key), prefsVersion);
            editor.commit(); // need to wait for it - make sure they're written to disk in order
            Crashlytics.log(Log.DEBUG, TAG, "Upgraded app Preferences to " + prefsVersion);
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
        String currentVal = prefs.getString(key, defaultVal);
        if (currentVal != null && !currentVal.equals(defaultVal)) {
            String encryptedVal = SecurePrefsUtil.getInstance(context).encryptValue(key, currentVal);
            editor.putString(key, encryptedVal);
        }
    }

    protected void copyStringSetPreferenceToConnectionSettingsProfiles(Context context, SharedPreferences prefs, SharedPreferences.Editor editor, @StringRes int prefId) {
        // move into all profiles
        String prefKey = context.getString(prefId);
        if(prefs.contains(prefKey)) {
            Set<String> value = prefs.getStringSet(prefKey, null); // default value is NEVER used
            Set<String> connectionProfiles = ConnectionPreferences.getConnectionProfileList(prefs, context);
            for (String profileId : connectionProfiles) {
                upgradeConnectionProfilePreference(context, profileId, prefId, actor -> {

                    if(!actor.isForActiveProfile()) { // because its already in the active profile.
                        actor.writeStringSet(editor, context, value);
                    }
                });
            }
        }
    }

    protected void copyStringPreferenceToConnectionSettingsProfiles(Context context, SharedPreferences prefs, SharedPreferences.Editor editor, @StringRes int prefId) {
        // delete and move into all profiles
        String prefKey = context.getString(prefId);
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
    }

    protected interface ConnectionPreferenceUpgradeAction {
        void upgrade(ConnectionPreferences.ProfilePreferences.PreferenceActor actor);
    }

    protected void upgradeConnectionProfilePreference(Context context, String profileId, @StringRes int profileKey, ConnectionPreferenceUpgradeAction action) {
        ConnectionPreferences.ProfilePreferences.PreferenceActor actor = ConnectionPreferences.getPreferenceActor(context, profileId, profileKey);
        action.upgrade(actor);
    }

    @Override
    public int hashCode() {
        return id;
    }

    protected abstract void upgradePreferences(Context context, SharedPreferences prefs, SharedPreferences.Editor editor);
}
