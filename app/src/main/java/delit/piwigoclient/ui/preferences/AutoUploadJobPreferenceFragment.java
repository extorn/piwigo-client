package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.io.File;
import java.util.ArrayList;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.ui.common.MyPreferenceFragment;

public class AutoUploadJobPreferenceFragment extends MyPreferenceFragment {

    private static final String JOB_ID_ARG = "jobId";
    int jobId = -1;

    public AutoUploadJobPreferenceFragment(){}

    public static AutoUploadJobPreferenceFragment newInstance(int jobId) {
        AutoUploadJobPreferenceFragment fragment = new AutoUploadJobPreferenceFragment();
        Bundle args = new Bundle();
        args.putInt(JOB_ID_ARG, jobId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);
        jobId = getArguments().getInt(JOB_ID_ARG);
        getPreferenceManager().setSharedPreferencesName(String.format("autoUploadJob[%1$d]",jobId));
    }


    /**
     * disable this job as long as at least one preference is not valid
     */
    private void invokePreferenceValuesValidation() {
        SharedPreferences appPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        boolean allPreferencesValid = true;
        ConnectionPreferences.ProfilePreferences profilePrefs = null;
        // check server connection details
        if(allPreferencesValid) {
            String serverProfile = getPreferenceValueOrNull(R.string.preference_data_upload_automatic_server_key);
            profilePrefs = ConnectionPreferences.getPreferences(serverProfile);
            String serverName = profilePrefs.getPiwigoServerAddress(appPrefs, getContext());
            allPreferencesValid &= serverName != null;
        }
        // check local folder
        if(allPreferencesValid) {
            String localFolder = getPreferenceValueOrNull(R.string.preference_data_upload_automatic_local_folder_key);
            allPreferencesValid &= localFolder != null && new File(localFolder).exists();
        }
        // check remote folder
        if(allPreferencesValid) {
            String remoteFolderDetails = getPreferenceValueOrNull(R.string.preference_data_upload_automatic_server_album_key);
            allPreferencesValid &= remoteFolderDetails != null;
            if(allPreferencesValid) {
                long albumId = ServerAlbumListPreference.ServerAlbumPreference.getSelectedAlbumId(remoteFolderDetails);
                AlbumGetSubAlbumNamesResponseHandler albumHandler = new AlbumGetSubAlbumNamesResponseHandler(albumId, false);
                albumHandler.withConnectionPreferences(profilePrefs);
                addActiveServiceCall(albumHandler.invokeAsync(getContext()));
            }
        }
    }

    private void finishPreferenceValuesValidation(ArrayList<CategoryItemStub> albumNames) {
        boolean allPreferencesValid = albumNames != null && albumNames.size() == 1;
        SharedPreferences.Editor editor = getPrefs().edit();

        editor.putBoolean(getString(R.string.preference_data_upload_automatic_job_is_valid_key), allPreferencesValid);

        if(allPreferencesValid) {
            // ensure the folder name is in-sync with the value on the server
            String remoteFolderDetails = ServerAlbumListPreference.ServerAlbumPreference.toValue(albumNames.get(0));
            editor.putString(getString(R.string.preference_data_upload_automatic_server_album_key), remoteFolderDetails);
        }

        editor.commit();
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    @Override
    public View onCreateView(LayoutInflater paramLayoutInflater, ViewGroup paramViewGroup, Bundle paramBundle) {
        View v = super.onCreateView(paramLayoutInflater, paramViewGroup, paramBundle);
//        Preference p = findPreference(R.string.preference_data_upload_automatic_server_key);
        addPreferencesFromResource(R.xml.pref_auto_upload_job);
        setHasOptionsMenu(true);

        invokePreferenceValuesValidation();
        return v;
    }

    private class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {


        @Override
        protected void handleErrorRetryPossible(final PiwigoResponseBufferingHandler.RemoteErrorResponse errorResponse, int title, String msg) {
            // for now, allow the default "retry button" to pop-up.
            super.handleErrorRetryPossible(errorResponse, title, msg);
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
            if(response instanceof PiwigoResponseBufferingHandler.PiwigoGetSubAlbumNamesResponse) {
                finishPreferenceValuesValidation(((PiwigoResponseBufferingHandler.PiwigoGetSubAlbumNamesResponse) response).getAlbumNames());
            }
        }
    }


}
