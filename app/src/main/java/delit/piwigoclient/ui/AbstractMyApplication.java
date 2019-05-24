package delit.piwigoclient.ui;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.multidex.MultiDexApplication;

import java.net.URI;
import java.util.Set;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.ui.common.util.SecurePrefsUtil;
import delit.piwigoclient.util.ProjectUtils;

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

    private void upgradeAnyPreferencesIfRequired() {
        SharedPreferences prefs = getPrefs();
        int prefsVersion = prefs.getInt(getString(R.string.preference_app_prefs_version_key), -1);
        if (prefsVersion == -1) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(getString(R.string.preference_app_prefs_version_key), ProjectUtils.getVersionCode(getApplicationContext()));
            editor.commit();
        } else if (prefsVersion <= 43) {
            SharedPreferences.Editor editor = prefs.edit();
            encryptAndSaveValue(prefs, editor, R.string.preference_piwigo_server_username_key, null);
            encryptAndSaveValue(prefs, editor, R.string.preference_piwigo_server_password_key, null);
            encryptAndSaveValue(prefs, editor, R.string.preference_server_basic_auth_username_key, null);
            encryptAndSaveValue(prefs, editor, R.string.preference_server_basic_auth_password_key, null);
            editor.putInt(getString(R.string.preference_app_prefs_version_key), ProjectUtils.getVersionCode(getApplicationContext()));
            editor.commit();
        } else if(prefsVersion <= 144) {
            // Fix any addresses with a space character.
            SharedPreferences.Editor editor = prefs.edit();
            String serverName = prefs.getString(getString(R.string.preference_piwigo_server_address_key), null);
            if(serverName != null) {
                try {
                    URI.create(serverName);
                } catch (IllegalArgumentException e) {
                    editor.putString(getString(R.string.preference_piwigo_server_address_key), serverName.replaceAll(" ", ""));
                }
            }
            editor.commit();
        } else if(prefsVersion < 147) {
            SharedPreferences.Editor editor = prefs.edit();
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
            for(String profile : connectionProfiles) {
                ConnectionPreferences.ProfilePreferences connPrefs = ConnectionPreferences.getPreferences(profile, getPrefs(), this);
                int currentTimeout = connPrefs.getServerConnectTimeout(prefs, getApplicationContext());
                if(currentTimeout >= 1000) {
                    currentTimeout = (int) Math.round(Math.ceil((double)currentTimeout / 1000));
                    editor.putInt(connPrefs.getKey(getApplicationContext(), R.string.preference_server_connection_timeout_secs_key), currentTimeout);
                }
            }
            editor.commit();
        }

        if (prefsVersion < ProjectUtils.getVersionCode(getApplicationContext())) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(getString(R.string.preference_app_prefs_version_key), ProjectUtils.getVersionCode(getApplicationContext()));
            editor.commit();
        }
    }

    private void encryptAndSaveValue(SharedPreferences prefs, SharedPreferences.Editor editor, int keyId, String defaultVal) {
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
        super.onCreate();

        //Fabric.with(this, new Crashlytics.Builder().core(new CrashlyticsCore.Builder().disabled(BuildConfig.DEBUG).build()).build());

        PicassoFactory.initialise();
        upgradeAnyPreferencesIfRequired();
        AdsManager.getInstance().updateShowAdvertsSetting(getApplicationContext());
        registerActivityLifecycleCallbacks(this);
        resources = getResources();
        onAppCreate();
//        TooLargeTool.startLogging(this);
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
