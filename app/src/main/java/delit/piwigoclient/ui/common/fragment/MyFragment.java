package delit.piwigoclient.ui.common.fragment;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.ui.common.FragmentUIHelper;

/**
 * Created by gareth on 26/05/17.
 */

public class MyFragment extends Fragment {

    private static final String STATE_ACTIVE_SESSION_TOKEN = "activeSessionToken";
    private static final String STATE_ACTIVE_SERVER_CONNECTION = "activeServerConnection";
    protected ProgressDialog determinateProgressDialog;
    protected SharedPreferences prefs;
    // Stored state below here.
    private FragmentUIHelper uiHelper;
    private String piwigoSessionToken;
    private String piwigoServerConnected;

    protected long addActiveServiceCall(int titleStringId, long messageId) {
        return addActiveServiceCall(getString(titleStringId), messageId);
    }

    protected long addActiveServiceCall(String title, long messageId) {
        uiHelper.addActiveServiceCall(title, messageId);
        return messageId;
    }

    protected void addActiveServiceCall(long messageId) {
        uiHelper.addActiveServiceCall(R.string.talking_to_server_please_wait, messageId);
    }

    @Override
    public void onDetach() {
        uiHelper.deregisterFromActiveServiceCalls();
        uiHelper.closeAllDialogs();
        super.onDetach();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        uiHelper.onSaveInstanceState(outState);
        outState.putString(STATE_ACTIVE_SESSION_TOKEN, piwigoSessionToken);
        outState.putString(STATE_ACTIVE_SERVER_CONNECTION, piwigoServerConnected);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onAttach(Context context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        if(uiHelper == null) {
            uiHelper = buildUIHelper(context);
            BasicPiwigoResponseListener listener = buildPiwigoResponseListener(context);
            listener.withUiHelper(this, uiHelper);
            uiHelper.setPiwigoResponseListener(listener);
        }
        super.onAttach(context);
        setupDialogBoxes();
    }

    protected FragmentUIHelper buildUIHelper(Context context) {
        return new FragmentUIHelper(this, prefs, context);
    }

    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new BasicPiwigoResponseListener();
    }

    protected boolean isSessionDetailsChanged() {
        return !PiwigoSessionDetails.matchesSessionToken(ConnectionPreferences.getActiveProfile(), piwigoSessionToken);
    }
    protected boolean isServerConnectionChanged() {
        return !PiwigoSessionDetails.matchesServerConnection(ConnectionPreferences.getActiveProfile(), piwigoServerConnected);
    }

    protected void updateActiveSessionDetails() {
        piwigoSessionToken = PiwigoSessionDetails.getActiveSessionToken(ConnectionPreferences.getActiveProfile());
        piwigoServerConnected = PiwigoSessionDetails.getActiveServerConnection(ConnectionPreferences.getActiveProfile());
    }

    @Override
    public void onResume() {
        super.onResume();

        // This block wrapper is to hopefully protect against a WindowManager$BadTokenException when showing a dialog as part of this call.
        if(getActivity().isDestroyed() || getActivity().isFinishing()) {
            return;
        }

        Context context = getContext();
        if (uiHelper.isContextOutOfSync(context)) {
            uiHelper.swapToNewContext(context);
        }
        uiHelper.handleAnyQueuedPiwigoMessages();
        uiHelper.showNextQueuedMessage();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        if (savedInstanceState != null) {
            uiHelper.onRestoreSavedInstanceState(savedInstanceState);
            piwigoSessionToken = savedInstanceState.getString(STATE_ACTIVE_SESSION_TOKEN);
            piwigoServerConnected = savedInstanceState.getString(STATE_ACTIVE_SERVER_CONNECTION);
        }
        if(piwigoSessionToken == null) {
            updateActiveSessionDetails();
        }

        doInOnCreateView();

        return super.onCreateView(inflater, container, savedInstanceState);
    }

    /**
     * Currently registers for active service calls.
     */
    protected void doInOnCreateView() {
        uiHelper.registerToActiveServiceCalls();
    }

    private void setupDialogBoxes() {
        if(determinateProgressDialog != null) {
            // don't set them up twice.
            return;
        }
        determinateProgressDialog = new ProgressDialog(getActivity());
        determinateProgressDialog.setCancelable(false);
        determinateProgressDialog.setIndeterminate(false);
        determinateProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
    }

    protected FragmentUIHelper getUiHelper() {
        return uiHelper;
    }

    protected boolean isAppInReadOnlyMode() {
        return prefs.getBoolean(getContext().getString(R.string.preference_app_read_only_mode_key), false);
    }

}
