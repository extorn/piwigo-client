package delit.piwigoclient.ui.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.github.machinarius.preferencefragment.PreferenceFragment;

import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;

/**
 * Created by gareth on 26/05/17.
 */

public class MyPreferenceFragment extends PreferenceFragment {
    private UIHelper uiHelper;
    protected SharedPreferences prefs;

    protected UIHelper getUiHelper() {
        return uiHelper;
    }

    @Override
    public void onAttach(Context context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        if(uiHelper == null) {
            uiHelper = new FragmentUIHelper(this, prefs, context);
            BasicPiwigoResponseListener listener = buildPiwigoResponseListener(context);
            listener.withUiHelper(this, uiHelper);
            uiHelper.setPiwigoResponseListener(listener);
        }
        super.onAttach(context);
    }

    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new BasicPiwigoResponseListener();
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

    // Not needed from API v23 and above
    public Context getContext() {
        return getActivity().getApplicationContext();
    }

    @Override
    public View onCreateView(LayoutInflater paramLayoutInflater, ViewGroup paramViewGroup, Bundle savedInstanceState) {
        View v = super.onCreateView(paramLayoutInflater, paramViewGroup, savedInstanceState);
        if (savedInstanceState != null) {
            uiHelper.onRestoreSavedInstanceState(savedInstanceState);
        }
        uiHelper.registerToActiveServiceCalls();
        return v;
    }

    protected Preference findPreference(int preferenceId) {
        return findPreference(getContext().getString(preferenceId));
    }

    protected boolean getBooleanPreferenceValue(String preferenceKey) {
        Context context = findPreference(preferenceKey).getContext();
        return PreferenceManager
                .getDefaultSharedPreferences(context)
                .getBoolean(preferenceKey, false);
    }

    protected String getPreferenceValue(String preferenceKey) {
        Context context = findPreference(preferenceKey).getContext();
        return PreferenceManager
                .getDefaultSharedPreferences(context)
                .getString(preferenceKey, "");
    }


    @Override
    public void onResume() {
        super.onResume();
        uiHelper.handleAnyQueuedPiwigoMessages();
        uiHelper.showNextQueuedMessage();
    }

}
