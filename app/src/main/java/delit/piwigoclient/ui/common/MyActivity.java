package delit.piwigoclient.ui.common;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.HashMap;

import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.ui.MyApplication;
import delit.piwigoclient.ui.events.UserNotUniqueWarningEvent;

/**
 * Created by gareth on 26/05/17.
 */

public class MyActivity extends AppCompatActivity {

    private static final String STATE_TRACKED_ACTION_TO_INTENTS_MAP = "trackedActionIntentsMap";
    protected SharedPreferences prefs;

    private HashMap<Long,Integer> trackedActionIntentsMap = new HashMap<>(3);
    private UIHelper uiHelper;
    private LicenceCheckingHelper licencingHelper;


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

        if(BuildConfig.PAID_VERSION) {
            licencingHelper = new LicenceCheckingHelper();
            licencingHelper.onCreate(this);
        }

        super.onCreate(savedInstanceState);
        uiHelper.registerToActiveServiceCalls();
    }

    private BasicPiwigoResponseListener buildPiwigoResponseListener() {
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

    public boolean hasAgreedToEula() {
        int agreedEulaVersion = prefs.getInt(getString(R.string.preference_agreed_eula_version_key), -1);
        int currentEulaVersion = getResources().getInteger(R.integer.eula_version);
        boolean agreedToEula = agreedEulaVersion >= currentEulaVersion;
        return agreedToEula;
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
            return intentType.intValue();
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
