package delit.piwigoclient.ui.preferences;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.piwigoApi.upload.BackgroundPiwigoUploadService;
import delit.piwigoclient.ui.common.fragment.MyPreferenceFragment;
import delit.piwigoclient.ui.events.trackable.AutoUploadJobViewCompleteEvent;

public class AutoUploadJobPreferenceFragment extends MyPreferenceFragment {

    private static final String JOB_ID_ARG = "jobId";
    private static final String ACTION_ID_ARG = "actionId";
    private PreferenceChangeListener prefChangeListener;
    private int actionId = -1;
    private int jobId = -1;

    public AutoUploadJobPreferenceFragment(){}

    public static AutoUploadJobPreferenceFragment newInstance(int actionId, int jobId) {
        AutoUploadJobPreferenceFragment fragment = new AutoUploadJobPreferenceFragment();
        Bundle args = new Bundle();
        args.putInt(ACTION_ID_ARG, actionId);
        args.putInt(JOB_ID_ARG, jobId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);
        jobId = getArguments().getInt(JOB_ID_ARG);
        actionId = getArguments().getInt(ACTION_ID_ARG);
        getPreferenceManager().setSharedPreferencesName(AutoUploadJobConfig.getSharedPreferencesName(jobId));
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Load the preferences from an XML resource
        setPreferencesFromResource(R.xml.pref_auto_upload_job, rootKey);
    }


    /**
     * disable this job as long as at least one preference is not valid
     */
    private void invokePreferenceValuesValidation(boolean isFinalValidationCheck) {
        SharedPreferences appPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        boolean allPreferencesValid;
        ConnectionPreferences.ProfilePreferences profilePrefs;
        // check server connection details

        String serverProfile = getPreferenceValueOrNull(R.string.preference_data_upload_automatic_job_server_key);
        allPreferencesValid = serverProfile != null;
        if(allPreferencesValid) {
            profilePrefs = ConnectionPreferences.getPreferences(serverProfile);
            String serverName = profilePrefs.getPiwigoServerAddress(appPrefs, getContext());
            allPreferencesValid = serverName != null;
        }

        // check local folder
        if(allPreferencesValid) {
            String localFolder = getPreferenceValueOrNull(R.string.preference_data_upload_automatic_job_local_folder_key);
            allPreferencesValid = localFolder != null && new File(localFolder).exists();
        }
        // check remote privacy level
        if(allPreferencesValid) {
            int privacyLevel = getPreferenceValueOrMinInt(R.string.preference_data_upload_automatic_job_privacy_level_key);
            allPreferencesValid = allPreferencesValid & privacyLevel != Integer.MIN_VALUE;
        }

        updateJobValidPreferenceIfNeeded(allPreferencesValid, isFinalValidationCheck);

        // check remote folder (will trigger marking job as valid if successfully completes)
        if(allPreferencesValid && !isFinalValidationCheck) {
            invokeRemoteFolderPreferenceValidation();
        }
    }

    private void updateJobValidPreferenceIfNeeded(boolean allPreferencesValid, boolean isFinalValidationCheck) {
        if(isFinalValidationCheck || (!allPreferencesValid && getBooleanPreferenceValue(getString(R.string.preference_data_upload_automatic_job_is_valid_key), false))) {
            ((CheckBoxPreference)findPreference(R.string.preference_data_upload_automatic_job_is_valid_key)).setChecked(allPreferencesValid);
//            getListView().getAdapter().notifyDataSetChanged();
        }
    }

    private void invokeRemoteFolderPreferenceValidation() {
        String serverProfile = getPreferenceValueOrNull(R.string.preference_data_upload_automatic_job_server_key);
        ConnectionPreferences.ProfilePreferences profilePrefs = ConnectionPreferences.getPreferences(serverProfile);
        String remoteFolderDetails = getPreferenceValueOrNull(R.string.preference_data_upload_automatic_job_server_album_key);
        if(remoteFolderDetails != null) {
            long albumId = ServerAlbumListPreference.ServerAlbumPreference.getSelectedAlbumId(remoteFolderDetails);
            AlbumGetSubAlbumNamesResponseHandler albumHandler = new AlbumGetSubAlbumNamesResponseHandler(albumId, false);
            albumHandler.withConnectionPreferences(profilePrefs);
            callServer(R.string.progress_loading_albums,albumHandler);
        } else {
            updateJobValidPreferenceIfNeeded(false, false);
        }
    }

    private void finishPreferenceValuesValidation(ArrayList<CategoryItemStub> albumNames) {
        boolean remoteAlbumExists = albumNames != null && albumNames.size() >= 1;

        SharedPreferences.Editor editor = getPrefs().edit();
        if(remoteAlbumExists) {
            // ensure the folder name is in-sync with the value on the server
            String remoteFolderDetails = ServerAlbumListPreference.ServerAlbumPreference.toValue(albumNames.get(0));
            editor.putString(getString(R.string.preference_data_upload_automatic_job_server_album_key), remoteFolderDetails);
        } else {
            editor.putString(getString(R.string.preference_data_upload_automatic_job_server_album_key), null);
        }
        editor.commit();

        getListView().getAdapter().notifyDataSetChanged();

        // update job validity status.
        if(!remoteAlbumExists) {
            updateJobValidPreferenceIfNeeded(remoteAlbumExists, true);
        } else {
            invokePreferenceValuesValidation(true);
        }

    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    @Override
    public View onCreateView(LayoutInflater paramLayoutInflater, ViewGroup paramViewGroup, Bundle paramBundle) {
        return super.onCreateView(paramLayoutInflater, paramViewGroup, paramBundle);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setHasOptionsMenu(true);

        invokePreferenceValuesValidation(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        prefChangeListener = new PreferenceChangeListener();
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(prefChangeListener);
    }

    @Override
    public void onPause() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(prefChangeListener);
        super.onPause();
    }

    @Override
    public void onStop() {
        EventBus.getDefault().postSticky(new AutoUploadJobViewCompleteEvent(actionId, jobId));
        super.onStop();
    }

    private class PreferenceChangeListener implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if(key.equals(getString(R.string.preference_data_upload_automatic_job_server_album_key))) {
                String remoteFolderDetails = getPreferenceValueOrNull(R.string.preference_data_upload_automatic_job_server_album_key);
                long albumId = ServerAlbumListPreference.ServerAlbumPreference.getSelectedAlbumId(remoteFolderDetails);
                if(albumId >= 0) {
                    invokeRemoteFolderPreferenceValidation();
                }
            } else if(key.equals(getString(R.string.preference_data_upload_automatic_upload_wireless_only_key))) {
                boolean wifiOnlyEnabled = getBooleanPreferenceValue(key, R.bool.preference_data_upload_automatic_upload_wireless_only_default);
                if(wifiOnlyEnabled) {
                    getUiHelper().runWithExtraPermissions(getActivity(), Build.VERSION_CODES.BASE, Integer.MAX_VALUE, Manifest.permission.ACCESS_NETWORK_STATE, getString(R.string.alert_network_monitor_permission_needed_for_wifi_upload));
                }
            } else if(key.equals(getString(R.string.preference_data_upload_automatic_upload_enabled_key))) {
                if(getBooleanPreferenceValue(key, R.bool.preference_data_upload_automatic_upload_enabled_default)) {
                    if(!BackgroundPiwigoUploadService.isStarted()) {
                        BackgroundPiwigoUploadService.startService(getContext(), true);
                    }
                } else {
                    BackgroundPiwigoUploadService.killService();
                }
            } else {
                invokePreferenceValuesValidation(false);
            }
        }
    }

    private class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {


        @Override
        protected void handleErrorRetryPossible(final PiwigoResponseBufferingHandler.RemoteErrorResponse errorResponse, int title, String msg, String detail) {
            // for now, allow the default "retry button" to pop-up.
            super.handleErrorRetryPossible(errorResponse, title, msg, null);
            // ensure the job is marked as invalid (it'll be updated if the user retries and it succeeds)
            finishPreferenceValuesValidation(null);

            // different idea for the retries button
//            getListView().removeHeaderView(retryActionView);
//            getListView().addHeaderView(actionButton);
        }

        /**
         * This occurs whenever and only when there is an error getting the expected response and that cannot be resolved.
         * @param title
         * @param message
         */
        @Override
        protected void showOrQueueMessage(int title, String message) {
            //TODO do something to notify user
            super.showOrQueueMessage(title, message);
            // ensure the job is marked as invalid.
            finishPreferenceValuesValidation(null);
        }

        @Override
        public <T extends PiwigoResponseBufferingHandler.Response> void onAfterHandlePiwigoResponse(T response) {
            super.onAfterHandlePiwigoResponse(response);
            if(response instanceof AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) {
                finishPreferenceValuesValidation(((AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) response).getAlbumNames());
            }
        }
    }


}
