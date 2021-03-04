package delit.piwigoclient.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import delit.libs.ui.util.BundleUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AppPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.ui.album.drillDownSelect.CategoryItemViewAdapterPreferences;
import delit.piwigoclient.ui.album.drillDownSelect.RecyclerViewCategoryItemSelectFragment;
import delit.piwigoclient.ui.common.ActivityUIHelper;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;
import delit.piwigoclient.ui.events.ConnectionsPreferencesChangedEvent;
import delit.piwigoclient.ui.events.ViewJobStatusDetailsEvent;
import delit.piwigoclient.ui.events.trackable.AutoUploadJobViewRequestedEvent;
import delit.piwigoclient.ui.events.trackable.ExpandingAlbumSelectionNeededEvent;
import delit.piwigoclient.ui.permissions.users.UserFragment;
import delit.piwigoclient.ui.preferences.AutoUploadJobPreferenceFragment;
import delit.piwigoclient.ui.upload.status.UploadJobStatusDetailsFragment;

public class PreferencesActivity<A extends PreferencesActivity<A,AUIH>, AUIH extends ActivityUIHelper<AUIH, A>> extends AbstractPreferencesActivity<A,AUIH> {

    private static final String TAG = "PrefAct";

    public static Intent buildIntent(Context context) {
        //Intent intent = new Intent(Intent.ACTION_APPLICATION_PREFERENCES, null, context.getApplicationContext(), PreferencesActivity.class);
        Intent intent = new Intent(context.getApplicationContext(), PreferencesActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        checkForAndHandleDeepLinkIntent(getIntent());
    }

    @Override
    public void onResume() {
        super.onResume();
        checkForAndHandleDeepLinkIntent(getIntent());
    }

    private void checkForAndHandleDeepLinkIntent(Intent intent) {
        if("android.intent.action.VIEW".equals(intent.getAction())) {
            Uri data = intent.getData();
            if(data != null) {
                getUiHelper().showOrQueueDialogQuestion(R.string.alert_question_title, getString(R.string.alert_create_or_update_server_connection_details), R.string.button_cancel, R.string.button_ok, new HandleDeepLinkUserConfirmationListener<>(getUiHelper()));
            }
        }
    }

    public void handleDeepLinkNow() {
        Intent intent = getIntent();
        Uri data = intent.getData();
        intent.setData(null); // don't do this action twice

        ConnectionPreferences.ProfilePreferences profile = ConnectionPreferences.getActiveProfile();
        if(PiwigoSessionDetails.isLoggedIn(profile)) {
            //FIXME Log out of active session on the server too
            PiwigoSessionDetails.logout(profile, this);
        }
        String affectedProfile = ConnectionPreferences.parseDeepLinkSettingsChange(data, this,true);
        getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.connection_profile_amended_with_new_details_pattern, affectedProfile));
        EventBus.getDefault().post(new ConnectionsPreferencesChangedEvent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        checkForAndHandleDeepLinkIntent(intent);
    }

    @Override
    protected String getDesiredLanguage(Context context) {
        return AppPreferences.getDesiredLanguage(getSharedPrefs(context), context);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        if(BuildConfig.DEBUG) {
            BundleUtils.logSize("Current Preferences Activity", outState);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(final ViewJobStatusDetailsEvent event) {
        if (event.isHandled()) {
            return;
        }
        UploadJobStatusDetailsFragment<?,?> fragment = UploadJobStatusDetailsFragment.newInstance(event.getJob());
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AutoUploadJobViewRequestedEvent event) {
        AutoUploadJobPreferenceFragment<?,?> fragment = AutoUploadJobPreferenceFragment.newInstance(event.getActionId(), event.getJobId());
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(ExpandingAlbumSelectionNeededEvent event) {
//        ExpandableAlbumsListAdapter.ExpandableAlbumsListAdapterPreferences prefs = new ExpandableAlbumsListAdapter.ExpandableAlbumsListAdapterPreferences();
//        AlbumSelectExpandableFragment f = AlbumSelectExpandableFragment.newInstance(prefs, event.getActionId(), event.getInitialSelection());
        CategoryItemViewAdapterPreferences prefs = new CategoryItemViewAdapterPreferences(event.getInitialRoot(), event.isAllowEditing(), event.getInitialSelection(), event.isAllowMultiSelect(), event.isInitialSelectionLocked());
        prefs.withConnectionProfile(event.getConnectionProfileName());
        RecyclerViewCategoryItemSelectFragment<?,?> f = RecyclerViewCategoryItemSelectFragment.newInstance(prefs, event.getActionId());
        showFragmentNow(f);
    }

    private static class HandleDeepLinkUserConfirmationListener<A extends PreferencesActivity<A,AUIH>, AUIH extends ActivityUIHelper<AUIH,A>> extends QuestionResultAdapter<AUIH, A> implements Parcelable {

        public HandleDeepLinkUserConfirmationListener(AUIH uiHelper) {
            super(uiHelper);
        }

        protected HandleDeepLinkUserConfirmationListener(Parcel in) {
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

        public static final Creator<HandleDeepLinkUserConfirmationListener<?,?>> CREATOR = new Creator<HandleDeepLinkUserConfirmationListener<?,?>>() {
            @Override
            public HandleDeepLinkUserConfirmationListener<?,?> createFromParcel(Parcel in) {
                return new HandleDeepLinkUserConfirmationListener<>(in);
            }

            @Override
            public HandleDeepLinkUserConfirmationListener<?,?>[] newArray(int size) {
                return new HandleDeepLinkUserConfirmationListener[size];
            }
        };

        @Override
        protected void onPositiveResult(AlertDialog dialog) {
            getParent().handleDeepLinkNow();
        }
    }
}
