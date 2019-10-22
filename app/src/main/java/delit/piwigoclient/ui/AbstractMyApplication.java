package delit.piwigoclient.ui;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.multidex.MultiDexApplication;

import com.google.android.play.core.missingsplits.MissingSplitsManagerFactory;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import delit.libs.ui.util.MediaScanner;
import delit.libs.ui.util.SecurePrefsUtil;
import delit.libs.util.CollectionUtils;
import delit.libs.util.IOUtils;
import delit.libs.util.ProjectUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;

/**
 * Created by gareth on 14/06/17.
 */

public abstract class AbstractMyApplication extends MultiDexApplication implements Application.ActivityLifecycleCallbacks {

    static {
        // required for vector graphics support on older devices
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }

    protected transient static Resources resources;
    private transient SharedPreferences prefs;

    public static Resources getAppResources() {
        return resources;
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
                    Set<String> connectionProfiles = ConnectionPreferences.getConnectionProfileList(prefs, getApplicationContext());
                    for (String profile : connectionProfiles) {
                        ConnectionPreferences.ProfilePreferences connPrefs = ConnectionPreferences.getPreferences(profile, getPrefs(), context);
                        int currentTimeout = connPrefs.getServerConnectTimeout(prefs, getApplicationContext());
                        if (currentTimeout >= 1000) {
                            currentTimeout = (int) Math.round(Math.ceil((double) currentTimeout / 1000));
                            editor.putInt(connPrefs.getKey(getApplicationContext(), R.string.preference_server_connection_timeout_secs_key), currentTimeout);
                        }
                    }
                }

                String key = getString(R.string.preference_piwigo_playable_media_extensions_key);
                if (prefs.contains(key)) {
                    try {
                        String multimediaCsvList = prefs.getString(key, null);
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
                        editor.remove(key);
                        editor.putStringSet(key, cleanedValues);
                    } catch (ClassCastException e) {
                        // will occur if the user has previously migrated preferences at version 222!
                    }
                }
            }
        });
        migrators.add(new PreferenceMigrator(240) {

            @Override
            protected void upgradePreferences(Context context, SharedPreferences prefs, SharedPreferences.Editor editor) {
                String key = getString(R.string.preference_piwigo_playable_media_extensions_key);
                if (prefs.contains(key)) {
                    try {
                        String multimediaCsvList = prefs.getString(key, null);
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
                        editor.remove(key);
                        editor.putStringSet(key, cleanedValues);
                    } catch (ClassCastException e) {
                        // will occur if the user has previously migrated preferences at version 240!
                    }
                }
            }
        });
        return migrators;
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
                SharedPreferences.Editor editor = prefs.edit();
                upgradePreferences(context, prefs, editor);
                editor.putInt(context.getString(R.string.preference_app_prefs_version_key), prefsVersion);
                editor.commit(); // need to wait for it - make sure they're written to disk in order
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
        long folderSizeBytes = IOUtils.getFolderSize(tmp_upload_folder);
        long folderMaxSizeBytes = 100 * 1024 * 1024;
        if (folderSizeBytes > folderMaxSizeBytes) {
            Bundle b = new Bundle();
            b.putString("folder_size", IOUtils.toNormalizedText(folderSizeBytes));
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
