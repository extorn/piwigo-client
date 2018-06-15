package delit.piwigoclient.ui.common.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.view.View;

import com.github.machinarius.preferencefragment.PreferenceFragment;

import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;

/**
 * Created by gareth on 26/05/17.
 */

public class MyPreferenceFragment extends PreferenceFragment {
    private UIHelper uiHelper;
    private Context c;

    protected UIHelper getUiHelper() {
        return uiHelper;
    }

    protected SharedPreferences getPrefs() {
        return getPreferenceManager().getSharedPreferences();
    }

    @Override
    public void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);
        if(uiHelper == null) {
            uiHelper = new FragmentUIHelper(this, getPrefs(), c);
            BasicPiwigoResponseListener listener = buildPiwigoResponseListener(c);
            listener.withUiHelper(this, uiHelper);
            uiHelper.setPiwigoResponseListener(listener);
        }
    }

    @Override
    public void onAttach(Context context) {
        c = context;
        super.onAttach(context);
    }

    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new BasicPiwigoResponseListener();
    }

    protected void addActiveServiceCall(@StringRes int stringId, long messageId) {
        uiHelper.addActiveServiceCall(stringId, messageId);
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
        // this is sometimes used before the view is initialised.
        return getActivity().getApplicationContext();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState != null) {
            uiHelper.onRestoreSavedInstanceState(savedInstanceState);
        }
        uiHelper.registerToActiveServiceCalls();
    }

    protected Preference findPreference(int preferenceId) {
        return findPreference(getContext().getString(preferenceId));
    }

    protected boolean getBooleanPreferenceValue(String preferenceKey) {
        return getPreferenceManager().getSharedPreferences().getBoolean(preferenceKey, false);
    }

    protected String getPreferenceValue(String preferenceKey) {
        return getPreferenceManager().getSharedPreferences().getString(preferenceKey, "");
    }

    protected String getPreferenceValueOrNull(@StringRes int preferenceKey) {
        return getPreferenceManager().getSharedPreferences().getString(getString(preferenceKey), null);
    }

    protected int getPreferenceValueOrMinInt(@StringRes int preferenceKey) {

        try {
            return getPreferenceManager().getSharedPreferences().getInt(getString(preferenceKey), Integer.MIN_VALUE);
        } catch(ClassCastException e) {
            if(e.getMessage().equals("java.lang.String cannot be cast to java.lang.Integer")) {
                String intStr = getPreferenceManager().getSharedPreferences().getString(getString(preferenceKey), null);
                try {
                    return Integer.valueOf(intStr);
                } catch(NumberFormatException e2) {
                    return Integer.MIN_VALUE;
                }
            }
        }
        return Integer.MIN_VALUE;
    }


    @Override
    public void onResume() {
        super.onResume();
        uiHelper.handleAnyQueuedPiwigoMessages();
        uiHelper.showNextQueuedMessage();
    }

}
