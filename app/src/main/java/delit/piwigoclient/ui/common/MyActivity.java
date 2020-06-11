package delit.piwigoclient.ui.common;

import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.os.ConfigurationCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.preference.PreferenceManager;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.util.IOUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AppPreferences;
import delit.piwigoclient.database.AppSettingsViewModel;
import delit.piwigoclient.database.UriPermissionUse;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.FileSelectActivity;
import delit.piwigoclient.ui.events.PiwigoMethodNowUnavailableUsingFallback;
import delit.piwigoclient.ui.events.ServerConfigErrorEvent;
import delit.piwigoclient.ui.events.ServerConnectionWarningEvent;
import delit.piwigoclient.ui.events.ShowMessageEvent;
import delit.piwigoclient.ui.events.UserNotUniqueWarningEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionNeededEvent;
import delit.piwigoclient.ui.file.FolderItemRecyclerViewAdapter;

/**
 * Created by gareth on 26/05/17.
 */

public abstract class MyActivity<T extends MyActivity<T>> extends AppCompatActivity {

    private static final int OPEN_GOOGLE_PLAY_INTENT_REQUEST = 10102;
    private static final int FILE_SELECTION_INTENT_REQUEST = 10101;
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

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(FileSelectionNeededEvent event) {
        Intent intent = new Intent(Intent.ACTION_VIEW, null, this, FileSelectActivity.class);
        intent.putExtra(FileSelectActivity.INTENT_DATA, event);
        setTrackedIntent(event.getActionId(), FILE_SELECTION_INTENT_REQUEST);
        startActivityForResult(intent, event.getActionId());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (getTrackedIntentType(requestCode) == FILE_SELECTION_INTENT_REQUEST) {
            if (resultCode == RESULT_OK && data.getExtras() != null) {
//                int sourceEventId = data.getExtras().getInt(FileSelectActivity.INTENT_SOURCE_EVENT_ID);
                long actionTimeMillis = data.getLongExtra(FileSelectActivity.ACTION_TIME_MILLIS, -1);
                ArrayList<FolderItemRecyclerViewAdapter.FolderItem> filesForUpload = data.getParcelableArrayListExtra(FileSelectActivity.INTENT_SELECTED_FILES);
                FileSelectionCompleteEvent event = new FileSelectionCompleteEvent(requestCode, actionTimeMillis).withFolderItems(filesForUpload);
                // post sticky because the fragment to handle this event may not yet be created and registered with the event bus.
                EventBus.getDefault().postSticky(event);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull String name, @NonNull Context context, @NonNull AttributeSet attrs) {
        return super.onCreateView(name, context, attrs);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if(!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
    }

    @Override
    public void onStop() {
        if(EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
        super.onStop();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Obtain the FirebaseAnalytics instance.
//        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ActivityManager.TaskDescription taskDesc;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                taskDesc = new ActivityManager.TaskDescription(
                        null, // Leave the default title.
                        R.mipmap.ic_launcher_round_new
                );
                setTaskDescription(taskDesc);
            } /*else {
                taskDesc = new ActivityManager.TaskDescription(null, null, ContextCompat.getColor(this, R.color.primary));
            }*/

        }
        rewardsCountdownAction = AdsManager.RewardCountDownAction.getInstance(getBaseContext(), REWARD_COUNT_UPDATE_FREQUENCY);
        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        initialisedWithLanguage = AppPreferences.getDesiredLanguage(getSharedPrefs(), this);

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

        if (uiHelper == null) {
            //TODO move this to after the view is created so that the UI helper progress indicator can init if needed!
            uiHelper = new ActivityUIHelper<>((T)this, prefs);
            BasicPiwigoResponseListener listener = buildPiwigoResponseListener();
            listener.withUiHelper(this, uiHelper);
            uiHelper.setPiwigoResponseListener(listener);
        }
        if(savedInstanceState != null) {
            uiHelper.onRestoreSavedInstanceState(savedInstanceState);
            trackedActionIntentsMap = BundleUtils.readMap(savedInstanceState, STATE_TRACKED_ACTION_TO_INTENTS_MAP, null);
        }

        if(!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }

        uiHelper.registerToActiveServiceCalls();
        if (!EventBus.getDefault().isRegistered(this)) {
            throw new RuntimeException("Activity must register with event bus to ensure handling of UserNotUniqueWarningEvent");
        }
        uiHelper.handleAnyQueuedPiwigoMessages();
        uiHelper.showNextQueuedMessage();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            checkAllUriPermissionsStillPresent();
        }
        checkPrerequisites();
    }


    private void checkPrerequisites() {
        GoogleApiAvailability googleApi = GoogleApiAvailability.getInstance();
        int result = googleApi.isGooglePlayServicesAvailable(getApplicationContext());
        if (!BuildConfig.DEBUG && result != ConnectionResult.SUCCESS) {
            if (googleApi.isUserResolvableError(result)) {
                Dialog d = googleApi.getErrorDialog(this, result, OPEN_GOOGLE_PLAY_INTENT_REQUEST);
                d.setOnDismissListener(dialog -> {
                    if (!BuildConfig.DEBUG) {
                        finish();
                    }
                });
                d.show();
            } else {
                getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.unsupported_device), new UIHelper.QuestionResultAdapter<ActivityUIHelper<T>>(getUiHelper()) {
                    @Override
                    public void onDismiss(AlertDialog dialog) {
                        if (!BuildConfig.DEBUG) {
                            finish();
                        }
                    }
                });
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    protected void checkAllUriPermissionsStillPresent() {
        LifecycleOwner lifecycleOwner = DisplayUtils.getLifecycleOwner(this);
        ViewModelStoreOwner viewModelProvider = DisplayUtils.getViewModelStoreOwner(this);
        AppSettingsViewModel appSettingsViewModel = new ViewModelProvider(viewModelProvider).get(AppSettingsViewModel.class);
        LiveData<List<UriPermissionUse>> uriPermissionsData = appSettingsViewModel.getAll();
        uriPermissionsData.observe(lifecycleOwner, new Observer<List<UriPermissionUse>>() {
            @Override
            public void onChanged(List<UriPermissionUse> permissionsHeld) {
                uriPermissionsData.removeObserver(this);
                Set<Uri> heldPerms = new HashSet<>();
                for(UriPermission actualHeldPerm : getContentResolver().getPersistedUriPermissions()) {
                    heldPerms.add(actualHeldPerm.getUri());
                }
                HashSet<UriPermissionUse> missingPermissions = new HashSet<UriPermissionUse>();
                for(UriPermissionUse permWeNeed : permissionsHeld) {
                    if(!heldPerms.contains(Uri.parse(permWeNeed.uri))) {
                        missingPermissions.add(permWeNeed);
                        appSettingsViewModel.delete(permWeNeed);
                    }
                }
                if(!missingPermissions.isEmpty()) {
                    StringBuilder missingPermissionsCsvListSb = new StringBuilder();
                    for (Iterator<UriPermissionUse> iterator = missingPermissions.iterator(); iterator.hasNext(); ) {
                        UriPermissionUse perm = iterator.next();
                        missingPermissionsCsvListSb.append(perm.localizedConsumerName);
                        missingPermissionsCsvListSb.append(" : ");
                        missingPermissionsCsvListSb.append(IOUtils.getFilename(getApplicationContext(), Uri.parse(perm.uri)));
                        if(iterator.hasNext()) {
                            missingPermissionsCsvListSb.append('\n');
                        }
                    }
                    getUiHelper().showOrQueueDialogMessage(R.string.alert_warning, getString(R.string.missing_permissions_warning_message_pattern, missingPermissionsCsvListSb), R.string.button_ok);
                }
            }
        });
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
        super.onSaveInstanceState(outState);
        uiHelper.onSaveInstanceState(outState);
        BundleUtils.writeMap(outState, STATE_TRACKED_ACTION_TO_INTENTS_MAP, trackedActionIntentsMap);
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
