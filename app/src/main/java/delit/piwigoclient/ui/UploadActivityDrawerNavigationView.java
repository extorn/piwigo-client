package delit.piwigoclient.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.LoginResponseHandler;
import delit.piwigoclient.ui.common.DrawerNavigationView;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.ViewGroupUIHelper;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.LockAppEvent;
import delit.piwigoclient.ui.events.NavigationItemSelectEvent;
import delit.piwigoclient.ui.events.PiwigoLoginSuccessEvent;

/**
 * Created by gareth on 08/06/17.
 */

public class UploadActivityDrawerNavigationView extends NavigationView implements NavigationView.OnNavigationItemSelectedListener, DrawerNavigationView {

    private static final String TAG = "NavView";
    private SharedPreferences prefs;
    private ViewGroupUIHelper uiHelper;

    public UploadActivityDrawerNavigationView(Context context) {
        this(context, null);
    }

    public UploadActivityDrawerNavigationView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public UploadActivityDrawerNavigationView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        super.setNavigationItemSelectedListener(this);
    }

    @Override
    public void inflateMenu(int resId) {
        prefs = PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
        if (uiHelper == null) {
            if (!isInEditMode()) {
                // don't do this if showing in the IDE.
                uiHelper = new ViewGroupUIHelper<>(this, prefs, getContext());
                BasicPiwigoResponseListener listener = new BasicPiwigoResponseListener();
                listener.withUiHelper(this, uiHelper);
                uiHelper.setPiwigoResponseListener(listener);
            }
        }
        super.inflateMenu(resId);
        setMenuVisibilityToMatchSessionState();
        if (!isInEditMode()) {
            uiHelper.registerToActiveServiceCalls();
        }
        EventBus.getDefault().register(this);
    }

    public void setMenuVisibilityToMatchSessionState() {
        boolean isReadOnly = prefs.getBoolean(getContext().getString(R.string.preference_app_read_only_mode_key), false);
        setMenuVisibilityToMatchSessionState(isReadOnly);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.nav_online_mode:
                configureNetworkAccess(true);
                break;
            case R.id.nav_offline_mode:
                configureNetworkAccess(false);
                break;
            default:
                EventBus.getDefault().post(new NavigationItemSelectEvent(item.getItemId()));
        }
        return true;
    }

    @Override
    public Parcelable onSaveInstanceState() {
        EventBus.getDefault().unregister(this);
        uiHelper.deregisterFromActiveServiceCalls();
        SavedState outstate = (SavedState) super.onSaveInstanceState();
        SavedState myState = new SavedState(outstate);
        myState.menuState = new Bundle();
        uiHelper.onSaveInstanceState(myState.menuState);
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable savedState) {
        SavedState myState = (SavedState) savedState;
        uiHelper.onRestoreSavedInstanceState(myState.menuState);
        super.onRestoreInstanceState(((SavedState) savedState).getSuperState());
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(final LockAppEvent event) {
        lockAppInReadOnlyMode(true);
        EventBus.getDefault().post(new AppLockedEvent());
    }

    private void configureNetworkAccess(boolean accessAllowed) {
        String message;
        if (accessAllowed) {
            message = getContext().getString(R.string.alert_question_enable_network_access);
        } else {
            message = getContext().getString(R.string.alert_question_disable_network_access);
        }
        uiHelper.showOrQueueDialogQuestion(R.string.alert_question_title, message, R.string.button_no, R.string.button_yes, new ConfigureNetworkAccessQuestionResult(uiHelper, accessAllowed));
    }

    private void setMenuVisibilityToMatchSessionState(boolean isReadOnly) {
        Menu m = getMenu();
        if (m != null) {
            PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
//            boolean isAdminUser = sessionDetails != null && sessionDetails.isAdminUser();
//            m.findItem(R.id.nav_groups).setVisible(isAdminUser && !isReadOnly);
//            m.findItem(R.id.nav_users).setVisible(isAdminUser && !isReadOnly);
            m.findItem(R.id.nav_settings).setVisible(!isReadOnly);
            m.findItem(R.id.nav_offline_mode).setVisible(sessionDetails == null || !sessionDetails.isCached());
            m.findItem(R.id.nav_online_mode).setVisible(sessionDetails != null && sessionDetails.isCached());
        }
    }

    private void lockAppInReadOnlyMode(boolean lockApp) {
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putBoolean(getContext().getString(R.string.preference_app_read_only_mode_key), lockApp);
        prefsEditor.commit();
        setMenuVisibilityToMatchSessionState(lockApp);
    }

    @Override
    public void onDrawerOpened() {
        uiHelper.showUserHint(TAG, 1, R.string.hint_navigation_panel_1);
    }

    private static class OnLoginAction extends UIHelper.Action<UIHelper<UploadActivityDrawerNavigationView>, UploadActivityDrawerNavigationView, LoginResponseHandler.PiwigoOnLoginResponse> {

        private static final long serialVersionUID = -672337878074631707L;

        @Override
        public boolean onFailure(UIHelper<UploadActivityDrawerNavigationView> uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
//            uiHelper.getParent().markRefreshSessionComplete();
            return super.onFailure(uiHelper, response);
        }

        @Override
        public boolean onSuccess(UIHelper<UploadActivityDrawerNavigationView> uiHelper, LoginResponseHandler.PiwigoOnLoginResponse response) {
            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
//            uiHelper.getParent().markRefreshSessionComplete();
            if (PiwigoSessionDetails.isFullyLoggedIn(connectionPrefs)) {
                PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
                String msg = uiHelper.getAppContext().getString(R.string.alert_message_success_connectionTest, sessionDetails.getUserType());
                if (sessionDetails.getAvailableImageSizes().size() == 0) {
                    msg += '\n' + uiHelper.getAppContext().getString(R.string.alert_message_no_available_image_sizes);
                    uiHelper.showDetailedMsg(R.string.alert_title_login, msg);
                } else {
                    uiHelper.showDetailedMsg(R.string.alert_title_login, msg);
                }
                EventBus.getDefault().post(new PiwigoLoginSuccessEvent(response.getOldCredentials(), false));
            }
            return false;
        }
    }

    private static class ConfigureNetworkAccessQuestionResult extends UIHelper.QuestionResultAdapter<ViewGroupUIHelper<UploadActivityDrawerNavigationView>> {

        private static final long serialVersionUID = 2805625019197988481L;
        private final boolean networkAccessDesired;

        public ConfigureNetworkAccessQuestionResult(ViewGroupUIHelper<UploadActivityDrawerNavigationView> uiHelper, boolean networkAccessDesired) {
            super(uiHelper);
            this.networkAccessDesired = networkAccessDesired;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            super.onResult(dialog, positiveAnswer);
            if (Boolean.TRUE.equals(positiveAnswer)) {
                ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
                PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);
                if (sessionDetails != null) {
                    sessionDetails.setCached(!networkAccessDesired);
                    if (networkAccessDesired) {
                        PiwigoSessionDetails.logout(connectionPrefs, getUiHelper().getAppContext());
                        String serverUri = connectionPrefs.getPiwigoServerAddress(getUiHelper().getPrefs(), getUiHelper().getAppContext());
                        getUiHelper().invokeActiveServiceCall(String.format(getUiHelper().getAppContext().getString(R.string.logging_in_to_piwigo_pattern), serverUri), new LoginResponseHandler(), new OnLoginAction());
                    }
                    getUiHelper().getParent().setMenuVisibilityToMatchSessionState();
                } else if (!networkAccessDesired) {
                    String serverUri = connectionPrefs.getPiwigoServerAddress(getUiHelper().getPrefs(), getUiHelper().getAppContext());
                    getUiHelper().invokeActiveServiceCall(String.format(getUiHelper().getAppContext().getString(R.string.logging_in_to_piwigo_pattern), serverUri), new LoginResponseHandler().withCachedResponsesAllowed(true), new OnLoginAction());
                }
            }
        }
    }
}
