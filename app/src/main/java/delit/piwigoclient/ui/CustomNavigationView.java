package delit.piwigoclient.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.LoginResponseHandler;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.ViewGroupUIHelper;
import delit.piwigoclient.ui.common.util.SecurePrefsUtil;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.AppUnlockedEvent;
import delit.piwigoclient.ui.events.LockAppEvent;
import delit.piwigoclient.ui.events.NavigationItemSelectEvent;
import delit.piwigoclient.ui.events.UnlockAppEvent;
import delit.piwigoclient.util.ProjectUtils;

/**
 * Created by gareth on 08/06/17.
 */

public class CustomNavigationView extends NavigationView implements NavigationView.OnNavigationItemSelectedListener {

    private SharedPreferences prefs;
    private UIHelper uiHelper;
    private ViewGroup headerView;

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

        boolean useDarkMode = prefs.getBoolean(getResources().getString(R.string.preference_gallery_use_dark_mode_key), getResources().getBoolean(R.bool.preference_gallery_use_dark_mode_default));
        if (useDarkMode) {
            headerView.setBackgroundColor(Color.BLACK);
        }

        String appVersion;
        if (isInEditMode()) {
            appVersion = "1.0.0";
        } else {
            appVersion = ProjectUtils.getVersionName(getContext());
        }

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

        return headerView;
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
        if (item.getItemId() == R.id.nav_lock) {
            showLockDialog();
        } else if (item.getItemId() == R.id.nav_unlock) {
            showUnlockDialog();
        } else {
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
        uiHelper.showOrQueueDialogQuestion(R.string.alert_title_unlock, getContext().getString(R.string.alert_message_unlock, username), R.layout.password_entry_layout, R.string.button_cancel, R.string.button_unlock, new UIHelper.QuestionResultListener() {
            @Override
            public void onDismiss(AlertDialog dialog) {
            }

            @Override
            public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                if (Boolean.TRUE == positiveAnswer) {
                    EditText passwordEdit = dialog.findViewById(R.id.password);
                    String password = passwordEdit.getText().toString();
                    EventBus.getDefault().post(new UnlockAppEvent(password));
                }
            }
        });
    }

    protected void addActiveServiceCall(long messageId) {
        uiHelper.addActiveServiceCall(R.string.talking_to_server_please_wait, messageId);
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

    private void onLogin(PiwigoSessionDetails oldCredentials) {
        lockAppInReadOnlyMode(false);
        uiHelper.closeAllDialogs();
        uiHelper.showOrQueueDialogMessage(R.string.alert_success, getContext().getString(R.string.alert_app_unlocked_message));
        EventBus.getDefault().post(new AppUnlockedEvent());
    }

    private void showLockDialog() {
        uiHelper.showOrQueueDialogQuestion(R.string.alert_title_lock, getContext().getString(R.string.alert_message_lock), R.string.button_cancel, R.string.button_lock, new UIHelper.QuestionResultListener() {
            @Override
            public void onDismiss(AlertDialog dialog) {
            }

            @Override
            public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                if (Boolean.TRUE == positiveAnswer) {
                    EventBus.getDefault().post(new LockAppEvent());
                }
            }
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(final UnlockAppEvent event) {
        String savedPassword = ConnectionPreferences.getActiveProfile().getPiwigoPasswordNotNull(prefs, getContext());
        if (savedPassword.equals(event.getPassword())) {
            lockAppInReadOnlyMode(false);
            uiHelper.showOrQueueDialogMessage(R.string.alert_success, getContext().getString(R.string.alert_app_unlocked_message));
            EventBus.getDefault().post(new AppUnlockedEvent());
        } else {
            // attempt login to PIWIGO server using this password.
            uiHelper.addActiveServiceCall(R.string.progress_checking_with_server, new LoginResponseHandler(event.getPassword()).invokeAsync(getContext()));
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(final LockAppEvent event) {
        lockAppInReadOnlyMode(true);
        EventBus.getDefault().post(new AppLockedEvent());
    }

    private void lockAppInReadOnlyMode(boolean lockApp) {
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putBoolean(getContext().getString(R.string.preference_app_read_only_mode_key), lockApp);
        prefsEditor.commit();
        setMenuVisibilityToMatchSessionState(lockApp);
    }

    private void setMenuVisibilityToMatchSessionState(boolean isReadOnly) {
        Menu m = getMenu();
        if (m != null) {
            PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
            boolean isAdminUser = PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile());
            boolean hasCommunityPlugin = sessionDetails != null && sessionDetails.isUseCommunityPlugin();
//            m.findItem(R.id.nav_gallery).setVisible(PiwigoSessionDetails.isLoggedInAndHaveSessionAndUserDetails());
            m.findItem(R.id.nav_upload).setVisible((isAdminUser || hasCommunityPlugin) && !isReadOnly);
            m.findItem(R.id.nav_groups).setVisible(isAdminUser && !isReadOnly);
            m.findItem(R.id.nav_users).setVisible(isAdminUser && !isReadOnly);

            m.findItem(R.id.nav_settings).setVisible(!isReadOnly);
            // only allow locking of the app if we've got an active login to PIWIGO.
            m.findItem(R.id.nav_lock).setVisible(!isReadOnly && sessionDetails != null && sessionDetails.isFullyLoggedIn() && !sessionDetails.isGuest());
            m.findItem(R.id.nav_unlock).setVisible(isReadOnly);
        }
    }

    public void updateTheme() {
        boolean useDarkMode = prefs.getBoolean(getResources().getString(R.string.preference_gallery_use_dark_mode_key), getResources().getBoolean(R.bool.preference_gallery_use_dark_mode_default));
        if (useDarkMode) {
            headerView.setBackgroundColor(Color.BLACK);
        } else {
            headerView.setBackgroundResource(R.drawable.side_nav_bar);
        }
    }

    class CustomPiwigoListener extends BasicPiwigoResponseListener {
        @Override
        public void onBeforeHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            // invokeAndWait the chained call before hiding the progress dialog to avoid flicker.
            if (response instanceof LoginResponseHandler.PiwigoOnLoginResponse) {
                LoginResponseHandler.PiwigoOnLoginResponse rsp = (LoginResponseHandler.PiwigoOnLoginResponse) response;
                if (PiwigoSessionDetails.isFullyLoggedIn(ConnectionPreferences.getActiveProfile())) {
                    onLogin(rsp.getOldCredentials());
                }
            }
        }
    }
}
