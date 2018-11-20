package delit.piwigoclient.ui.preferences;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.upload.BackgroundPiwigoUploadService;
import delit.piwigoclient.ui.common.fragment.MyPreferenceFragment;

public class AutoUploadJobsPreferenceFragment extends MyPreferenceFragment {

    private View view;

    public AutoUploadJobsPreferenceFragment(){}

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Load the preferences from an XML resource
        setPreferencesFromResource(R.xml.pref_auto_upload_jobs, rootKey);
    }

    public static AutoUploadJobsPreferenceFragment newInstance() {
        AutoUploadJobsPreferenceFragment fragment = new AutoUploadJobsPreferenceFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    private SharedPreferences.OnSharedPreferenceChangeListener prefChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if(getActivity() == null) {
                return;
            }
            if(key.equals(getString(R.string.preference_data_upload_automatic_upload_enabled_key))) {
                onBackgroundServiceEnabled(getBooleanPreferenceValue(key, R.bool.preference_data_upload_automatic_upload_enabled_default));
            } else if(key.equals(getString(R.string.preference_data_upload_automatic_upload_wireless_only_key))) {
                onUploadWirelessOnlyChanged(getBooleanPreferenceValue(key, R.bool.preference_data_upload_automatic_upload_wireless_only_default));
            } else if(key.equals(getString(R.string.preference_data_upload_automatic_upload_jobs_key))) {
                onUploadJobsChanged();
            }

        }

        private void onUploadWirelessOnlyChanged(Boolean uploadOverWirelessOnly) {
            if(BackgroundPiwigoUploadService.isStarted()) {
                // ensure the service knows the change occurred.
                BackgroundPiwigoUploadService.wakeServiceIfSleeping();
            }
        }

        private void onBackgroundServiceEnabled(Boolean backgroundUploadEnabled) {
            if(BackgroundPiwigoUploadService.isStarted() && !backgroundUploadEnabled) {
                BackgroundPiwigoUploadService.killService();
            } else if(!BackgroundPiwigoUploadService.isStarted() && backgroundUploadEnabled) {
                BackgroundPiwigoUploadService.startService(getContext());
            }
        }
    };

    private void onUploadJobsChanged() {
        if(BackgroundPiwigoUploadService.isStarted()) {
            // ensure the service knows the change occurred.
            BackgroundPiwigoUploadService.wakeServiceIfSleeping();
        }
    }

    @Override
    protected DialogFragment onDisplayCustomPreferenceDialog(Preference preference) {
        if(preference instanceof AutoUploadJobsPreference) {
            return AutoUploadJobsPreferenceDialogFragmentCompat.newInstance(preference.getKey());
        }
        return super.onDisplayCustomPreferenceDialog(preference);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onPause() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(prefChangeListener);
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(prefChangeListener);
    }
}
