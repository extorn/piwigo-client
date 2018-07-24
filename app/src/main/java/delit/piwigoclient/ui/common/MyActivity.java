package delit.piwigoclient.ui.common;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.HashMap;

import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.ui.LoginFragment;
import delit.piwigoclient.ui.MyApplication;
import delit.piwigoclient.ui.events.UserNotUniqueWarningEvent;

/**
 * Created by gareth on 26/05/17.
 */

public abstract class MyActivity extends AppCompatActivity {

    private static final String STATE_TRACKED_ACTION_TO_INTENTS_MAP = "trackedActionIntentsMap";
    protected SharedPreferences prefs;

    private HashMap<Long,Integer> trackedActionIntentsMap = new HashMap<>(3);
    private UIHelper uiHelper;
    private LicenceCheckingHelper licencingHelper;

    public SharedPreferences getSharedPrefs() {
        return prefs;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if(uiHelper == null) {
            uiHelper = new AppCompatActivityUIHelper(this, prefs);
            BasicPiwigoResponseListener listener = buildPiwigoResponseListener();
            listener.withUiHelper(this, uiHelper);
            uiHelper.setPiwigoResponseListener(listener);
        }

        if (savedInstanceState != null) {
            uiHelper.onRestoreSavedInstanceState(savedInstanceState);
            trackedActionIntentsMap = (HashMap<Long, Integer>) savedInstanceState.getSerializable(STATE_TRACKED_ACTION_TO_INTENTS_MAP);
        }

        if(BuildConfig.PAID_VERSION && !BuildConfig.DEBUG) {
            licencingHelper = new LicenceCheckingHelper();
            licencingHelper.onCreate(this);
        }

        super.onCreate(savedInstanceState);
        uiHelper.registerToActiveServiceCalls();
    }

    protected BasicPiwigoResponseListener buildPiwigoResponseListener() {
        return new BasicPiwigoResponseListener();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(!EventBus.getDefault().isRegistered(this)) {
            throw new RuntimeException("Activity must register with event bus to ensure handling of UserNotUniqueWarningEvent");
        }
        uiHelper.handleAnyQueuedPiwigoMessages();
        uiHelper.showNextQueuedMessage();
    }

    protected boolean hasAgreedToEula() {
        int agreedEulaVersion = prefs.getInt(getString(R.string.preference_agreed_eula_version_key), -1);
        int currentEulaVersion = getResources().getInteger(R.integer.eula_version);
        return agreedEulaVersion >= currentEulaVersion;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(UserNotUniqueWarningEvent event) {
        getUiHelper().showOrQueueDialogMessage(R.string.alert_warning, getString(R.string.alert_warning_multiple_users_found_pattern, event.getOtherUsers().size(), event.getUserSelected().getUsername()));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        uiHelper.onSaveInstanceState(outState);
        outState.putSerializable(STATE_TRACKED_ACTION_TO_INTENTS_MAP, trackedActionIntentsMap);
        super.onSaveInstanceState(outState);
    }

    protected void setTrackedIntent(long trackedAction, int intentType) {
        trackedActionIntentsMap.put(trackedAction, intentType);
    }

    protected int getTrackedIntentType(long trackedAction) {
        Integer intentType = trackedActionIntentsMap.remove(trackedAction);
        if(intentType == null) {
            return -1;
        } else {
            return intentType;
        }
    }

    @Override
    public void onDetachedFromWindow() {
        uiHelper.deregisterFromActiveServiceCalls();
        uiHelper.closeAllDialogs();
        super.onDetachedFromWindow();
    }

    protected void addActiveServiceCall(long messageId) {
        uiHelper.addActiveServiceCall(R.string.talking_to_server_please_wait, messageId);
    }

    protected void removeFragmentsFromHistory(Class<? extends Fragment> fragmentClass, boolean includeMidSessionLogins) {
        boolean found = false;
        int i = 0;
        int popToStateId = -1;
        while(!found && getSupportFragmentManager().getBackStackEntryCount() > i) {
            FragmentManager.BackStackEntry entry = getSupportFragmentManager().getBackStackEntryAt(i);
            if(fragmentClass.getName().equals(entry.getName())) {
                found = true;
                popToStateId = entry.getId();
                if(i > 0) {
                    // if the previous item was a login action - force that off the stack too.
                    entry = getSupportFragmentManager().getBackStackEntryAt(i - 1);
                    if(LoginFragment.class.getName().equals(entry.getName())) {
                        i--;
                    }
                    popToStateId = entry.getId();
                }
            } else {
                i++;
            }
        }
        if(found) {
            getSupportFragmentManager().popBackStack(popToStateId, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }
    }

    protected void checkLicenceIfNeeded() {
        if(licencingHelper != null) {
            licencingHelper.doSilentCheck();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(licencingHelper != null) {
            licencingHelper.onDestroy();
        }
    }

    public UIHelper getUiHelper() {
        return uiHelper;
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

    public MyApplication getMyApplication() {
        return (MyApplication)getApplication();
    }
}
