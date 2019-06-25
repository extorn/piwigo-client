package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.CheckBoxPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.LoginResponseHandler;
import delit.piwigoclient.ui.common.UIHelper;
import delit.libs.ui.view.fragment.MyPreferenceFragment;
import delit.piwigoclient.ui.common.preference.ServerAlbumListPreference;
import delit.piwigoclient.ui.common.preference.ServerAlbumSelectPreference;
import delit.piwigoclient.ui.common.preference.ServerConnectionsListPreference;
import delit.piwigoclient.ui.events.trackable.AutoUploadJobViewCompleteEvent;
import delit.libs.util.CollectionUtils;
import delit.libs.util.SetUtils;

public class AutoUploadJobPreferenceFragment extends MyPreferenceFragment {

    private static final String JOB_ID_ARG = "jobId";
    private static final String ACTION_ID_ARG = "actionId";
    private PreferenceChangeListener prefChangeListener;
    private int actionId = -1;
    private int jobId = -1;
    private SharedPreferences appPrefs;

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
        jobId = getArguments().getInt(JOB_ID_ARG);
        actionId = getArguments().getInt(ACTION_ID_ARG);
        super.onCreate(paramBundle);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        appPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        // ensure we use the shared preferences for this job
        getPreferenceManager().setSharedPreferencesName(AutoUploadJobConfig.getSharedPreferencesName(jobId));
        // Load the preferences from an XML resource
        setPreferencesFromResource(R.xml.pref_auto_upload_job, rootKey);

        ServerConnectionsListPreference serverConnPref = (ServerConnectionsListPreference) findPreference(R.string.preference_data_upload_automatic_job_server_key);

        MultiSelectListPreference fileExtPref = (MultiSelectListPreference) findPreference(R.string.preference_data_upload_automatic_job_file_exts_uploaded_key);
        fileExtPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                MultiSelectListPreference pref = (MultiSelectListPreference) preference;
                if (pref.getEntries() == null || pref.getValues() == null) {
                    preference.setEnabled(false);
                }
                return false;
            }
        });

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            Preference p = findPreference(R.string.preference_data_upload_automatic_job_compress_videos_key);
            p.setEnabled(false);
            p.setVisible(false);
        }
    }


    /**
     * disable this job as long as at least one preference is not valid
     */
    private void invokePreferenceValuesValidation(boolean isFinalValidationCheck) {
        boolean allPreferencesValid;
        ConnectionPreferences.ProfilePreferences profilePrefs;
        // check server connection details

        String serverProfile = getPreferenceValueOrNull(R.string.preference_data_upload_automatic_job_server_key);
        allPreferencesValid = serverProfile != null;
        if(allPreferencesValid) {
            profilePrefs = ConnectionPreferences.getPreferences(serverProfile, appPrefs, getContext());
            String serverName = profilePrefs.getPiwigoServerAddress(appPrefs, getContext());
            allPreferencesValid = serverName != null;
        }

        if (allPreferencesValid) {
            getAcceptableUploadFileTypes();
        }

        // check local folder
        if(allPreferencesValid) {
            String localFolder = getPreferenceValueOrNull(R.string.preference_data_upload_automatic_job_local_folder_key);
            allPreferencesValid = localFolder != null && new File(localFolder).exists();
        }
        // check remote privacy level
        if(allPreferencesValid) {
            int privacyLevel = getIntegerPreferenceValue(getString(R.string.preference_data_upload_automatic_job_privacy_level_key), R.integer.preference_data_upload_automatic_job_privacy_level_default);
            allPreferencesValid &= privacyLevel != Integer.MIN_VALUE;
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

    private void getAcceptableUploadFileTypes() {
        String serverProfile = getPreferenceValueOrNull(R.string.preference_data_upload_automatic_job_server_key);
        final ConnectionPreferences.ProfilePreferences profilePrefs = ConnectionPreferences.getPreferences(serverProfile, appPrefs, getContext());

        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(profilePrefs);
        if (sessionDetails != null) {
            updateAvailableFileTypes(sessionDetails.getAllowedFileTypes());
        } else {
            String serverUri = profilePrefs.getPiwigoServerAddress(appPrefs, getContext());
            LoginResponseHandler loginHandler = new LoginResponseHandler();
            loginHandler.withConnectionPreferences(profilePrefs);
            getUiHelper().addActionOnResponse(loginHandler.getMessageId(), new UIHelper.Action() {
                @Override
                public boolean onSuccess(UIHelper uiHelper, PiwigoResponseBufferingHandler.Response response) {
                    updateAvailableFileTypes(PiwigoSessionDetails.getInstance(profilePrefs).getAllowedFileTypes());
                    return true;
                }
            });
            callServer(getString(R.string.logging_in_to_piwigo_pattern, serverUri), loginHandler);
        }
    }

    private void updateAvailableFileTypes(Set<String> allowedFileTypes) {
        MultiSelectListPreference p = (MultiSelectListPreference) findPreference(R.string.preference_data_upload_automatic_job_file_exts_uploaded_key);
        String[] availableOptions = allowedFileTypes.toArray(new String[0]);

        AutoUploadJobConfig jobConfig = new AutoUploadJobConfig(jobId);
        Set<String> newValues = jobConfig.getFileExtsToUpload(getContext());
        Set<String> availableValues = new HashSet<>();
        Collections.addAll(availableValues, availableOptions);
        Set<String> invalidOptions = SetUtils.difference(newValues, availableValues);
        if (invalidOptions.size() > 0) {
            // remove  all invalid options from the list
            newValues.removeAll(invalidOptions);
        }
        p.setEntries(availableOptions);
        p.setEntryValues(availableOptions);
        p.setValues(newValues); // update the users selection
        p.setEnabled(true);
        String valueStr = CollectionUtils.toCsvList(p.getValues());
        if (valueStr == null) {
            valueStr = "";
        }
        p.setSummary(getString(R.string.preference_data_upload_automatic_job_file_exts_uploaded_summary, valueStr));
    }

    private void invokeRemoteFolderPreferenceValidation() {
        String serverProfile = getPreferenceValueOrNull(R.string.preference_data_upload_automatic_job_server_key);
        ConnectionPreferences.ProfilePreferences profilePrefs = ConnectionPreferences.getPreferences(serverProfile, getPrefs(), getContext());
        String remoteFolderDetails = getPreferenceValueOrNull(R.string.preference_data_upload_automatic_job_server_album_key);
        if(remoteFolderDetails != null) {
            long albumId = ServerAlbumListPreference.ServerAlbumPreference.getSelectedAlbumId(remoteFolderDetails);
            if(albumId >= 0) {
                AlbumGetSubAlbumNamesResponseHandler albumHandler = new AlbumGetSubAlbumNamesResponseHandler(albumId, false);
                albumHandler.withConnectionPreferences(profilePrefs);
                albumHandler.forceLogin();
                callServer(R.string.progress_loading_albums, albumHandler);
            } else {
                updateJobValidPreferenceIfNeeded(false, false);
            }
        } else {
            updateJobValidPreferenceIfNeeded(false, false);
        }
    }

    private void finishPreferenceValuesValidation(ArrayList<CategoryItemStub> albumNames) {
        boolean remoteAlbumExists = albumNames != null && albumNames.size() >= 1;

        SharedPreferences.Editor editor = getPrefs().edit();
        if(remoteAlbumExists) {
            // ensure the folder name is in-sync with the value on the server
            ServerAlbumSelectPreference.ServerAlbumDetails selectedAlbumDetails = new AutoUploadJobConfig(jobId).getUploadToAlbumDetails(getContext());
            boolean changed = false;
            if(selectedAlbumDetails.getAlbumId() >= 0) {
                String selectedAlbumName = selectedAlbumDetails.getAlbumName();
                CategoryItemStub testAlbum = albumNames.get(0);

                if(!testAlbum.getName().equals(selectedAlbumName)) {
                    selectedAlbumName = testAlbum.getName();
                    changed = true;
                }
                List<Long> selectedAlbumParentage = selectedAlbumDetails.getParentage();
                String selectedAlbumPath = selectedAlbumDetails.getAlbumPath();
                if(selectedAlbumParentage == null || !testAlbum.getParentageChain().equals(selectedAlbumParentage)) {
                    selectedAlbumParentage = testAlbum.getParentageChain();
                    StringBuilder sb = new StringBuilder();
                    for(int i = 0; i < selectedAlbumParentage.size() - 1; i++) {
                        sb.append("? / ");
                    }
                    sb.append(selectedAlbumName);
                    selectedAlbumPath = sb.toString();
                    changed = true;
                }
                if(changed) {
                    ServerAlbumSelectPreference.ServerAlbumDetails newAlbumDetails = new ServerAlbumSelectPreference.ServerAlbumDetails(selectedAlbumDetails.getAlbumId(), selectedAlbumName, selectedAlbumParentage, selectedAlbumPath);
                    editor.putString(getString(R.string.preference_data_upload_automatic_job_server_album_key), newAlbumDetails.encode());
                }
            }
        } else {
            editor.putString(getString(R.string.preference_data_upload_automatic_job_server_album_key), null);
        }
        editor.commit();

        getListView().getAdapter().notifyDataSetChanged();

        // update job validity status.
        if(!remoteAlbumExists) {
            updateJobValidPreferenceIfNeeded(false, true);
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
