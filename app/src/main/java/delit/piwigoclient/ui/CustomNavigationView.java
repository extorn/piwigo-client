package delit.piwigoclient.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcelable;
import androidx.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.crashlytics.android.Crashlytics;
import com.google.android.material.navigation.NavigationView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.util.SecurePrefsUtil;
import delit.libs.ui.view.PasswordInputToggle;
import delit.libs.ui.view.button.AppCompatCheckboxTriState;
import delit.libs.util.ProjectUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.HttpClientFactory;
import delit.piwigoclient.piwigoApi.HttpConnectionCleanup;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.LoginResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.LogoutResponseHandler;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.ViewGroupUIHelper;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.AppUnlockedEvent;
import delit.piwigoclient.ui.events.LockAppEvent;
import delit.piwigoclient.ui.events.NavigationItemSelectEvent;
import delit.piwigoclient.ui.events.PiwigoLoginSuccessEvent;
import delit.piwigoclient.ui.events.RewardUpdateEvent;
import delit.piwigoclient.ui.events.UnlockAppEvent;

/**
 * Created by gareth on 08/06/17.
 */

public class CustomNavigationView extends NavigationView implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "NavView";
    private SharedPreferences prefs;
    private ViewGroupUIHelper<CustomNavigationView> uiHelper;
    private ViewGroup headerView;
    private boolean refreshSessionInProgress;
    private TextView currentUsernameField;
    private TextView currentServerField;

    public CustomNavigationView(Context context) {
        this(context, null);
    }

    public CustomNavigationView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CustomNavigationView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        super.setNavigationItemSelectedListener(this);
    }

    @Override
    public View inflateHeaderView(@LayoutRes int res) {

        headerView = (ViewGroup) super.inflateHeaderView(res);

        String appVersion;
        if (isInEditMode()) {
            appVersion = "1.0.0";
        } else {
            appVersion = ProjectUtils.getVersionName(getContext());
        }

        ImageView appIcon = headerView.findViewById(R.id.app_icon);
        appIcon.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                synchronized (v) {
                    if (!refreshSessionInProgress) {
                        refreshSessionInProgress = true;
                        refreshPiwigoSession();
                    }
                }
            }
        });

        TextView appName = headerView.findViewById(R.id.app_name);
        if (BuildConfig.PAID_VERSION) {
            appName.setText(String.format(getResources().getString(R.string.app_paid_name_and_version_pattern), appVersion));
        } else {
            appName.setText(String.format(getResources().getString(R.string.app_name_and_version_pattern), appVersion));
        }

        final TextView email = headerView.findViewById(R.id.admin_email);

        email.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendEmail(((TextView) v).getText().toString());
            }
        });

        currentUsernameField = headerView.findViewById(R.id.current_user_name);
        currentServerField = headerView.findViewById(R.id.current_server);

        return headerView;
    }

    private void refreshPiwigoSession() {
        ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);
        if (sessionDetails != null && sessionDetails.isLoggedIn()) {
            uiHelper.invokeActiveServiceCall(String.format(getContext().getString(R.string.logging_out_of_piwigo_pattern), sessionDetails.getServerUrl()), new LogoutResponseHandler(), new OnLogoutAction());
        } else if (HttpClientFactory.getInstance(getContext()).isInitialised(connectionPrefs)) {
            runHttpClientCleanup(connectionPrefs);
        } else {
            new OnHttpConnectionsCleanedAction().onSuccess(uiHelper, null);
        }
    }

    private void runHttpClientCleanup(ConnectionPreferences.ProfilePreferences connectionPrefs) {
        HttpConnectionCleanup cleanup = new HttpConnectionCleanup(connectionPrefs, getContext());
        long msgId = cleanup.getMessageId();
        uiHelper.addActionOnResponse(msgId, new OnHttpConnectionsCleanedAction());
        uiHelper.addActiveServiceCall(getContext().getString(R.string.loading_new_server_configuration), msgId, "httpCleanup");
        cleanup.start();
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(RewardUpdateEvent event) {
        MenuItem item = getMenu().findItem(R.id.nav_buy_time);
        long millis = event.getRewardTimeRemaining();
        if (millis > 0) {
            long mins = millis / (60000);
            long secs = (millis - (mins * 60000)) / 1000;
            item.setTitle(getContext().getString(R.string.bought_time_menu_title_pattern, mins, secs));
            if (mins > 30) {
                item.setEnabled(false);
            }
        } else {
            item.setEnabled(true);
            item.setTitle(getContext().getString(R.string.buy_time_menu_title));
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(final UnlockAppEvent event) {
        String savedPassword = ConnectionPreferences.getActiveProfile().getPiwigoPasswordNotNull(prefs, getContext());
        if (savedPassword.equals(event.getPassword())) {
            lockAppInReadOnlyMode(false);
            uiHelper.showDetailedMsg(R.string.alert_success, getContext().getString(R.string.alert_app_unlocked_message));
            EventBus.getDefault().post(new AppUnlockedEvent());
        } else {
            // attempt login to PIWIGO server using this password.
            uiHelper.addActiveServiceCall(R.string.progress_checking_with_server, new LoginResponseHandler(event.getPassword()));
        }
    }

    private void markRefreshSessionComplete() {
        refreshSessionInProgress = false;
    }

    private void onLoginAfterAppUnlockEvent(PiwigoSessionDetails oldCredentials) {
        lockAppInReadOnlyMode(false);
        uiHelper.closeAllDialogs();
        uiHelper.showDetailedMsg(R.string.alert_success, getContext().getString(R.string.alert_app_unlocked_message));
        EventBus.getDefault().post(new AppUnlockedEvent());
    }

    private void sendEmail(String email) {

        final String appVersion = ProjectUtils.getVersionName(getContext());

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain"); // send email as plain text
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{email});
        intent.putExtra(Intent.EXTRA_SUBJECT, "PIWIGO Client");
        String serverVersion = "Unknown";
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        if (sessionDetails != null && sessionDetails.isLoggedInWithFullSessionDetails()) {
            serverVersion = sessionDetails.getPiwigoVersion();
        }
        intent.putExtra(Intent.EXTRA_TEXT, "Comments:\nFeature Request:\nBug Summary:\nBug Details:\nVersion of Piwigo Server Connected to: " + serverVersion + "\nVersion of PIWIGO Client: " + appVersion + "\nType and model of Device Being Used:\n");
        getContext().startActivity(Intent.createChooser(intent, ""));
    }

    @Override
    public void inflateMenu(int resId) {
        prefs = PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
        if (uiHelper == null) {
            if (!isInEditMode()) {
                // don't do this if showing in the IDE.
                uiHelper = new ViewGroupUIHelper(this, prefs, getContext());
                CustomPiwigoListener listener = new CustomPiwigoListener();
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
            case R.id.nav_lock:
                showLockDialog();
                break;
            case R.id.nav_unlock:
                showUnlockDialog();
                break;
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

    private void showUnlockDialog() {

        ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);
        String username;
        if (sessionDetails != null) {
            username = sessionDetails.getUsername();
        } else {
            SecurePrefsUtil prefUtil = SecurePrefsUtil.getInstance(getContext());
            username = connectionPrefs.getPiwigoUsername(prefs, getContext());
        }
        uiHelper.showOrQueueDialogQuestion(R.string.alert_title_unlock, getContext().getString(R.string.alert_message_unlock, username), R.layout.layout_password_entry, R.string.button_cancel, R.string.button_unlock, new OnUnlockAction(uiHelper));
    }

    public void onDrawerOpened() {
        uiHelper.showUserHint(TAG, 1, R.string.hint_navigation_panel_1);
    }

    private static class OnUnlockAction extends UIHelper.QuestionResultAdapter {
        public OnUnlockAction(UIHelper uiHelper) {
            super(uiHelper);
        }

        @Override
        public void onShow(AlertDialog alertDialog) {
            super.onShow(alertDialog);

            AppCompatCheckboxTriState viewUnencryptedToggle = alertDialog.findViewById(R.id.toggle_visibility);
            if (viewUnencryptedToggle != null) {
                EditText passwordField = alertDialog.findViewById(R.id.password);
                viewUnencryptedToggle.setOnCheckedChangeListener(new PasswordInputToggle(passwordField));
            }
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE == positiveAnswer) {
                DisplayUtils.hideKeyboardFrom(getContext(), dialog);
                EditText passwordEdit = dialog.findViewById(R.id.password);
                if(passwordEdit != null) {
                    String password = passwordEdit.getText().toString();
                    EventBus.getDefault().post(new UnlockAppEvent(password));
                } else {
                    Crashlytics.log("unable to find password field on dialog");
                }
            }
        }
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
    public void onEvent(final PiwigoLoginSuccessEvent event) {
        markRefreshSessionComplete();
        updateServerConnectionDetails();
    }

    private void updateServerConnectionDetails() {
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        if (sessionDetails != null) {
            currentServerField.setText(sessionDetails.getServerUrl());
            currentServerField.setVisibility(VISIBLE);
            currentUsernameField.setText(sessionDetails.getUsername());
            currentUsernameField.setVisibility(VISIBLE);
        } else {
            currentServerField.setVisibility(INVISIBLE);
            currentUsernameField.setVisibility(INVISIBLE);
        }
    }

    private void showLockDialog() {
        uiHelper.showOrQueueDialogQuestion(R.string.alert_title_lock, getContext().getString(R.string.alert_message_lock), R.string.button_cancel, R.string.button_lock, new OnAppLockAction(uiHelper));
    }

    private static class OnAppLockAction extends UIHelper.QuestionResultAdapter {
        public OnAppLockAction(UIHelper uiHelper) {
            super(uiHelper);
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE == positiveAnswer) {
                EventBus.getDefault().post(new LockAppEvent());
            }
        }
    }

    private static class OnLogoutAction extends UIHelper.Action<UIHelper<CustomNavigationView>, CustomNavigationView, LogoutResponseHandler.PiwigoOnLogoutResponse> {

        @Override
        public boolean onSuccess(UIHelper<CustomNavigationView> uiHelper, LogoutResponseHandler.PiwigoOnLogoutResponse response) {
            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
            uiHelper.getParent().runHttpClientCleanup(connectionPrefs);
            uiHelper.getParent().updateServerConnectionDetails();
            return false;
        }

        @Override
        public boolean onFailure(UIHelper<CustomNavigationView> uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
            PiwigoSessionDetails.logout(connectionPrefs, uiHelper.getContext());
            onSuccess(uiHelper, null);
            return false;
        }
    }

    private static class OnLoginAction extends UIHelper.Action<UIHelper<CustomNavigationView>, CustomNavigationView, LoginResponseHandler.PiwigoOnLoginResponse> {

        @Override
        public boolean onFailure(UIHelper<CustomNavigationView> uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
            uiHelper.getParent().markRefreshSessionComplete();
            return super.onFailure(uiHelper, response);
        }

        @Override
        public boolean onSuccess(UIHelper<CustomNavigationView> uiHelper, LoginResponseHandler.PiwigoOnLoginResponse response) {
            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
            uiHelper.getParent().markRefreshSessionComplete();
            if (PiwigoSessionDetails.isFullyLoggedIn(connectionPrefs)) {
                PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
                String msg = uiHelper.getContext().getString(R.string.alert_message_success_connectionTest, sessionDetails.getUserType());
                if (sessionDetails.getAvailableImageSizes().size() == 0) {
                    msg += '\n' + uiHelper.getContext().getString(R.string.alert_message_no_available_image_sizes);
                    uiHelper.showDetailedMsg(R.string.alert_title_login, msg);
                } else {
                    uiHelper.showDetailedMsg(R.string.alert_title_login, msg);
                }
                EventBus.getDefault().post(new PiwigoLoginSuccessEvent(response.getOldCredentials(), false));
            }
            return false;
        }
    }

    private static class OnHttpConnectionsCleanedAction extends UIHelper.Action<UIHelper<CustomNavigationView>, CustomNavigationView, HttpConnectionCleanup.HttpClientsShutdownResponse> {

        @Override
        public boolean onFailure(UIHelper<CustomNavigationView> uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
            uiHelper.getParent().markRefreshSessionComplete();
            return super.onFailure(uiHelper, response);
        }

        @Override
        public boolean onSuccess(UIHelper<CustomNavigationView> uiHelper, HttpConnectionCleanup.HttpClientsShutdownResponse response) {
            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
            String serverUri = connectionPrefs.getPiwigoServerAddress(uiHelper.getPrefs(), uiHelper.getContext());
            if ((serverUri == null || serverUri.trim().isEmpty())) {
                uiHelper.showOrQueueDialogMessage(R.string.alert_error, uiHelper.getContext().getString(R.string.alert_warning_no_server_url_specified));
                uiHelper.getParent().markRefreshSessionComplete();
            } else {
                uiHelper.invokeActiveServiceCall(String.format(uiHelper.getContext().getString(R.string.logging_in_to_piwigo_pattern), serverUri), new LoginResponseHandler(), new OnLoginAction());
            }
            return false; // don't run standard listener code
        }
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
            boolean isAdminUser = sessionDetails != null && sessionDetails.isAdminUser();
            boolean hasCommunityPlugin = sessionDetails != null && sessionDetails.isUseCommunityPlugin();
//            m.findItem(R.id.nav_gallery).setVisible(PiwigoSessionDetails.isLoggedInAndHaveSessionAndUserDetails());
            m.findItem(R.id.nav_upload).setVisible((isAdminUser || hasCommunityPlugin) && !isReadOnly);
            m.findItem(R.id.nav_groups).setVisible(isAdminUser && !isReadOnly);
            m.findItem(R.id.nav_users).setVisible(isAdminUser && !isReadOnly);

            m.findItem(R.id.nav_settings).setVisible(!isReadOnly);
            // only allow locking of the app if we've got an active login to PIWIGO.
            m.findItem(R.id.nav_lock).setVisible(!isReadOnly && sessionDetails != null && sessionDetails.isFullyLoggedIn() && !sessionDetails.isGuest());
            m.findItem(R.id.nav_unlock).setVisible(isReadOnly);

            m.findItem(R.id.nav_buy_time).setVisible(!BuildConfig.PAID_VERSION);

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

    private static class ConfigureNetworkAccessQuestionResult extends UIHelper.QuestionResultAdapter<ViewGroupUIHelper<CustomNavigationView>> {

        private final boolean networkAccessDesired;

        public ConfigureNetworkAccessQuestionResult(ViewGroupUIHelper<CustomNavigationView> uiHelper, boolean networkAccessDesired) {
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
                        PiwigoSessionDetails.logout(connectionPrefs, getUiHelper().getContext());
                        String serverUri = connectionPrefs.getPiwigoServerAddress(getUiHelper().getPrefs(), getUiHelper().getContext());
                        getUiHelper().invokeActiveServiceCall(String.format(getUiHelper().getContext().getString(R.string.logging_in_to_piwigo_pattern), serverUri), new LoginResponseHandler(), new OnLoginAction());
                    }
                    getUiHelper().getParent().setMenuVisibilityToMatchSessionState();
                } else if (!networkAccessDesired) {
                    String serverUri = connectionPrefs.getPiwigoServerAddress(getUiHelper().getPrefs(), getUiHelper().getContext());
                    getUiHelper().invokeActiveServiceCall(String.format(getUiHelper().getContext().getString(R.string.logging_in_to_piwigo_pattern), serverUri), new LoginResponseHandler().withCachedResponsesAllowed(true), new OnLoginAction());
                }
            }
        }
    }

    public void updateTheme() {
    }

    class CustomPiwigoListener extends BasicPiwigoResponseListener {
        @Override
        public void onBeforeHandlePiwigoResponseInListener(PiwigoResponseBufferingHandler.Response response) {
            // invokeAndWait the chained call before hiding the progress dialog to avoid flicker.
            if (response instanceof LoginResponseHandler.PiwigoOnLoginResponse) {
                LoginResponseHandler.PiwigoOnLoginResponse rsp = (LoginResponseHandler.PiwigoOnLoginResponse) response;
                if (PiwigoSessionDetails.isFullyLoggedIn(ConnectionPreferences.getActiveProfile())) {
                    onLoginAfterAppUnlockEvent(rsp.getOldCredentials());
                }
            }
        }
    }
}
