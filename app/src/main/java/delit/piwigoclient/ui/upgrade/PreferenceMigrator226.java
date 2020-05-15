package delit.piwigoclient.ui.upgrade;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.crashlytics.android.Crashlytics;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import delit.libs.util.CollectionUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;

public class PreferenceMigrator226 extends PreferenceMigrator {

    public PreferenceMigrator226() {
        super(226);
    }

    @Override
    protected void upgradePreferences(Context context, SharedPreferences prefs, SharedPreferences.Editor editor) {
        if (prefs.contains(context.getString(R.string.preference_piwigo_server_address_key))) {
            String serverName = prefs.getString(context.getString(R.string.preference_piwigo_server_address_key), null);
            if (serverName != null) {
                try {
                    URI.create(serverName);
                } catch (IllegalArgumentException e) {
                    editor.putString(context.getString(R.string.preference_piwigo_server_address_key), serverName.replaceAll(" ", ""));
                }
            }
        }
        if (prefs.contains(context.getString(R.string.preference_gallery_show_album_thumbnail_zoomed_key))) {
            editor.remove(context.getString(R.string.preference_gallery_show_album_thumbnail_zoomed_key));
            editor.remove(context.getString(R.string.preference_gallery_albums_preferredColumnsLandscape_key));
            editor.remove(context.getString(R.string.preference_gallery_albums_preferredColumnsPortrait_key));
            editor.remove(context.getString(R.string.preference_gallery_images_preferredColumnsLandscape_key));
            editor.remove(context.getString(R.string.preference_gallery_images_preferredColumnsPortrait_key));
            editor.remove(context.getString(R.string.preference_data_file_selector_preferredFolderColumnsLandscape_key));
            editor.remove(context.getString(R.string.preference_data_file_selector_preferredFolderColumnsPortrait_key));
            editor.remove(context.getString(R.string.preference_data_file_selector_preferredFileColumnsLandscape_key));
            editor.remove(context.getString(R.string.preference_data_file_selector_preferredFileColumnsPortrait_key));
        }
        Set<String> connectionProfiles = ConnectionPreferences.getConnectionProfileList(prefs, context);
        for (String profileId : connectionProfiles) {
            upgradeConnectionProfilePreference(context, profileId, R.string.preference_server_connection_timeout_secs_key, new ConnectionPreferenceUpgradeAction() {
                public void upgrade(ConnectionPreferences.ProfilePreferences.PreferenceActor actor) {
                    int currentTimeout = actor.readInt(prefs, context, -1);
                    if (currentTimeout >= 1000) {
                        currentTimeout = (int) Math.round(Math.ceil((double) currentTimeout / 1000));
                        actor.writeInt(editor, context, currentTimeout);
                    }
                }
            });
            upgradeConnectionProfilePreference(context, profileId, R.string.preference_piwigo_playable_media_extensions_key, new ConnectionPreferenceUpgradeAction() {
                public void upgrade(ConnectionPreferences.ProfilePreferences.PreferenceActor actor) {
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
                }
            });
        }
    }
}
