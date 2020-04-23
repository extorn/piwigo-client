package delit.piwigoclient.ui.common;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.os.ConfigurationCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.crashlytics.android.Crashlytics;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.HashMap;
import java.util.Locale;

import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.util.DisplayUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AppPreferences;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.events.PiwigoMethodNowUnavailableUsingFallback;
import delit.piwigoclient.ui.events.ServerConfigErrorEvent;
import delit.piwigoclient.ui.events.ServerConnectionWarningEvent;
import delit.piwigoclient.ui.events.ShowMessageEvent;
import delit.piwigoclient.ui.events.UserNotUniqueWarningEvent;

/**
 * Created by gareth on 26/05/17.
 */

public abstract class MyActivity<T extends MyActivity<T>> extends AppCompatActivity {

    private static final String TAG = "MyActivity";
    protected static final long REWARD_COUNT_UPDATE_FREQUENCY = 1000;
    private static final String STATE_TRACKED_ACTION_TO_INTENTS_MAP = "trackedActionIntentsMap";
    protected SharedPreferences prefs;
    private static int activitiesResumed = 0;
    private static boolean activitySwap;

    private HashMap<Long, Integer> trackedActionIntentsMap = new HashMap<>(3);
    private ActivityUIHelper<T> uiHelper;
    private LicenceCheckingHelper licencingHelper;
    private AdsManager.RewardCountDownAction rewardsCountdownAction;
    private String initialisedWithLanguage;

//    private FirebaseAnalytics mFirebaseAnalytics;

    public SharedPreferences getSharedPrefs() {
        return prefs;
    }

    public SharedPreferences getSharedPrefs(Context c) {
        if (prefs == null) {
            prefs = PreferenceManager.getDefaultSharedPreferences(c.getApplicationContext());
        }
        return getSharedPrefs();
    }

    public static int getActivitiesResumedCount() {
        return activitiesResumed;
    }

    public LicenceCheckingHelper getLicencingHelper() {
        return licencingHelper;
    }

    protected BasicPiwigoResponseListener buildPiwigoResponseListener() {
        return new BasicPiwigoResponseListener();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull String name, @NonNull Context context, @NonNull AttributeSet attrs) {
        return super.onCreateView(name, context, attrs);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Obtain the FirebaseAnalytics instance.
//        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        rewardsCountdownAction = AdsManager.RewardCountDownAction.getInstance(getBaseContext(), REWARD_COUNT_UPDATE_FREQUENCY);
        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        initialisedWithLanguage = AppPreferences.getDesiredLanguage(getSharedPrefs(), this);

        if (uiHelper == null) {
            //TODO move this to after the view is created so that the UI helper progress indicator can init if needed!
            uiHelper = new ActivityUIHelper<>((T) this, prefs);
            BasicPiwigoResponseListener listener = buildPiwigoResponseListener();
            listener.withUiHelper(this, uiHelper);
            uiHelper.setPiwigoResponseListener(listener);
        }

        if (savedInstanceState != null) {
            uiHelper.onRestoreSavedInstanceState(savedInstanceState);
            trackedActionIntentsMap = BundleUtils.readMap(savedInstanceState, STATE_TRACKED_ACTION_TO_INTENTS_MAP, null);
        }

        if (BuildConfig.PAID_VERSION && !BuildConfig.DEBUG) {
            licencingHelper = new LicenceCheckingHelper();
            licencingHelper.onCreate(this);
        }

        try {
            super.onCreate(savedInstanceState);
        } catch (RuntimeException e) {
            String error = "Unknown";
            if (e.getCause() != null) {
                error = e.getCause().getMessage();
            }
            Crashlytics.log(Log.ERROR, TAG, "Unable to  create activity (cause : " + error + ")");
            Crashlytics.logException(e.getCause());
        }
        uiHelper.registerToActiveServiceCalls();
    }

    protected void onAppPaused() {
        if (rewardsCountdownAction != null) {
            rewardsCountdownAction.stop();
        }
    }

    protected void onAppResumed() {
        rewardsCountdownAction.start();
    }

    @Override
    protected void onPause() {
        activitiesResumed--;
        activitySwap = true;
        DisplayUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activitySwap = false;
                if (getActivitiesResumedCount() == 0) {
                    onAppPaused();
                }
            }
        }, 1000);
        uiHelper.deregisterFromActiveServiceCalls();
        super.onPause();
    }

    @Override
    public void onResume() {
        if (initialisedWithLanguage != null && !initialisedWithLanguage.equals(AppPreferences.getDesiredLanguage(getSharedPrefs(), this))) {
            recreate(); // this doesn't appear to be used ever though I'm unclear why. Maybe just the way I use fragments in this app (prevent stacking some on others).
        }
        activitiesResumed++;
        if (!activitySwap) {
            onAppResumed();
        }
        super.onResume();
        uiHelper.registerToActiveServiceCalls();
        if (!EventBus.getDefault().isRegistered(this)) {
            throw new RuntimeException("Activity must register with event bus to ensure handling of UserNotUniqueWarningEvent");
        }
        uiHelper.handleAnyQueuedPiwigoMessages();
        uiHelper.showNextQueuedMessage();
    }

    protected abstract String getDesiredLanguage(Context context);

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(updateBaseContextLocale(base));
        DisplayUtils.updateContext(this, ConfigurationCompat.getLocales(base.getResources().getConfiguration()).get(0));
    }

    //
    private Context updateBaseContextLocale(Context context) {
//        Locale currentLocale = ConfigurationCompat.getLocales(context.getResources().getConfiguration()).get(0);
        String language = getDesiredLanguage(context); // Helper method to get saved language from SharedPreferences
        Locale newLocale = new Locale(language);
        return DisplayUtils.updateContext(context, newLocale);
    }

    protected boolean hasAgreedToEula() {
        int agreedEulaVersion = prefs.getInt(getString(R.string.preference_agreed_eula_version_key), -1);
        int currentEulaVersion = getResources().getInteger(R.integer.eula_version);
        return agreedEulaVersion >= currentEulaVersion;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        uiHelper.onSaveInstanceState(outState);
        BundleUtils.writeMap(outState, STATE_TRACKED_ACTION_TO_INTENTS_MAP, trackedActionIntentsMap);
        super.onSaveInstanceState(outState);
    }

    protected void setTrackedIntent(long trackedAction, int intentType) {
        trackedActionIntentsMap.put(trackedAction, intentType);
    }

    protected int getTrackedIntentType(long trackedAction) {
        Integer intentType = trackedActionIntentsMap.remove(trackedAction);
        if (intentType == null) {
            return -1;
        } else {
            return intentType;
        }
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode, @Nullable Bundle options) {
        try {
            if (requestCode < -1) {
                // don't track this because we can't process a negative code anyway.
//                setTrackedIntent(Long.valueOf(requestCode), -1);
                requestCode = -1;
                // record the event
                recordInvalidRequestCodeEvent(intent, requestCode);
            }
            super.startActivityForResult(intent, requestCode, options);
        } catch(IllegalArgumentException e) {
            Crashlytics.log(String.format(Locale.getDefault(), "Failed to start activity for result : %1$s (requestCode %3$d - valid: %2$s)", intent.toString(), requestCode > -2 && requestCode <= Short.MAX_VALUE, requestCode));
            throw e;
        }
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        try {
            if (requestCode < -1) {
                // don't track this because we can't process a negative code anyway.
//                setTrackedIntent(Long.valueOf(requestCode), -1);
                requestCode = -1;
                // record the event
                recordInvalidRequestCodeEvent(intent, requestCode);
            }
            super.startActivityForResult(intent, requestCode & 0xFFFF);
        } catch(IllegalArgumentException e) {
            Crashlytics.log(String.format(Locale.getDefault(), "Failed to start activity for result : %1$s (requestCode %3$d - valid: %2$s)", intent.toString(), requestCode > -2 && requestCode <= Short.MAX_VALUE, requestCode));
            throw e;
        }
    }

    private void recordInvalidRequestCodeEvent(Intent intent, int requestCode) {
        Bundle bundle = new Bundle();
        bundle.putInt("requestCode", requestCode);
        bundle.putString("intent", intent.toString());
        bundle.putString("intentClass", intent.getClass().getName());
        FirebaseAnalytics.getInstance(this).logEvent("invalidRequestCode", bundle);
    }

    @Override
    public void onDetachedFromWindow() {
        uiHelper.deregisterFromActiveServiceCalls();
        uiHelper.closeAllDialogs();
        super.onDetachedFromWindow();
    }

    protected boolean removeFragmentsFromHistory(Class<? extends Fragment> fragmentClass, boolean includeMidSessionLogins) {
        boolean found = false;
        int i = 0;
        int popToStateId = -1;
        while (!found && getSupportFragmentManager().getBackStackEntryCount() > i) {
            FragmentManager.BackStackEntry entry = getSupportFragmentManager().getBackStackEntryAt(i);
            if (fragmentClass.getName().equals(entry.getName())) {
                found = true;
                popToStateId = entry.getId();
                if (i > 0) {
                    // if the previous item was a login action - force that off the stack too.
                    entry = getSupportFragmentManager().getBackStackEntryAt(i - 1);
                    popToStateId = entry.getId();
                }
            } else {
                i++;
            }
        }
        if (found) {
            getSupportFragmentManager().popBackStack(popToStateId, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }
        return found;
    }

    protected void checkLicenceIfNeeded() {
        if (licencingHelper != null) {
            licencingHelper.doSilentCheck();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (licencingHelper != null) {
            licencingHelper.onDestroy();
        }
    }

    public ActivityUIHelper<T> getUiHelper() {
        return uiHelper;
    }

    public Fragment getActiveFragment() {
        return getSupportFragmentManager().findFragmentById(R.id.main_view);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        uiHelper.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
    }

//    @Subscribe
//    public void onEvent(PermissionsWantedRequestEvent event) {
//        ActivityCompat.requestPermissions(this, event.getPermissionsNeeded(), event.getActionId());
//    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(UserNotUniqueWarningEvent event) {
        Bundle b = new Bundle();
        b.putInt("app_version", BuildConfig.VERSION_CODE);
        b.putString("app_version_name", BuildConfig.VERSION_NAME);
        FirebaseAnalytics.getInstance(getApplicationContext()).logEvent("usernameNotUniqueOnPiwigoServer", b);
        getUiHelper().showDetailedMsg(R.string.alert_warning, getString(R.string.alert_warning_multiple_users_found_pattern, event.getOtherUsers().size(), event.getUserSelected().getUsername()));
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(ServerConnectionWarningEvent event) {
        Bundle b = new Bundle();
        b.putInt("app_version", BuildConfig.VERSION_CODE);
        b.putString("app_version_name", BuildConfig.VERSION_NAME);
        FirebaseAnalytics.getInstance(getApplicationContext()).logEvent("serverKilledConnectionLots", b);
        getUiHelper().showDetailedMsg(R.string.alert_warning, event.getMessage());
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(ServerConfigErrorEvent event) {
        Bundle b = new Bundle();
        b.putInt("app_version", BuildConfig.VERSION_CODE);
        b.putString("app_version_name", BuildConfig.VERSION_NAME);
        FirebaseAnalytics.getInstance(getApplicationContext()).logEvent("serverCfgErrAdminMissingPerm", b);
        getUiHelper().showDetailedMsg(R.string.alert_warning, event.getMessage());
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(PiwigoMethodNowUnavailableUsingFallback event) {
        if(event.getFallbackMethod() != null) {
            getUiHelper().showDetailedMsg(R.string.alert_warning, getString(R.string.alert_msg_using_fallback_piwigo_method_pattern, event.getFailedOriginalMethod(), event.getFallbackMethod()), Toast.LENGTH_LONG);
        } else {
            getUiHelper().showDetailedMsg(R.string.alert_warning, getString(R.string.alert_msg_piwigo_method_not_available_pattern, event.getFailedOriginalMethod()));
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(ShowMessageEvent event) {
        getUiHelper().showOrQueueDialogMessage(event.getTitleResId(), event.getMessage());
    }
}
