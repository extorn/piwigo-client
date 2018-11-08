package delit.piwigoclient.ui.common.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.BoolRes;
import androidx.annotation.IntegerRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import android.view.View;

import com.crashlytics.android.Crashlytics;

import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.preference.ClientCertificatePreference;
import delit.piwigoclient.ui.common.preference.CustomEditTextPreference;
import delit.piwigoclient.ui.common.preference.CustomEditTextPreferenceDialogFragmentCompat;
import delit.piwigoclient.ui.common.preference.EditableListPreference;
import delit.piwigoclient.ui.common.preference.EditableListPreferenceDialogFragmentCompat;
import delit.piwigoclient.ui.common.preference.IntListPreference;
import delit.piwigoclient.ui.common.preference.KeystorePreferenceDialogFragmentCompat;
import delit.piwigoclient.ui.common.preference.MappedListPreferenceDialogFragmentCompat;
import delit.piwigoclient.ui.common.preference.NumberPickerPreference;
import delit.piwigoclient.ui.common.preference.NumberPickerPreferenceDialogFragmentCompat;
import delit.piwigoclient.ui.common.preference.SecureEditTextPreference;
import delit.piwigoclient.ui.common.preference.TrustedCaCertificatesPreference;
import delit.piwigoclient.ui.preferences.ServerAlbumListPreference;
import delit.piwigoclient.ui.preferences.ServerAlbumListPreferenceDialogFragmentCompat;
import delit.piwigoclient.ui.preferences.ServerConnectionsListPreference;
import delit.piwigoclient.ui.preferences.ServerConnectionsListPreferenceDialogFragmentCompat;

/**
 * Created by gareth on 26/05/17.
 */

public abstract class MyPreferenceFragment extends PreferenceFragmentCompat {
    private UIHelper uiHelper;
    private Context c;
    protected static final String DIALOG_FRAGMENT_TAG =
            "androidx.preference.PreferenceFragment.DIALOG";

    protected UIHelper getUiHelper() {
        return uiHelper;
    }

    protected SharedPreferences getPrefs() {
        return getPreferenceManager().getSharedPreferences();
    }

    @Override
    public void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);
        if (uiHelper == null) {
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

    protected void callServer(@StringRes int callDescriptionResId, AbstractPiwigoDirectResponseHandler handler) {
        uiHelper.addActiveServiceCall(callDescriptionResId, handler.invokeAsync(getContext()));
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

    protected boolean getBooleanPreferenceValue(String preferenceKey, boolean defaultValue) {
        return getPreferenceManager().getSharedPreferences().getBoolean(preferenceKey, defaultValue);
    }

    protected boolean getBooleanPreferenceValue(String preferenceKey, @BoolRes int defaultKey) {
        return getPreferenceManager().getSharedPreferences().getBoolean(preferenceKey, getResources().getBoolean(defaultKey));
    }

    protected int getIntegerPreferenceValue(String preferenceKey, @IntegerRes int defaultKey) {
        return getPreferenceManager().getSharedPreferences().getInt(preferenceKey, getResources().getInteger(defaultKey));
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
        } catch (ClassCastException e) {
            Crashlytics.logException(e);
            Crashlytics.logException(e);
            if (e.getMessage().equals("java.lang.String cannot be cast to java.lang.Integer")) {
                String intStr = getPreferenceManager().getSharedPreferences().getString(getString(preferenceKey), null);
                try {
                    return Integer.valueOf(intStr);
                } catch (NumberFormatException e2) {
                    Crashlytics.logException(e);
                    Crashlytics.logException(e2);
                    return Integer.MIN_VALUE;
                }
            }
        }
        return Integer.MIN_VALUE;
    }


    @Override
    public void onResume() {
        super.onResume();
//        TODO try force allways hiding the keyboard
        uiHelper.handleAnyQueuedPiwigoMessages();
        uiHelper.showNextQueuedMessage();
    }

    protected DialogFragment onDisplayCustomPreferenceDialog(Preference preference) {
        return null;
    }

    @Override
    public final void onDisplayPreferenceDialog(Preference preference) {
        DialogFragment f = onDisplayCustomPreferenceDialog(preference);
        if(f == null) {
            if (preference instanceof ServerConnectionsListPreference) {
                f = ServerConnectionsListPreferenceDialogFragmentCompat.newInstance(preference.getKey());
            } else if (preference instanceof ServerAlbumListPreference) {
                f = ServerAlbumListPreferenceDialogFragmentCompat.newInstance(preference.getKey());
            } else if (preference instanceof IntListPreference) {
                f = MappedListPreferenceDialogFragmentCompat.newInstance(preference.getKey());
            } else if (preference instanceof EditableListPreference) {
                f = EditableListPreferenceDialogFragmentCompat.newInstance(preference.getKey());
            } else if (preference instanceof SecureEditTextPreference) {
                f = CustomEditTextPreferenceDialogFragmentCompat.newInstance(preference.getKey());
            } else if (preference instanceof CustomEditTextPreference) {
                f = CustomEditTextPreferenceDialogFragmentCompat.newInstance(preference.getKey());
            } else if (preference instanceof TrustedCaCertificatesPreference) {
                f = KeystorePreferenceDialogFragmentCompat.newInstance(preference.getKey());
            } else if (preference instanceof ClientCertificatePreference) {
                f = KeystorePreferenceDialogFragmentCompat.newInstance(preference.getKey());
            }  else if (preference instanceof NumberPickerPreference) {
                f = NumberPickerPreferenceDialogFragmentCompat.newInstance(preference.getKey());
            }
        }
        if(f != null) {
            f.setTargetFragment(this, 0);
            f.show(this.getFragmentManager(), DIALOG_FRAGMENT_TAG);
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }
}
