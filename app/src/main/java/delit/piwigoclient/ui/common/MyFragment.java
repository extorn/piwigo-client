package delit.piwigoclient.ui.common;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;

/**
 * Created by gareth on 26/05/17.
 */

public class MyFragment extends Fragment {

    protected ProgressDialog determinateProgressDialog;
    protected SharedPreferences prefs;
    // Stored state below here.
    private FragmentUIHelper uiHelper;

    protected long addActiveServiceCall(int titleStringId, long messageId) {
        uiHelper.addActiveServiceCall(getString(titleStringId), messageId);
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
    public void onSaveInstanceState(Bundle outState) {
        uiHelper.onSaveInstanceState(outState);
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

    @Override
    public void onResume() {
        super.onResume();
        uiHelper.handleAnyQueuedPiwigoMessages();
        uiHelper.showNextQueuedMessage();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        if (savedInstanceState != null) {
            uiHelper.onRestoreSavedInstanceState(savedInstanceState);
        }
        //TODO move inside the savedState reload block above?
        uiHelper.registerToActiveServiceCalls();

        View v = super.onCreateView(inflater, container, savedInstanceState);

        return v;
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

    public FragmentUIHelper getUiHelper() {
        return uiHelper;
    }

    protected boolean isAppInReadOnlyMode() {
        boolean isReadOnly = prefs.getBoolean(getContext().getString(R.string.preference_app_read_only_mode_key), false);
        return isReadOnly;
    }

}
