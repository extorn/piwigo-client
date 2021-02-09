package delit.piwigoclient.ui.upgrade;

import android.content.Context;
import android.content.SharedPreferences;

import delit.piwigoclient.R;

public class PreferenceMigrator44 extends PreferenceMigrator {
    public PreferenceMigrator44() {
        super(44);
    }

    @Override
    protected void upgradePreferences(Context context, SharedPreferences prefs, SharedPreferences.Editor editor) {
        encryptAndSaveValue(context, prefs, editor, R.string.preference_piwigo_server_username_key, null);
        encryptAndSaveValue(context, prefs, editor, R.string.preference_piwigo_server_password_key, null);
        encryptAndSaveValue(context, prefs, editor, R.string.preference_server_basic_auth_username_key, null);
        encryptAndSaveValue(context, prefs, editor, R.string.preference_server_basic_auth_password_key, null);
    }
}
