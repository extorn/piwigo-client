package delit.piwigoclient.ui;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import androidx.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.multidex.MultiDexApplication;

import com.crashlytics.android.Crashlytics;
import com.google.android.play.core.missingsplits.MissingSplitsManagerFactory;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.util.MediaScanner;
import delit.libs.ui.util.SecurePrefsUtil;
import delit.libs.util.CollectionUtils;
import delit.libs.util.IOUtils;
import delit.libs.util.ProjectUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.piwigoApi.upload.BasePiwigoUploadService;

/**
 * Created by gareth on 14/06/17.
 */

public abstract class AbstractMyApplication extends MultiDexApplication implements Application.ActivityLifecycleCallbacks {

    private static final String TAG = "AbsApp";

    static {
        // required for vector graphics support on older devices
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }

    protected transient static Resources resources;
    private transient SharedPreferences prefs;

    public static Resources getAppResources() {
        return resources;
    }

    private void upgradeConnectionProfilePreference(Context context, String profileId, @StringRes int profileKey, ConnectionPreferenceUpgradeAction action) {
        ConnectionPreferences.ProfilePreferences.PreferenceActor actor = ConnectionPreferences.getPreferenceActor(context, profileId, profileKey);
        action.upgrade(actor);
    }

    private interface ConnectionPreferenceUpgradeAction {
        void upgrade(ConnectionPreferences.ProfilePreferences.PreferenceActor actor);
    }

    protected List<PreferenceMigrator> getPreferenceMigrators() {
        List<PreferenceMigrator> migrators = new ArrayList<>();

        migrators.add(new PreferenceMigrator(44) {
            @Override
            protected void upgradePreferences(Context context, SharedPreferences prefs, SharedPreferences.Editor editor) {
                encryptAndSaveValue(prefs, editor, R.string.preference_piwigo_server_username_key, null);
                encryptAndSaveValue(prefs, editor, R.string.preference_piwigo_server_password_key, null);
                encryptAndSaveValue(prefs, editor, R.string.preference_server_basic_auth_username_key, null);
                encryptAndSaveValue(prefs, editor, R.string.preference_server_basic_auth_password_key, null);
            }
        });
        migrators.add(new PreferenceMigrator(226) {

            @Override
            protected void upgradePreferences(Context context, SharedPreferences prefs, SharedPreferences.Editor editor) {
                if (prefs.contains(getString(R.string.preference_piwigo_server_address_key))) {
                    String serverName = prefs.getString(getString(R.string.preference_piwigo_server_address_key), null);
                    if (serverName != null) {
                        try {
                            URI.create(serverName);
                        } catch (IllegalArgumentException e) {
                            editor.putString(getString(R.string.preference_piwigo_server_address_key), serverName.replaceAll(" ", ""));
                        }
                    }
                }
                if (prefs.contains(getString(R.string.preference_gallery_show_album_thumbnail_zoomed_key))) {
                    editor.remove(getString(R.string.preference_gallery_show_album_thumbnail_zoomed_key));
                    editor.remove(getString(R.string.preference_gallery_albums_preferredColumnsLandscape_key));
                    editor.remove(getString(R.string.preference_gallery_albums_preferredColumnsPortrait_key));
                    editor.remove(getString(R.string.preference_gallery_images_preferredColumnsLandscape_key));
                    editor.remove(getString(R.string.preference_gallery_images_preferredColumnsPortrait_key));
                    editor.remove(getString(R.string.preference_data_file_selector_preferredFolderColumnsLandscape_key));
                    editor.remove(getString(R.string.preference_data_file_selector_preferredFolderColumnsPortrait_key));
                    editor.remove(getString(R.string.preference_data_file_selector_preferredFileColumnsLandscape_key));
                    editor.remove(getString(R.string.preference_data_file_selector_preferredFileColumnsPortrait_key));
                }
                Set<String> connectionProfiles = ConnectionPreferences.getConnectionProfileList(prefs, getApplicationContext());
                for (String profileId : connectionProfiles) {
                    upgradeConnectionProfilePreference(context, profileId, R.string.preference_server_connection_timeout_secs_key, new ConnectionPreferenceUpgradeAction() {
                        public void upgrade(ConnectionPreferences.ProfilePreferences.PreferenceActor actor) {
                            int currentTimeout = actor.readInt(prefs, getApplicationContext(), -1);
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
                                Crashlytics.log(Log.DEBUG, TAG, "Upgraded media extensions preference from string to Set<String>");
                            } catch (ClassCastException e) {
                                // will occur if the user has previously migrated preferences at version 222!
                            }
                        }
                    });
                }
            }
        });
        migrators.add(new PreferenceMigrator(240) {

            @Override
            protected void upgradePreferences(Context context, SharedPreferences prefs, SharedPreferences.Editor editor) {
                Set<String> connectionProfiles = ConnectionPreferences.getConnectionProfileList(prefs, getApplicationContext());
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
                            Crashlytics.log(Log.DEBUG, TAG, "Upgraded media extensions preference from string to Set<String>");
                        } catch (ClassCastException e) {
                            // will occur if the user has previously migrated preferences at version 222!
                        }
                    });
                }
            }
        });
        migrators.add(new PreferenceMigrator(282) {

            @Override
            protected void upgradePreferences(Context context, SharedPreferences prefs, SharedPreferences.Editor editor) {
                copyStringPreferenceToConnectionSettingsProfiles(context, editor, R.string.preference_gallery_unique_id_default);
                copyStringSetPreferenceToConnectionSettingsProfiles(context, editor, R.string.preference_piwigo_playable_media_extensions_key);
                editor.remove(getString(R.string.usage_hints_shown_list_key)); // force all hints to be shown once more.
            }
        });
        return migrators;
    }

    protected void copyStringPreferenceToConnectionSettingsProfiles(Context context, SharedPreferences.Editor editor, @StringRes int prefId) {
        // delete and move into all profiles
        String prefKey = context.getString(prefId);
        if(prefs.contains(prefKey)) {
            String value = prefs.getString(prefKey, null); // default value is NEVER used
            Set<String> connectionProfiles = ConnectionPreferences.getConnectionProfileList(prefs, getApplicationContext());
            for (String profileId : connectionProfiles) {
                upgradeConnectionProfilePreference(context, profileId, prefId, actor -> {

                    if(!actor.isForActiveProfile()) { // because its already in the active profile.
                        actor.writeString(editor, context, value);
                    }
                });
            }
        }
    }

    protected void copyStringSetPreferenceToConnectionSettingsProfiles(Context context, SharedPreferences.Editor editor, @StringRes int prefId) {
        // move into all profiles
        String prefKey = context.getString(prefId);
        if(prefs.contains(prefKey)) {
            Set<String> value = prefs.getStringSet(prefKey, null); // default value is NEVER used
            Set<String> connectionProfiles = ConnectionPreferences.getConnectionProfileList(prefs, getApplicationContext());
            for (String profileId : connectionProfiles) {
                upgradeConnectionProfilePreference(context, profileId, prefId, actor -> {

                    if(!actor.isForActiveProfile()) { // because its already in the active profile.
                        actor.writeStringSet(editor, context, value);
                    }
                });
            }
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(updateBaseContextLocale(base));
    }

    private Context updateBaseContextLocale(Context context) {
        String language = getDesiredLanguage(context); // Helper method to get saved language from SharedPreferences
        return DisplayUtils.updateContext(context, new Locale(language));
    }

    protected abstract String getDesiredLanguage(Context context);

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

//        Locale.setDefault(newConfig.locale);
        getBaseContext().getResources().updateConfiguration(newConfig, getResources().getDisplayMetrics());
    }

    private void upgradeAnyPreferencesIfRequired() {
        SharedPreferences prefs = getPrefs();
        List<PreferenceMigrator> migrators = getPreferenceMigrators();
        Collections.sort(migrators); // into incrementing version number order

        int currentPrefsVersion = prefs.getInt(getString(R.string.preference_app_prefs_version_key), -1);

        for (PreferenceMigrator migrator : migrators) {
            migrator.execute(this, prefs, currentPrefsVersion);
        }
        int latestAppVersion = ProjectUtils.getVersionCode(getApplicationContext());
        if (currentPrefsVersion < latestAppVersion) {
            SharedPreferences.Editor editor = prefs.edit();

            editor.putInt(getString(R.string.preference_app_prefs_version_key), latestAppVersion);
            editor.commit(); // ensure this is written to disk now.
            Bundle bundle = new Bundle();
            bundle.putInt("from_version", currentPrefsVersion);
            bundle.putInt("to_version", latestAppVersion);
            FirebaseAnalytics.getInstance(this).logEvent("app_upgraded", bundle);
            Crashlytics.log(Log.DEBUG, TAG, "Upgraded app Preferences from " + currentPrefsVersion +" to " + latestAppVersion + " and saved");
        }
    }

    protected static abstract class PreferenceMigrator implements Comparable<PreferenceMigrator> {

        private static int idGen = 0;
        private final int id;
        private final int prefsVersion;

        public PreferenceMigrator(int prefsVersion) {
            this.id = idGen++;
            this.prefsVersion = prefsVersion;
        }

        @Override
        public int compareTo(PreferenceMigrator o) {
            return (prefsVersion < o.prefsVersion) ? -1 : ((prefsVersion == o.prefsVersion) ? 0 : 1);
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

        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof PreferenceMigrator)) {
                return false;
            }
            return id == ((PreferenceMigrator) obj).id;
        }

        @Override
        public int hashCode() {
            return id;
        }

        protected abstract void upgradePreferences(Context context, SharedPreferences prefs, SharedPreferences.Editor editor);
    }

    protected void encryptAndSaveValue(SharedPreferences prefs, SharedPreferences.Editor editor, int keyId, String defaultVal) {
        String key = getString(keyId);
        String currentVal = prefs.getString(key, defaultVal);
        if (currentVal != null && !currentVal.equals(defaultVal)) {
            String encryptedVal = SecurePrefsUtil.getInstance(getApplicationContext()).encryptValue(key, currentVal);
            editor.putString(key, encryptedVal);
        }
    }

    protected SharedPreferences getPrefs(Context c) {
        if (prefs == null) {
            prefs = PreferenceManager.getDefaultSharedPreferences(c);
        }
        return prefs;
    }

    protected SharedPreferences getPrefs() {
        if (prefs == null) {
            prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        }
        return prefs;
    }


    @Override
    public final void onCreate() {
        if (MissingSplitsManagerFactory.create(this).disableAppIfMissingRequiredSplits()) {
            // Skip app initialization.
            return;
        }


        super.onCreate();
        // ensure it's available for any users of it
        resources = getResources();

        //Fabric.with(this, new Crashlytics.Builder().core(new CrashlyticsCore.Builder().disabled(BuildConfig.DEBUG).build()).build());
        MediaScanner.instance(getApplicationContext());
        PicassoFactory.initialise();

        upgradeAnyPreferencesIfRequired();
        sanityCheckTheTempUploadFolder();

        AdsManager.getInstance().updateShowAdvertsSetting(getApplicationContext());
        registerActivityLifecycleCallbacks(this);

        onAppCreate();
        FirebaseAnalytics.getInstance(this).setUserProperty("global_app_version", BuildConfig.VERSION_NAME);
        FirebaseAnalytics.getInstance(this).setUserProperty("global_app_version_code", "" + BuildConfig.VERSION_CODE);
//        TooLargeTool.startLogging(this);
    }

    private void sanityCheckTheTempUploadFolder() {
        File tmp_upload_folder = new File(getExternalCacheDir(), "piwigo-upload");
        long folderSizeBytes = IOUtils.getFolderSize(tmp_upload_folder, true);
        long folderMaxSizeBytes = 25 * 1024 * 1024;
        if (folderSizeBytes > folderMaxSizeBytes) {
            Bundle b = new Bundle();
            int activeUploadJobCount = BasePiwigoUploadService.getUploadJobsCount(this);
            b.putString("folder_size", IOUtils.toNormalizedText(folderSizeBytes));
            b.putInt("active_uploads", activeUploadJobCount);
            FirebaseAnalytics.getInstance(this).logEvent("tmp_upload_folder_size", b);
        }
    }


    @Override
    public void onTerminate() {
        MediaScanner.instance(getApplicationContext()).close();
        super.onTerminate();
    }

    protected abstract void onAppCreate();

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {

    }

    @Override
    public void onActivityResumed(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }

    @Override
    protected void finalize() throws Throwable {
        unregisterActivityLifecycleCallbacks(this);
        super.finalize();
    }

}
