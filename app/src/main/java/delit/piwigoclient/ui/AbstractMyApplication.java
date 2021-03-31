package delit.piwigoclient.ui;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.multidex.MultiDexApplication;
import androidx.preference.PreferenceManager;

import com.google.android.gms.tasks.Task;
import com.google.android.play.core.missingsplits.MissingSplitsManagerFactory;
import com.google.firebase.installations.FirebaseInstallations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.util.ProjectUtils;
import delit.libs.util.Utils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AppPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.ui.upgrade.PreferenceMigrator;
import delit.piwigoclient.ui.upgrade.PreferenceMigrator226;
import delit.piwigoclient.ui.upgrade.PreferenceMigrator282;
import delit.piwigoclient.ui.upgrade.PreferenceMigrator348;
import delit.piwigoclient.ui.upgrade.PreferenceMigrator371;
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

    private SharedPreferences prefs;

    protected List<PreferenceMigrator> getPreferenceMigrators() {
        List<PreferenceMigrator> migrators = new ArrayList<>();

        migrators.add(new PreferenceMigrator44());
        migrators.add(new PreferenceMigrator226());
        migrators.add(new PreferenceMigrator282());
        migrators.add(new PreferenceMigrator348());
        migrators.add(new PreferenceMigrator371());
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

    protected String getDesiredLanguage(Context context) {
        return AppPreferences.getDesiredLanguage(getPrefs(context), context);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Logging.log(Log.DEBUG, TAG, "App Configuration change %1$s", Utils.getId(this));

//        Locale.setDefault(newConfig.locale);
        getBaseContext().getResources().updateConfiguration(newConfig, getResources().getDisplayMetrics());
    }

    private void upgradeAnyPreferencesIfRequired() {
        SharedPreferences prefs = getPrefs();
        List<PreferenceMigrator> migrators = getPreferenceMigrators();
        Collections.sort(migrators); // into incrementing version number order

        int currentPrefsVersion = prefs.getInt(getString(R.string.preference_app_prefs_version_key), -1);
        int latestAppVersion = ProjectUtils.getVersionCode(getApplicationContext());
        if (currentPrefsVersion < latestAppVersion) {
            //TODO show message - migrating settings to latest app version progress bar ideally (maybe impossible at this stage of the app startup).
        }
        for (PreferenceMigrator migrator : migrators) {
            migrator.execute(this, prefs, currentPrefsVersion);
        }
        if (currentPrefsVersion < latestAppVersion) {
            ConnectionPreferences.PreferenceActor actor = new ConnectionPreferences.PreferenceActor();
            actor.with(R.string.preference_app_prefs_version_key);
            actor.writeInt(prefs, this, latestAppVersion);
            Bundle bundle = new Bundle();
            bundle.putInt("from_version", currentPrefsVersion);
            bundle.putInt("to_version", latestAppVersion);
            Logging.logAnalyticEvent(this,"app_upgraded", bundle);
            Logging.log(Log.DEBUG, TAG, "Upgraded app Preferences from " + currentPrefsVersion +" to " + latestAppVersion + " and saved");
            DisplayUtils.postOnUiThread(() -> {
                Toast.makeText(this, R.string.migrated_settings_from_previous_version, Toast.LENGTH_LONG).show();
            });
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if(MissingSplitsManagerFactory.create(this).disableAppIfMissingRequiredSplits()) {
                // Skip app initialization.
                return;
            }
        }

        super.onCreate();

        PicassoFactory.initialise();
//        AdsManager.getInstance(this).updateShowAdvertsSetting(getApplicationContext());
        registerActivityLifecycleCallbacks(this);
        Logging.initialise(this, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE);
        Task<String> idTask = FirebaseInstallations.getInstance().getId(); //This is a globally unique id for the app installation instance.
        idTask.addOnSuccessListener(this::withInstallGuid);

        upgradeAnyPreferencesIfRequired();

        onAppCreate();

//        TooLargeTool.startLogging(this);
    }

    private void withInstallGuid(String userGuid) {
        //This is used so that I can identify the logs that pertain to a given user having issues they want me to look at.
        // It will be displayed in the app about screen.
        Logging.addUserGuid(this, userGuid);
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
