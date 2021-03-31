package delit.piwigoclient.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MenuItem;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.PasswordInputToggle;
import delit.libs.ui.view.button.MaterialCheckboxTriState;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.LoginResponseHandler;
import delit.piwigoclient.ui.common.DrawerNavigationView;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.ViewGroupUIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.AppUnlockedEvent;
import delit.piwigoclient.ui.events.LockAppEvent;
import delit.piwigoclient.ui.events.NavigationItemSelectEvent;
import delit.piwigoclient.ui.events.PiwigoLoginSuccessEvent;
import delit.piwigoclient.ui.events.UnlockAppEvent;

public class BaseActivityDrawerNavigationView<V extends BaseActivityDrawerNavigationView<V,VUIH>, VUIH extends ViewGroupUIHelper<VUIH,V>> extends NavigationView implements NavigationView.OnNavigationItemSelectedListener, DrawerNavigationView {


    private static final String TAG = "BaseActDrNavV";
    private SharedPreferences prefs;
    private VUIH uiHelper;

    public BaseActivityDrawerNavigationView(Context context) {
        this(context, null);
    }

    public BaseActivityDrawerNavigationView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BaseActivityDrawerNavigationView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        super.setNavigationItemSelectedListener(this);
    }

    protected ViewGroupUIHelper getUiHelper() {
        return uiHelper;
    }

    protected SharedPreferences getPrefs() {
        return prefs;
    }

    protected CustomPiwigoListener buildPiwigoListener() {
        return new CustomPiwigoListener();
    }

    public void setMenuVisibilityToMatchSessionState() {
        boolean isReadOnly = prefs.getBoolean(getContext().getString(R.string.preference_app_read_only_mode_key), false);
        setMenuVisibilityToMatchSessionState(isReadOnly);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        EventBus.getDefault().post(new NavigationItemSelectEvent(item.getItemId()));
        return true;
    }

    @Override
    public void onDrawerOpened() {
        setMenuVisibilityToMatchSessionState();
        uiHelper.showUserHint(TAG, 1, R.string.hint_navigation_panel_1);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(final LockAppEvent event) {
        lockAppInReadOnlyMode(true);
        EventBus.getDefault().post(new AppLockedEvent());
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(final PiwigoLoginSuccessEvent event) {
        setMenuVisibilityToMatchSessionState();
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

    protected void setMenuVisibilityToMatchSessionState(boolean isReadOnly) {
    }

    private void lockAppInReadOnlyMode(boolean lockApp) {
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putBoolean(getContext().getString(R.string.preference_app_read_only_mode_key), lockApp);
        prefsEditor.commit();
        setMenuVisibilityToMatchSessionState(lockApp);
    }

    protected void configureNetworkAccess(boolean accessAllowed) {
        String message;
        if (accessAllowed) {
            message = getContext().getString(R.string.alert_question_enable_network_access);
        } else {
            message = getContext().getString(R.string.alert_question_disable_network_access);
        }
        uiHelper.showOrQueueDialogQuestion(R.string.alert_question_title, message, R.string.button_no, R.string.button_yes, new ConfigureNetworkAccessQuestionResult<>(uiHelper, accessAllowed));
    }

    protected void showLockDialog() {
        uiHelper.showOrQueueDialogQuestion(R.string.alert_title_lock, getContext().getString(R.string.alert_message_lock), R.string.button_cancel, R.string.button_lock, new OnAppLockAction<>(uiHelper));
    }


    protected void showUnlockDialog() {

        ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);
        String username;
        if (sessionDetails != null) {
            username = sessionDetails.getUsername();
        } else {
            username = connectionPrefs.getPiwigoUsername(prefs, getContext());
        }
        uiHelper.showOrQueueDialogQuestion(R.string.alert_title_unlock, getContext().getString(R.string.alert_message_unlock, username), R.layout.layout_password_entry, R.string.button_cancel, R.string.button_unlock, new OnUnlockAction<>(uiHelper));
    }

    @Override
    public Parcelable onSaveInstanceState() {
        SavedState outstate = (SavedState) super.onSaveInstanceState();
        SavedState myState = new SavedState(outstate);
        myState.menuState = new Bundle();
        if(uiHelper != null) {
            uiHelper.onSaveInstanceState(myState.menuState);
        }
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable savedState) {
        createUiHelperIfNeeded();
        SavedState myState = (SavedState) savedState;
        uiHelper.onRestoreSavedInstanceState(myState.menuState);
        super.onRestoreInstanceState(((SavedState) savedState).getSuperState());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if(isInEditMode()) {
            // In Android studio preview
            return;
        }
        createUiHelperIfNeeded();

        setMenuVisibilityToMatchSessionState();
        uiHelper.registerToActiveServiceCalls();

        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
    }

    private void createUiHelperIfNeeded() {
        if (uiHelper == null) {
            prefs = PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
            // don't do this if showing in the IDE.
            uiHelper = (VUIH) new ViewGroupUIHelper(this, prefs, getContext());
            BasicPiwigoResponseListener listener = buildPiwigoListener();
            listener.withUiHelper(this, uiHelper);
            uiHelper.setPiwigoResponseListener(listener);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if(!isInEditMode()) {
            // NOT In Android studio preview
            if (EventBus.getDefault().isRegistered(this)) {
                EventBus.getDefault().unregister(this);
            }
            if(uiHelper != null) {
                uiHelper.deregisterFromActiveServiceCalls();
            }
        }
        super.onDetachedFromWindow();
    }

    protected void onLoginAfterAppUnlockEvent(PiwigoSessionDetails oldCredentials) {
        lockAppInReadOnlyMode(false);
        uiHelper.closeAllDialogs();
        uiHelper.showDetailedMsg(R.string.alert_success, getContext().getString(R.string.alert_app_unlocked_message));
        EventBus.getDefault().post(new AppUnlockedEvent());
    }

    private static class OnAppLockAction<V extends BaseActivityDrawerNavigationView<V,VUIH>, VUIH extends ViewGroupUIHelper<VUIH,V>> extends QuestionResultAdapter<VUIH,V> implements Parcelable {

        public OnAppLockAction(VUIH uiHelper) {
            super(uiHelper);
        }

        protected OnAppLockAction(Parcel in) {
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

        public static final Creator<OnAppLockAction<?,?>> CREATOR = new Creator<OnAppLockAction<?,?>>() {
            @Override
            public OnAppLockAction<?,?> createFromParcel(Parcel in) {
                return new OnAppLockAction<>(in);
            }

            @Override
            public OnAppLockAction<?,?>[] newArray(int size) {
                return new OnAppLockAction[size];
            }
        };

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE == positiveAnswer) {
                EventBus.getDefault().post(new LockAppEvent());
            }
        }
    }

    private static class ConfigureNetworkAccessQuestionResult<V extends BaseActivityDrawerNavigationView<V,VUIH>, VUIH extends ViewGroupUIHelper<VUIH,V>> extends QuestionResultAdapter<VUIH, V> implements Parcelable {

        private final boolean networkAccessDesired;

        public ConfigureNetworkAccessQuestionResult(VUIH uiHelper, boolean networkAccessDesired) {
            super(uiHelper);
            this.networkAccessDesired = networkAccessDesired;
        }

        protected ConfigureNetworkAccessQuestionResult(Parcel in) {
            super(in);
            networkAccessDesired = in.readByte() != 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeByte((byte) (networkAccessDesired ? 1 : 0));
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<ConfigureNetworkAccessQuestionResult> CREATOR = new Creator<ConfigureNetworkAccessQuestionResult>() {
            @Override
            public ConfigureNetworkAccessQuestionResult createFromParcel(Parcel in) {
                return new ConfigureNetworkAccessQuestionResult(in);
            }

            @Override
            public ConfigureNetworkAccessQuestionResult[] newArray(int size) {
                return new ConfigureNetworkAccessQuestionResult[size];
            }
        };

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
                        getUiHelper().invokeActiveServiceCall(String.format(getUiHelper().getAppContext().getString(R.string.logging_in_to_piwigo_pattern), serverUri), new LoginResponseHandler(), new OnLoginAction<>());
                    }
                    getUiHelper().getParent().setMenuVisibilityToMatchSessionState();
                } else if (!networkAccessDesired) {
                    String serverUri = connectionPrefs.getPiwigoServerAddress(getUiHelper().getPrefs(), getUiHelper().getAppContext());
                    getUiHelper().invokeActiveServiceCall(String.format(getUiHelper().getAppContext().getString(R.string.logging_in_to_piwigo_pattern), serverUri), new LoginResponseHandler().withCachedResponsesAllowed(true), new OnLoginAction<>());
                }
            }
        }
    }

    private static class OnLoginAction<V extends BaseActivityDrawerNavigationView<V,VUIH>, VUIH extends ViewGroupUIHelper<VUIH,V>> extends UIHelper.Action<VUIH, V, LoginResponseHandler.PiwigoOnLoginResponse> implements Parcelable {

        protected OnLoginAction(){}

        protected OnLoginAction(Parcel in) {
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

        public static final Creator<OnLoginAction<?,?>> CREATOR = new Creator<OnLoginAction<?,?>>() {
            @Override
            public OnLoginAction<?,?> createFromParcel(Parcel in) {
                return new OnLoginAction<>(in);
            }

            @Override
            public OnLoginAction<?,?>[] newArray(int size) {
                return new OnLoginAction[size];
            }
        };

        @Override
        public boolean onFailure(VUIH uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
//            uiHelper.getParent().markRefreshSessionComplete();
            return super.onFailure(uiHelper, response);
        }

        @Override
        public boolean onSuccess(VUIH uiHelper, LoginResponseHandler.PiwigoOnLoginResponse response) {
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

    private static class OnUnlockAction<V extends BaseActivityDrawerNavigationView<V,VUIH>, VUIH extends ViewGroupUIHelper<VUIH,V>> extends QuestionResultAdapter<VUIH,V> implements Parcelable {

        public OnUnlockAction(VUIH uiHelper) {
            super(uiHelper);
        }

        protected OnUnlockAction(Parcel in) {
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

        public static final Creator<OnUnlockAction<?,?>> CREATOR = new Creator<OnUnlockAction<?,?>>() {
            @Override
            public OnUnlockAction<?,?> createFromParcel(Parcel in) {
                return new OnUnlockAction<>(in);
            }

            @Override
            public OnUnlockAction<?,?>[] newArray(int size) {
                return new OnUnlockAction[size];
            }
        };

        @Override
        public void onShow(AlertDialog alertDialog) {
            super.onShow(alertDialog);

            MaterialCheckboxTriState viewUnencryptedToggle = alertDialog.findViewById(R.id.toggle_visibility);
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
                if (passwordEdit != null) {
                    String password = passwordEdit.getText().toString();
                    EventBus.getDefault().post(new UnlockAppEvent(password));
                } else {
                    Logging.log(Log.ERROR, TAG, "unable to find password field on dialog");
                }
            }
        }
    }

    protected static class CustomPiwigoListener<V extends BaseActivityDrawerNavigationView<V,VUIH>, VUIH extends ViewGroupUIHelper<VUIH,V>> extends BasicPiwigoResponseListener<VUIH,V> {
        @Override
        public void onBeforeHandlePiwigoResponseInListener(PiwigoResponseBufferingHandler.Response response) {
            // invokeAndWait the chained call before hiding the progress dialog to avoid flicker.
            if (response instanceof LoginResponseHandler.PiwigoOnLoginResponse) {
                LoginResponseHandler.PiwigoOnLoginResponse rsp = (LoginResponseHandler.PiwigoOnLoginResponse) response;
                if (PiwigoSessionDetails.isFullyLoggedIn(ConnectionPreferences.getActiveProfile())) {
                    getParent().onLoginAfterAppUnlockEvent(rsp.getOldCredentials());
                }
            }
        }
    }

}
