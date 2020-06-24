package delit.piwigoclient.ui;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.documentfile.provider.DocumentFile;
import androidx.multidex.MultiDexApplication;
import androidx.preference.PreferenceManager;

import com.google.android.play.core.missingsplits.MissingSplitsManagerFactory;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.util.IOUtils;
import delit.libs.util.LegacyIOUtils;
import delit.libs.util.ProjectUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.upload.BasePiwigoUploadService;
import delit.piwigoclient.ui.upgrade.PreferenceMigrator;
import delit.piwigoclient.ui.upgrade.PreferenceMigrator226;
import delit.piwigoclient.ui.upgrade.PreferenceMigrator240;
import delit.piwigoclient.ui.upgrade.PreferenceMigrator282;
import delit.piwigoclient.ui.upgrade.PreferenceMigrator44;

/**
 * Created by gareth on 14/06/17.
 */

public abstract class AbstractMyApplication extends MultiDexApplication implements Application.ActivityLifecycleCallbacks {

    private static final String TAG = "AbsApp";

    static {
        // required for vector graphics support on older devices
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        Logging.setDebug(BuildConfig.DEBUG);
    }

    protected static Resources resources;
    private SharedPreferences prefs;

    public static Resources getAppResources() {
        return resources;
    }

    protected List<PreferenceMigrator> getPreferenceMigrators() {
        List<PreferenceMigrator> migrators = new ArrayList<>();

        migrators.add(new PreferenceMigrator44());
        migrators.add(new PreferenceMigrator226());
        migrators.add(new PreferenceMigrator240());
        migrators.add(new PreferenceMigrator282());
        return migrators;
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
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
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
            Logging.log(Log.DEBUG, TAG, "Upgraded app Preferences from " + currentPrefsVersion +" to " + latestAppVersion + " and saved");
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

        PicassoFactory.initialise();
//        AdsManager.getInstance(this).updateShowAdvertsSetting(getApplicationContext());
        registerActivityLifecycleCallbacks(this);
        FirebaseAnalytics.getInstance(this).setUserProperty("global_app_version", BuildConfig.VERSION_NAME);
        FirebaseAnalytics.getInstance(this).setUserProperty("global_app_version_code", "" + BuildConfig.VERSION_CODE);

        upgradeAnyPreferencesIfRequired();
        sanityCheckTheTempUploadFolder();

        onAppCreate();

//        TooLargeTool.startLogging(this);
    }

    private void sanityCheckTheTempUploadFolder() {
        long folderSizeBytes = 0;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            File tmpUploadFolder = BasePiwigoUploadService.getTmpUploadFolderAsFile(this);
            if(tmpUploadFolder != null) {
                folderSizeBytes = LegacyIOUtils.getFolderSize(tmpUploadFolder, true);
            }
        } else {
            DocumentFile tmpUploadFolder = BasePiwigoUploadService.getTmpUploadFolder(this);
            folderSizeBytes = IOUtils.getFolderSize(tmpUploadFolder, true);
        }

        long folderMaxSizeBytes = 25 * 1024 * 1024;
        if (folderSizeBytes > folderMaxSizeBytes) {
            Bundle b = new Bundle();
            int activeUploadJobCount = BasePiwigoUploadService.getUploadJobsCount(this);
            b.putString("folder_size", IOUtils.bytesToNormalizedText(folderSizeBytes));
            b.putInt("active_uploads", activeUploadJobCount);
            FirebaseAnalytics.getInstance(this).logEvent("tmp_upload_folder_size", b);
        }
    }

    protected abstract void onAppCreate();

    @Override
    public void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {

    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }

    @Override
    protected void finalize() throws Throwable {
        unregisterActivityLifecycleCallbacks(this);
        super.finalize();
    }

}
