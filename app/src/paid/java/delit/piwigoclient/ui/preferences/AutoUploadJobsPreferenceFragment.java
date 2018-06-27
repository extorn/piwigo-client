package delit.piwigoclient.ui.preferences;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.upload.BackgroundPiwigoUploadService;
import delit.piwigoclient.ui.common.fragment.MyPreferenceFragment;

public class AutoUploadJobsPreferenceFragment extends MyPreferenceFragment {

    private View view;

    public AutoUploadJobsPreferenceFragment(){}

    public static AutoUploadJobsPreferenceFragment newInstance() {
        AutoUploadJobsPreferenceFragment fragment = new AutoUploadJobsPreferenceFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater paramLayoutInflater, ViewGroup paramViewGroup, Bundle paramBundle) {
        if(view == null) {
            view = super.onCreateView(paramLayoutInflater, paramViewGroup, paramBundle);
        }
        return view;
    }

    SharedPreferences.OnSharedPreferenceChangeListener prefChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if(getActivity() == null) {
                return;
            }
            if(key.equals(getString(R.string.preference_data_upload_automatic_upload_enable_key))) {
                onBackgroundServiceEnabled(getBooleanPreferenceValue(key));
            } else if(key.equals(getString(R.string.preference_data_upload_automatic_upload_wireless_only_key))) {
                onUploadWirelessOnlyChanged(getBooleanPreferenceValue(key));
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
                BackgroundPiwigoUploadService.startService(getContext(), true);
            }
        }
    };

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if(getPreferenceScreen() == null) {
            addPreferencesFromResource(R.xml.pref_auto_upload_jobs);
            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(prefChangeListener);
        }
        setHasOptionsMenu(true);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
    }
}
