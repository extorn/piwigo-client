package delit.piwigoclient.ui.preferences;

import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.preference.Preference;

import delit.libs.core.util.Logging;
import delit.libs.ui.view.preference.NumberPickerPreference;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.business.UploadPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.LoginResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.PiwigoClientFailedUploadsCleanResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;

/**
 * Created by gareth on 12/05/17.
 */

public class UploadPreferenceFragment extends MyPreferenceFragment<UploadPreferenceFragment> {

    private static final String TAG = "Upload Settings";

    public UploadPreferenceFragment() {
    }

    public UploadPreferenceFragment(int pagerIndex) {
        super(pagerIndex);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_page_upload, rootKey);
        setHasOptionsMenu(true);

        NumberPickerPreference pref = (NumberPickerPreference) findPreference(R.string.preference_data_upload_preferredColumnsLandscape_key);
        int defaultVal = UploadPreferences.getDefaultFilesColumnCount(getActivity(), Configuration.ORIENTATION_LANDSCAPE);
        pref.updateDefaultValue(defaultVal);

        pref = (NumberPickerPreference) findPreference(R.string.preference_data_upload_preferredColumnsPortrait_key);
        defaultVal = UploadPreferences.getDefaultFilesColumnCount(getActivity(), Configuration.ORIENTATION_PORTRAIT);
        pref.updateDefaultValue(defaultVal);

        Preference button = findPreference(R.string.preference_data_upload_clear_failed_uploads_from_server_key);
        button.setOnPreferenceClickListener(preference -> {
            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
            PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);
            String serverUri = connectionPrefs.getPiwigoServerAddress(getPrefs(), getContext());
            if (sessionDetails == null || !sessionDetails.isLoggedIn()) {
                getUiHelper().invokeActiveServiceCall(getString(R.string.logging_in_to_piwigo_pattern, serverUri), new LoginResponseHandler(), new OnLoginAction());
            } else if(sessionDetails.isPiwigoClientCleanUploadsAvailable()){
                getUiHelper().invokeActiveServiceCall(R.string.progress_clearing_failed_uploads_from_server, new PiwigoClientFailedUploadsCleanResponseHandler(), new FailedUploadCleanAction());
            } else {
                getUiHelper().showDetailedMsg(R.string.alert_error, R.string.alert_unavailable_feature_piwigo_client_plugin_needed);
            }
            return true;
        });
    }

    private static class OnLoginAction extends UIHelper.Action<FragmentUIHelper<UploadPreferenceFragment>, UploadPreferenceFragment, LoginResponseHandler.PiwigoOnLoginResponse>  implements Parcelable {

        protected OnLoginAction() {}

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

        public static final Creator<OnLoginAction> CREATOR = new Creator<OnLoginAction>() {
            @Override
            public OnLoginAction createFromParcel(Parcel in) {
                return new OnLoginAction(in);
            }

            @Override
            public OnLoginAction[] newArray(int size) {
                return new OnLoginAction[size];
            }
        };

        @Override
        public boolean onSuccess(FragmentUIHelper<UploadPreferenceFragment> uiHelper, LoginResponseHandler.PiwigoOnLoginResponse response) {
            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
            PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);
            if (sessionDetails == null) {
                Logging.log(Log.ERROR, TAG, "Session details null after login success - weird");
            } else if (sessionDetails.isPiwigoClientCleanUploadsAvailable()) {
                uiHelper.invokeActiveServiceCall(R.string.progress_clearing_failed_uploads_from_server, new PiwigoClientFailedUploadsCleanResponseHandler(), new FailedUploadCleanAction());
            } else {
                uiHelper.showDetailedMsg(R.string.alert_error, R.string.alert_unavailable_feature_piwigo_client_plugin_needed);
            }
            return true;
        }
    }

    private static class FailedUploadCleanAction extends UIHelper.Action<FragmentUIHelper<UploadPreferenceFragment>, UploadPreferenceFragment, PiwigoClientFailedUploadsCleanResponseHandler.PiwigoFailedUploadsCleanedResponse>  implements Parcelable {

        protected FailedUploadCleanAction(){}

        protected FailedUploadCleanAction(Parcel in) {
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

        public static final Creator<FailedUploadCleanAction> CREATOR = new Creator<FailedUploadCleanAction>() {
            @Override
            public FailedUploadCleanAction createFromParcel(Parcel in) {
                return new FailedUploadCleanAction(in);
            }

            @Override
            public FailedUploadCleanAction[] newArray(int size) {
                return new FailedUploadCleanAction[size];
            }
        };

        @Override
        public boolean onSuccess(FragmentUIHelper<UploadPreferenceFragment> uiHelper, PiwigoClientFailedUploadsCleanResponseHandler.PiwigoFailedUploadsCleanedResponse response) {
            uiHelper.showDetailedMsg(R.string.alert_information, uiHelper.getAppContext().getString(R.string.cleared_failed_uploads_from_server_pattern, response.getFilesCleaned()));
            return super.onSuccess(uiHelper, response);
        }

        @Override
        public boolean onFailure(FragmentUIHelper<UploadPreferenceFragment> uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
            return super.onFailure(uiHelper, response);
        }
    }

}