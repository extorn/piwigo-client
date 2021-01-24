package delit.piwigoclient.ui.common;

import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.os.ConfigurationCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.preference.PreferenceManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.NoSubscriberEvent;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.ui.events.ShowMessageEvent;
import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.CustomToolbar;
import delit.libs.util.IOUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AppPreferences;
import delit.piwigoclient.database.AppSettingsViewModel;
import delit.piwigoclient.database.UriPermissionUse;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.ui.FileSelectActivity;
import delit.piwigoclient.ui.events.PiwigoMethodNowUnavailableUsingFallback;
import delit.piwigoclient.ui.events.ServerConfigErrorEvent;
import delit.piwigoclient.ui.events.ServerConnectionWarningEvent;
import delit.piwigoclient.ui.events.StopActivityEvent;
import delit.piwigoclient.ui.events.UserNotUniqueWarningEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.TrackableRequestEvent;
import delit.piwigoclient.ui.file.FolderItem;

/**
 * Created by gareth on 26/05/17.
 */

public abstract class BaseMyActivity<T extends BaseMyActivity<T,UIH>,UIH extends ActivityUIHelper<UIH,T>> extends AppCompatActivity {

    private static final int OPEN_GOOGLE_PLAY_INTENT_REQUEST = 10102;
    private static final int FILE_SELECTION_INTENT_REQUEST = 10101;
    private static final String TAG = "MyActivity";
    private static final String STATE_TRACKED_ACTION_TO_INTENTS_MAP = "trackedActionIntentsMap";
    protected SharedPreferences prefs;
    private static int activitiesResumed = 0;
    private static boolean activitySwap;

    private HashMap<Long, Integer> trackedActionIntentsMap = new HashMap<>(3);
    private UIH uiHelper;
    private LicenceCheckingHelper licencingHelper;

    private String initialisedWithLanguage;
    private boolean isAttachedToWindow;

    public BaseMyActivity(@LayoutRes int contentView) {
        super(contentView);
    }

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

    protected BasicPiwigoResponseListener<UIH,T> buildPiwigoResponseListener() {
        return new BasicPiwigoResponseListener<>();
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(FileSelectionNeededEvent event) {
        Intent intent = new Intent(Intent.ACTION_VIEW, null, this, FileSelectActivity.class);
        intent.putExtra(FileSelectActivity.INTENT_DATA, event);
        setTrackedIntent(event.getActionId(), FILE_SELECTION_INTENT_REQUEST);
        startActivityForResult(intent, event.getActionId());
    }

    @Override
    public void onBackPressed() {
        doDefaultBackOperation();
    }

    protected void doDefaultBackOperation() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer != null && drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                // pop the current fragment off
                Logging.log(Log.INFO, TAG, "removing from activity - on default back button op");
                getSupportFragmentManager().popBackStack();
                // get the next fragment
                int i = getSupportFragmentManager().getBackStackEntryCount() - 2;
                // if there are no fragments left, do default back operation (i.e. close app)
                if (i < 0) {
                    super.onBackPressed();
                }
            } else {
                super.onBackPressed();
            }
        }
    }

    protected static void relayFileSelectionCompleteEvent(int sourceEventId, FileSelectionCompleteEvent event) {
        FileSelectionCompleteEvent evt;
        evt = new FileSelectionCompleteEvent(sourceEventId, event.getActionTimeMillis()).withFolderItems(event.getSelectedFolderItems());
        EventBus.getDefault().postSticky(evt);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        if (getTrackedIntentType(requestCode) == FILE_SELECTION_INTENT_REQUEST) {
            long actionTimeMillis = -1;
            if(data != null && data.hasExtra(FileSelectActivity.ACTION_TIME_MILLIS)) {
                actionTimeMillis = data.getLongExtra(FileSelectActivity.ACTION_TIME_MILLIS, -1);
            }
            if (resultCode == RESULT_OK && data.getExtras() != null) {
//                  int sourceEventId = data.getExtras().getInt(FileSelectActivity.INTENT_SOURCE_EVENT_ID);
                if(data.hasExtra(FileSelectActivity.INTENT_SELECTED_FILES)) {
                    ArrayList<FolderItem> filesForUpload = data.getParcelableArrayListExtra(FileSelectActivity.INTENT_SELECTED_FILES);
                    FileSelectionCompleteEvent event = new FileSelectionCompleteEvent(requestCode, actionTimeMillis).withFolderItems(filesForUpload);
                    // post sticky because the fragment to handle this event may not yet be created and registered with the event bus.
                    EventBus.getDefault().postSticky(event);
                } else {
                    // using the FileSelectionCompleteEvent posted by the FileSelectActivity to avoid TransactionTooLargeException.
                }
            } else {
                FileSelectionCompleteEvent event = new FileSelectionCompleteEvent(requestCode, actionTimeMillis);
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

    @NonNull
    @Override
    public LayoutInflater getLayoutInflater() {
        LayoutInflater inflator = super.getLayoutInflater();
        if(!(inflator.getContext() instanceof ContextThemeWrapper)) {
            inflator = inflator.cloneInContext(this);
        }
        return inflator;
    }

    protected DrawerLayout configureDrawer(CustomToolbar toolbar) {
        final DrawerLayout drawer = findViewById(R.id.drawer_layout);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ViewCompat.setOnApplyWindowInsetsListener(drawer, (v, insets) -> {
                if (!AppPreferences.isAlwaysShowStatusBar(prefs, v.getContext())) {
                    insets.replaceSystemWindowInsets(
                            insets.getStableInsetLeft(),
                            0,
                            insets.getStableInsetRight(),
                            0);
                    insets.consumeStableInsets();
                    //TODO forcing the top margin like this is really not a great idea. Find a better way.
                    ((FrameLayout.LayoutParams) v.getLayoutParams()).topMargin = 0;
                } else {
                    if (!AppPreferences.isAlwaysShowNavButtons(prefs, v.getContext())) {
                        int topMargin = ((FrameLayout.LayoutParams) v.getLayoutParams()).topMargin;
                        if (topMargin == 0) {
                            ((FrameLayout.LayoutParams) v.getLayoutParams()).topMargin = insets.getSystemWindowInsetTop();
                        }
                    }
                }
                return insets;
            });
        }

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close) {
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                ((DrawerNavigationView) drawerView).onDrawerOpened();
            }
        };
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        return drawer;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Obtain the FirebaseAnalytics instance.
//        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
//        setTheme(R.style.Theme_App);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ActivityManager.TaskDescription taskDesc;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                taskDesc = new ActivityManager.TaskDescription(
                        null, // Leave the default title.
                        R.mipmap.ic_launcher_round
                );
                setTaskDescription(taskDesc);
            } /*else {
                taskDesc = new ActivityManager.TaskDescription(null, null, ContextCompat.getColor(this, R.colors.primary));
            }*/

        }

        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        initialisedWithLanguage  = AppPreferences.getDesiredLanguage(getSharedPrefs(), this);

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
            Logging.log(Log.ERROR, TAG, "Unable to  create activity (cause : " + error + ")");
            Logging.recordException(e.getCause());
        }

        if (uiHelper == null) {
            uiHelper = (UIH)new ActivityUIHelper<UIH,T>((T)this, prefs, getWindow().getDecorView());
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

    @Override
    protected void onPostResume() {
        super.onPostResume();
        getUiHelper().showQueuedMsg(); // show any queued toast messages.
    }

    private void checkPrerequisites() {
        GoogleApiAvailability googleApi = GoogleApiAvailability.getInstance();
        int result = googleApi.isGooglePlayServicesAvailable(getApplicationContext());
        if (result != ConnectionResult.SUCCESS) {
            if(BuildConfig.DEBUG) {
                if(result == ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED) {
                    getUiHelper().showDetailedMsg(R.string.alert_warning, "Google Play services need updating. Version inadequate.");
                } else {
                    getUiHelper().showDetailedMsg(R.string.alert_warning, "Google Play services error : " + result);
                }
                return;
            }
            if (googleApi.isUserResolvableError(result)) {
                Dialog d = googleApi.getErrorDialog(this, result, OPEN_GOOGLE_PLAY_INTENT_REQUEST);
                d.setOnDismissListener(dialog -> {
                    finish();
                });
                d.show();
            } else {
                getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.unsupported_device), new ExitOnCloseAction<UIH,T>(getUiHelper()));
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
                HashSet<UriPermissionUse> missingPermissions = new HashSet<>();
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


    protected void createAndShowDialogWithExitOnClose(int titleId, int messageId) {

        final int trackingRequestId = TrackableRequestEvent.getNextEventId();
        getUiHelper().setTrackingRequest(trackingRequestId);

        getUiHelper().showOrQueueDialogMessage(titleId, getString(messageId), new OnStopActivityAction<>(getUiHelper(), trackingRequestId));
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

    protected void onAppPaused() {}

    protected void onAppResumed() {}

    @Override
    public void onResume() {
        if (initialisedWithLanguage != null && !initialisedWithLanguage.equals(AppPreferences.getDesiredLanguage(getSharedPrefs(), this))) {
            recreate(); // this doesn't appear to be used ever though I'm unclear why. Maybe just the way I use fragments in this app (prevent stacking some on others).
        }
        activitiesResumed++;
        if (!activitySwap) {
            onAppResumed();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            String label = getString(R.string.app_name);
            Bitmap icon = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
            int color = DisplayUtils.getColor(this, R.attr.colorPrimary);
            ActivityManager.TaskDescription taskDesc = new ActivityManager.TaskDescription(label, icon, color);
            setTaskDescription(taskDesc);
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

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if(uiHelper != null) {
            uiHelper.onSaveInstanceState(outState);
        }
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
            Logging.log(Log.ERROR,TAG, String.format(Locale.getDefault(), "Failed to start activity for result : %1$s (requestCode %3$d - valid: %2$s)", intent.toString(), requestCode > -2 && requestCode <= Short.MAX_VALUE, requestCode));
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
            Logging.log(Log.ERROR,TAG, String.format(Locale.getDefault(), "Failed to start activity for result : %1$s (requestCode %3$d - valid: %2$s)", intent.toString(), requestCode > -2 && requestCode <= Short.MAX_VALUE, requestCode));
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
        isAttachedToWindow = false;
        uiHelper.deregisterFromActiveServiceCalls();
        uiHelper.closeAllDialogs();
        super.onDetachedFromWindow();
    }

    protected <B extends Fragment> boolean removeFragmentsFromHistory(Class<B> fragmentClass) {
        boolean found = false;
        int i = 0;
        int popToStateId = -1;
        while (!found && getSupportFragmentManager().getBackStackEntryCount() > i) {
            FragmentManager.BackStackEntry entry = getSupportFragmentManager().getBackStackEntryAt(i);
            if (fragmentClass.getName().equals(entry.getName())) {
                found = true;
                popToStateId = entry.getId();
            }
            i++;
        }
        if (found) {
            Logging.log(Log.INFO, TAG, "removing from activity immediately as intentional flush of stack");
            getSupportFragmentManager().popBackStackImmediate(popToStateId, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }
        return found;
    }

    protected <F extends Fragment> void showFragmentNow(F f) {
        showFragmentNow(f, false);
    }

    protected void showFragmentNow(Fragment f, boolean addDuplicatePreviousToBackstack) {

        Logging.log(Log.DEBUG, TAG, String.format("showing fragment: %1$s (%2$s)", f.getTag(), f.getClass().getName()));
        checkLicenceIfNeeded();

        DisplayUtils.hideKeyboardFrom(getApplicationContext(), getWindow());

        Fragment lastFragment = getSupportFragmentManager().findFragmentById(R.id.main_view);
        String lastFragmentName = "";
        if (lastFragment != null) {
            lastFragmentName = lastFragment.getTag();
        }

        if (!addDuplicatePreviousToBackstack && f.getClass().getName().equals(lastFragmentName)) {
            Logging.log(Log.INFO, TAG, "removing from activity immediately as want to show and dups not allowed");
            getSupportFragmentManager().popBackStackImmediate();
        }

        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        tx.addToBackStack(f.getClass().getName());
        tx.add(R.id.main_view, f, f.getClass().getName()); // don't use replace because that causes the view to be recreated each time from scratch
        tx.commit();

        Logging.log(Log.DEBUG, TAG, "replaced existing fragment with new: " + f.getClass().getName());
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

    public UIH getUiHelper() {
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

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        isAttachedToWindow = true;
    }

    @Subscribe
    public void onEvent(NoSubscriberEvent e) {
        Logging.log(Log.WARN, TAG, "No subscriber for event " + e.originalEvent.toString());
    }

    public boolean isAttachedToWindow() {
        return isAttachedToWindow;
    }

    private static class OnStopActivityAction<UIH extends ActivityUIHelper<UIH,T>, T extends BaseMyActivity<T,UIH>> extends UIHelper.QuestionResultAdapter<UIH,T> implements Parcelable {
        private final int trackingRequestId;

        public OnStopActivityAction(UIH uiHelper, int trackingRequestId) {
            super(uiHelper);
            this.trackingRequestId = trackingRequestId;
        }

        protected OnStopActivityAction(Parcel in) {
            super(in);
            trackingRequestId = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(trackingRequestId);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<OnStopActivityAction<?,?>> CREATOR = new Creator<OnStopActivityAction<?,?>>() {
            @Override
            public OnStopActivityAction<?,?> createFromParcel(Parcel in) {
                return new OnStopActivityAction<>(in);
            }

            @Override
            public OnStopActivityAction<?,?>[] newArray(int size) {
                return new OnStopActivityAction<?,?>[size];
            }
        };

        @Override
        public void onDismiss(AlertDialog dialog) {
            //exit the app.
            EventBus.getDefault().post(new StopActivityEvent(trackingRequestId));
        }
    }



    protected boolean hasNotAcceptedEula() {
        int agreedEulaVersion = prefs.getInt(getString(R.string.preference_agreed_eula_version_key), -1);
        int currentEulaVersion = getResources().getInteger(R.integer.eula_version);
        return agreedEulaVersion < currentEulaVersion;
    }

    private static class ExitOnCloseAction<UIH extends ActivityUIHelper<UIH,T>, T extends BaseMyActivity<T,UIH>> extends UIHelper.QuestionResultAdapter<UIH,T> implements Parcelable {

        public ExitOnCloseAction(UIH uiHelper) {
            super(uiHelper);
        }

        protected ExitOnCloseAction(Parcel in) {
            super(in);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<ExitOnCloseAction<?,?>> CREATOR = new Creator<ExitOnCloseAction<?,?>>() {
            @Override
            public ExitOnCloseAction<?,?> createFromParcel(Parcel in) {
                return new ExitOnCloseAction<>(in);
            }

            @Override
            public ExitOnCloseAction<?,?>[] newArray(int size) {
                return new ExitOnCloseAction[size];
            }
        };

        @Override
        public void onDismiss(AlertDialog dialog) {
            getParent().finish();
        }
    }
}
