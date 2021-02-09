package delit.piwigoclient.ui.upgrade;

import android.content.Context;
import android.content.SharedPreferences;

import delit.piwigoclient.R;

public class PreferenceMigrator282 extends PreferenceMigrator {

    public PreferenceMigrator282() {
        super(282);
    }

    @Override
    protected void upgradePreferences(Context context, SharedPreferences prefs, SharedPreferences.Editor editor) {
        copyStringPreferenceToConnectionSettingsProfiles(context, prefs, editor, R.string.preference_gallery_unique_id_default);
        editor.remove(context.getString(R.string.usage_hints_shown_list_key)); // force all hints to be shown once more.
    }
}
