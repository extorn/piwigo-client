package delit.piwigoclient.ui.upgrade;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.crashlytics.android.Crashlytics;

import java.util.HashSet;
import java.util.Set;

import delit.libs.util.CollectionUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.ui.AbstractMyApplication;

public class PreferenceMigrator240 extends PreferenceMigrator {

    public PreferenceMigrator240() {
        super(240);
    }

    @Override
    protected void upgradePreferences(Context context, SharedPreferences prefs, SharedPreferences.Editor editor) {
        Set<String> connectionProfiles = ConnectionPreferences.getConnectionProfileList(prefs, context);
        for (String profileId : connectionProfiles) {
            upgradeConnectionProfilePreference(context, profileId, R.string.preference_piwigo_playable_media_extensions_key, actor -> {
                try {
                    String multimediaCsvList = actor.readString(prefs, context, null);
                    HashSet<String> values = new HashSet<>(CollectionUtils.stringsFromCsvList(multimediaCsvList));
                    HashSet<String> cleanedValues = new HashSet<>(values.size());
                    for (String value : values) {
                        int dotIdx = value.indexOf('.');
                        if (dotIdx < 0) {
                            cleanedValues.add(value.toLowerCase());
                        } else {
                            cleanedValues.add(value.substring(dotIdx + 1).toLowerCase());
                        }
                    }
                    actor.remove(editor, context);
                    actor.writeStringSet(editor, context, cleanedValues);
                    Crashlytics.log(Log.DEBUG, getLogTag(), "Upgraded media extensions preference from string to Set<String>");
                } catch (ClassCastException e) {
                    // will occur if the user has previously migrated preferences at version 222!
                }
            });
        }
    }
}
