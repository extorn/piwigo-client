package delit.piwigoclient.ui;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatDelegate;

import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.PiwigoAccessService;
import delit.piwigoclient.ui.preferences.SecurePrefsUtil;
import delit.piwigoclient.util.DisplayUtils;
import delit.piwigoclient.util.ProjectUtils;
import paul.arian.fileselector.FileSelectionActivity;

/**
 * Created by gareth on 14/06/17.
 */

public class MyApplication extends Application implements Application.ActivityLifecycleCallbacks {

    static {
        // required for vector graphics support on older devices
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }

    private transient SharedPreferences prefs;
    private static MyApplication instance;
    private Activity currentActivity;

    public MyApplication() {
        instance = this;
    }

    public static MyApplication getInstance() {
        return instance;
    }

    public Activity getCurrentActivity() {
        return currentActivity;
    }

    private void upgradeAnyPreferencesIfRequired() {
        SharedPreferences prefs = getPrefs();
        int prefsVersion = prefs.getInt(getString(R.string.preference_app_prefs_version_key), -1);
        if(prefsVersion == -1) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(getString(R.string.preference_app_prefs_version_key), ProjectUtils.getVersionCode(getApplicationContext()));

            int currentValue = prefs.getInt(getString(R.string.preference_gallery_detail_sheet_offset_key), -1);
            if(currentValue >= 0) {
                currentValue = DisplayUtils.pxToDp(getApplicationContext(), currentValue);
                editor.putInt(getString(R.string.preference_gallery_detail_sheet_offset_key), currentValue);
            }
            editor.commit();
        } else if(prefsVersion <= 43) {
            SharedPreferences.Editor editor = prefs.edit();
            encryptAndSaveValue(prefs, editor, R.string.preference_piwigo_server_username_key, null);
            encryptAndSaveValue(prefs, editor, R.string.preference_piwigo_server_password_key, null);
            encryptAndSaveValue(prefs, editor, R.string.preference_server_basic_auth_username_key, null);
            encryptAndSaveValue(prefs, editor, R.string.preference_server_basic_auth_password_key, null);
            editor.putInt(getString(R.string.preference_app_prefs_version_key), ProjectUtils.getVersionCode(getApplicationContext()));
            editor.commit();
        }
    }

    private void encryptAndSaveValue(SharedPreferences prefs, SharedPreferences.Editor editor, int keyId, String defaultVal) {
        String key = getString(keyId);
        String currentVal = prefs.getString(key, defaultVal);
        if(currentVal != null && !currentVal.equals(defaultVal)) {
            String encryptedVal = SecurePrefsUtil.getInstance(getApplicationContext()).encryptValue(key, currentVal);
            editor.putString(key, encryptedVal);
        }
    }

    private SharedPreferences getPrefs() {
        if(prefs == null) {
            prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        }
        return prefs;
    }



    @Override
    public void onCreate() {
        super.onCreate();
        PicassoFactory.initialise(getApplicationContext());
        upgradeAnyPreferencesIfRequired();
        AdsManager.getInstance(getApplicationContext()).updateShowAdvertsSetting();
        registerActivityLifecycleCallbacks(this);
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        currentActivity = activity;
    }

    @Override
    public void onActivityStarted(Activity activity) {

    }

    @Override
    public void onActivityResumed(Activity activity) {
        currentActivity = activity;
        if (activity instanceof FileSelectionActivity) {
            AdsManager.getInstance(getApplicationContext()).showFileToUploadAdvertIfAppropriate();
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        if(activity == currentActivity) {
            currentActivity = null;
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
        PiwigoAccessService.startActionCleanupHttpConnections(getApplicationContext());
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
