package delit.piwigoclient.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.installations.FirebaseInstallations;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

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
import delit.piwigoclient.ui.events.PiwigoLoginSuccessEvent;

public class NavBarHeaderView<V extends NavBarHeaderView<V,VUIH>, VUIH extends ViewGroupUIHelper<VUIH,V>> extends FrameLayout {

    public static final String EMAIL_TEMPLATE_PATTERN = "Comments:\n" +
            "Feature Request:\n" +
            "Bug Summary:\n" +
            "Bug Details:\n" +
            "Version of Piwigo Server Connected to: %1$s\n" +
            "Version of PIWIGO Client: %2$s\n" +
            "Android version of this device: %3$s\n" +
            "App Install UUID:%4$s\n" +
            "Type and model of Device Being Used:\n";

    private boolean refreshSessionInProgress;
    private TextView currentUsernameField;
    private TextView currentServerField;
    private VUIH uiHelper;

    public NavBarHeaderView(Context context) {
        super(context);
        init(context, null, 0);
    }


    public NavBarHeaderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public NavBarHeaderView(Context context, AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    private void configureUiHelper() {
        createUiHelperIfNeeded();

        uiHelper.registerToActiveServiceCalls();

        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
    }

    private void createUiHelperIfNeeded() {
        if (uiHelper == null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
            // don't do this if showing in the IDE.
            uiHelper = (VUIH)new ViewGroupUIHelper<>((V)this, prefs, getContext());
            BasicPiwigoResponseListener<VUIH,V> listener = new BasicPiwigoResponseListener<>();
            listener.withUiHelper((V)this, uiHelper);
            uiHelper.setPiwigoResponseListener(listener);
        }
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr) {
//        TypedArray a = context.obtainStyledAttributes(
//                attrs, R.styleable.NavBarHeaderView, defStyleAttr, 0);
//        ViewCompat.setBackgroundTintList(this, a.getColorStateList(R.styleable.NavBarHeaderView_backgroundTint));
//        a.recycle();

        View content = inflate(context, R.layout.nav_header_main, null);
        addView(content);

        String appVersion;
        if (isInEditMode()) {
            appVersion = "1.0.0";
        } else {
            appVersion = ProjectUtils.getVersionName(getContext());
        }

        ImageView appIcon = content.findViewById(R.id.app_icon);
        appIcon.setOnClickListener(v -> {
            synchronized (v) {
                if (!refreshSessionInProgress) {
                    refreshSessionInProgress = true;
                    refreshPiwigoSession();
                }
            }
        });

        TextView appName = content.findViewById(R.id.app_name);
        if (BuildConfig.PAID_VERSION) {
            appName.setText(String.format(getResources().getString(R.string.app_paid_name_and_version_pattern), appVersion));
        } else {
            appName.setText(String.format(getResources().getString(R.string.app_name_and_version_pattern), appVersion));
        }

        final TextView email = content.findViewById(R.id.admin_email);

        email.setOnClickListener(v -> sendEmail(((TextView) v).getText().toString()));

        currentUsernameField = content.findViewById(R.id.current_user_name);
        currentServerField = content.findViewById(R.id.current_server);
        //TODO is this next line really needed?
        content.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.side_nav_bar));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        configureUiHelper();
        updateServerConnectionDetails();
    }

    @Override
    protected void onDetachedFromWindow() {
        if(uiHelper != null) {
            uiHelper.deregisterFromActiveServiceCalls();
        }
        EventBus.getDefault().unregister(this);
        super.onDetachedFromWindow();
    }

    @Override
    public Parcelable onSaveInstanceState() {
        BaseSavedState outState = (BaseSavedState) super.onSaveInstanceState();
        SavedState myState = new SavedState(outState);
        myState.uiHelperState = new Bundle();
        if(uiHelper != null) {
            uiHelper.onSaveInstanceState(myState.uiHelperState);
        }
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable savedState) {
        createUiHelperIfNeeded();
        SavedState myState = (SavedState) savedState;
        uiHelper.onRestoreSavedInstanceState(myState.uiHelperState);
        super.onRestoreInstanceState(((SavedState) savedState).getSuperState());
    }

    private void sendEmail(String emailToAddress) {
        Task<String> idTask = FirebaseInstallations.getInstance().getId(); //This is a globally unique id for the app installation instance.
        idTask.addOnCompleteListener(new EmailSender(emailToAddress));
    }

    private class EmailSender implements OnCompleteListener<String> {

        private final String toEmailAddress;

        public EmailSender(String toEmailAddress) {
            this.toEmailAddress = toEmailAddress;
        }

        @Override
        public void onComplete(@NonNull Task<String> uuidTask) {
            String uuid = null;
            if(uuidTask.isSuccessful()) {
                uuid = uuidTask.getResult();
            }
            final String appVersion = ProjectUtils.getVersionName(getContext());

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain"); // send email as plain text
            intent.putExtra(Intent.EXTRA_EMAIL, new String[]{toEmailAddress});
            intent.putExtra(Intent.EXTRA_SUBJECT, "PIWIGO Client");
            String serverVersion = "Unknown";
            PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
            if (sessionDetails != null && sessionDetails.isLoggedInWithFullSessionDetails()) {
                serverVersion = sessionDetails.getPiwigoVersion();
            }
            String emailContent = String.format(EMAIL_TEMPLATE_PATTERN, serverVersion, appVersion, Build.VERSION.CODENAME + "(" + Build.VERSION.SDK_INT + ")", uuid);
            intent.putExtra(Intent.EXTRA_TEXT, emailContent);
            getContext().startActivity(Intent.createChooser(intent, ""));
        }


    }

    protected void markRefreshSessionComplete() {
        refreshSessionInProgress = false;
    }

    protected void updateServerConnectionDetails() {
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

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(final PiwigoLoginSuccessEvent event) {
        markRefreshSessionComplete();
        updateServerConnectionDetails();
    }

    protected void runHttpClientCleanup(ConnectionPreferences.ProfilePreferences connectionPrefs) {
        HttpConnectionCleanup cleanup = new HttpConnectionCleanup(connectionPrefs, getContext());
        long msgId = cleanup.getMessageId();
        uiHelper.addActionOnResponse(msgId, new OnHttpConnectionsCleanedAction());
        uiHelper.addActiveServiceCall(getContext().getString(R.string.loading_new_server_configuration), msgId, "httpCleanup");
        cleanup.start();
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

    private static class SavedState extends BaseSavedState {
        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
        Bundle uiHelperState;

        SavedState(Parcelable superState) {
            super(superState);
        }

        SavedState(Parcel in) {
            super(in);
            uiHelperState = in.readBundle(getClass().getClassLoader());
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeBundle(uiHelperState);
        }
    }

    private static class OnLoginAction<V extends NavBarHeaderView<V,VUIH>, VUIH extends ViewGroupUIHelper<VUIH,V>> extends UIHelper.Action<VUIH,V, LoginResponseHandler.PiwigoOnLoginResponse> {

        @Override
        public boolean onFailure(VUIH uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
            uiHelper.getParent().markRefreshSessionComplete();
            return super.onFailure(uiHelper, response);
        }

        @Override
        public boolean onSuccess(VUIH uiHelper, LoginResponseHandler.PiwigoOnLoginResponse response) {
            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
            uiHelper.getParent().markRefreshSessionComplete();
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

    private static class OnHttpConnectionsCleanedAction<V extends NavBarHeaderView<V,VUIH>, VUIH extends ViewGroupUIHelper<VUIH,V>> extends UIHelper.Action<VUIH,V, HttpConnectionCleanup.HttpClientsShutdownResponse> {

        @Override
        public boolean onFailure(VUIH uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
            uiHelper.getParent().markRefreshSessionComplete();
            return super.onFailure(uiHelper, response);
        }

        @Override
        public boolean onSuccess(VUIH uiHelper, HttpConnectionCleanup.HttpClientsShutdownResponse response) {
            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
            String serverUri = connectionPrefs.getPiwigoServerAddress(uiHelper.getPrefs(), uiHelper.getAppContext());
            if ((serverUri == null || serverUri.trim().isEmpty())) {
                uiHelper.showOrQueueDialogMessage(R.string.alert_error, uiHelper.getAppContext().getString(R.string.alert_warning_no_server_url_specified));
                uiHelper.getParent().markRefreshSessionComplete();
            } else {
                uiHelper.invokeActiveServiceCall(String.format(uiHelper.getAppContext().getString(R.string.logging_in_to_piwigo_pattern), serverUri), new LoginResponseHandler(), new OnLoginAction());
            }
            return false; // don't run standard listener code
        }
    }

    private static class OnLogoutAction<V extends NavBarHeaderView<V,VUIH>, VUIH extends ViewGroupUIHelper<VUIH,V>> extends UIHelper.Action<VUIH, V, LogoutResponseHandler.PiwigoOnLogoutResponse> {

        @Override
        public boolean onSuccess(VUIH uiHelper, LogoutResponseHandler.PiwigoOnLogoutResponse response) {
            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
            uiHelper.getParent().runHttpClientCleanup(connectionPrefs);
            uiHelper.getParent().updateServerConnectionDetails();
            return false;
        }

        @Override
        public boolean onFailure(VUIH uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
            PiwigoSessionDetails.logout(connectionPrefs, uiHelper.getAppContext());
            onSuccess(uiHelper, null);
            return false;
        }
    }
}
