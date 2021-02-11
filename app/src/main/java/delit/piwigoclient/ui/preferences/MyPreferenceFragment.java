package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.BoolRes;
import androidx.annotation.IntegerRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import java.util.Locale;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.preference.CustomEditTextPreference;
import delit.libs.ui.view.preference.CustomEditTextPreferenceDialogFragmentCompat;
import delit.libs.ui.view.preference.IntListPreference;
import delit.libs.ui.view.preference.MappedListPreferenceDialogFragmentCompat;
import delit.libs.ui.view.preference.NumberPickerPreference;
import delit.libs.ui.view.preference.NumberPickerPreferenceDialogFragmentCompat;
import delit.libs.ui.view.preference.StringListPreference;
import delit.libs.ui.view.recycler.MyFragmentRecyclerPagerAdapter;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AppPreferences;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.preference.ServerAlbumListPreference;
import delit.piwigoclient.ui.common.preference.ServerAlbumListPreferenceDialogFragmentCompat;
import delit.piwigoclient.ui.common.preference.ServerConnectionsListPreference;
import delit.piwigoclient.ui.common.preference.ServerConnectionsListPreferenceDialogFragmentCompat;
import delit.piwigoclient.ui.common.preference.UriPermissionsListPreference;
import delit.piwigoclient.ui.common.preference.UriPermissionsListPreferenceDialogFragmentCompat;

/**
 * Created by gareth on 26/05/17.
 */

public abstract class MyPreferenceFragment<F extends MyPreferenceFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>> extends PreferenceFragmentCompat implements MyFragmentRecyclerPagerAdapter.PagerItemView {
    private static final String STATE_PAGER_INDEX_POS = "pager_index_pos";
    protected static final int NO_PAGER_INDEX = -1;
    private static final String TAG = "MyPrefFrag";
    private FUIH uiHelper;
    private int pagerIndex;
    protected static final String DIALOG_FRAGMENT_TAG =
            "androidx.preference.PreferenceFragment.DIALOG";
    private boolean initialisedCoreComponents;

    public MyPreferenceFragment() {
        this(NO_PAGER_INDEX);
    }

    public MyPreferenceFragment(int pagerIndex) {
        this.pagerIndex = pagerIndex;
    }

    protected FUIH getUiHelper() {
        return uiHelper;
    }

    protected SharedPreferences getPrefs() {
        return getPreferenceManager().getSharedPreferences();
    }

    @Override
    public final View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = super.onCreateView(inflater, container, savedInstanceState);
        if(v != null && container != null) {
            v.setBackgroundColor(DisplayUtils.getColor(container.getContext(), R.attr.colorSurface));
        } else {
            Logging.log(Log.ERROR, TAG, "Unable to set background color, missing view or container");
        }
        initialiseCoreComponents(savedInstanceState, container);
        return v;
    }

    private void initialiseCoreComponents(Bundle savedInstanceState, View attachedView) {
        if(initialisedCoreComponents) {
            return;
        }
        initialisedCoreComponents = true;
        if (uiHelper == null) {
            uiHelper = (FUIH) new FragmentUIHelper<FUIH,F>((F) this, getPrefs(), getContext(), attachedView);
            BasicPiwigoResponseListener listener = buildPiwigoResponseListener(getContext());
            listener.withUiHelper(this, uiHelper);
            uiHelper.setPiwigoResponseListener(listener);
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        String language = AppPreferences.getDesiredLanguage(PreferenceManager.getDefaultSharedPreferences(context), requireContext());
        Locale newLocale = new Locale(language);
//        Locale.setDefault(newLocale);
        Context newContext = DisplayUtils.updateContext(getContext(), newLocale);
    }

    protected BasicPiwigoResponseListener<FUIH,F> buildPiwigoResponseListener(Context context) {
        return new BasicPiwigoResponseListener();
    }

    protected void callServer(String callDescription, AbstractPiwigoDirectResponseHandler handler) {
        uiHelper.addActiveServiceCall(callDescription, handler);
    }

    protected void callServer(@StringRes int callDescriptionResId, AbstractPiwigoDirectResponseHandler handler) {
        uiHelper.addActiveServiceCall(callDescriptionResId, handler);
    }

    @Override
    public void onPageSelected() {
        //TODO try force always hiding the keyboard
        if (uiHelper != null) {
            uiHelper.registerToActiveServiceCalls();
            uiHelper.handleAnyQueuedPiwigoMessages();
            uiHelper.showNextQueuedMessage();
        }
    }

    @Override
    public void onPageDeselected() {
        if (uiHelper != null) {
            uiHelper.deregisterFromActiveServiceCalls();
            uiHelper.closeAllDialogs();
        }
    }

    public void onPagerIndexChangedTo(int newPagerIndex) {
        // do nothing.
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if(uiHelper != null) {
            uiHelper.onSaveInstanceState(outState);
        }
        outState.putInt(STATE_PAGER_INDEX_POS, pagerIndex);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState); // this binds all the preferences
        if (savedInstanceState != null) {
            uiHelper.onRestoreSavedInstanceState(savedInstanceState);
            pagerIndex = savedInstanceState.getInt(STATE_PAGER_INDEX_POS);
        }
        uiHelper.registerToActiveServiceCalls();
    }

    // Not needed from API v23 and above
    @Override
    public Context getContext() {
        Context c = super.getContext();
        if (c == null) {
            // this is sometimes used before the view is initialised.
            return requireActivity();
        }
        return c;
    }

    @Override
    public int getPagerIndex() {
        return pagerIndex;
    }

    protected Preference findPreference(int preferenceId) {
        return findPreference(requireContext().getString(preferenceId));
    }

    protected boolean getBooleanPreferenceValue(@StringRes int preferenceKey, @BoolRes int defaultValue) {
        return getPreferenceManager().getSharedPreferences().getBoolean(getString(preferenceKey), getResources().getBoolean(defaultValue));
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
            Logging.recordException(e);
            Logging.recordException(e);
            if (e.getMessage().equals("java.lang.String cannot be cast to java.lang.Integer")) {
                String intStr = getPreferenceManager().getSharedPreferences().getString(getString(preferenceKey), null);
                try {
                    return Integer.parseInt(intStr);
                } catch (NumberFormatException e2) {
                    Logging.recordException(e);
                    Logging.recordException(e2);
                    return Integer.MIN_VALUE;
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    public static final class DummyPreferencesFragment extends MyPreferenceFragment {

        public DummyPreferencesFragment() {
        }

        public DummyPreferencesFragment(int pagerIndex) {
            super(pagerIndex);
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        }

        @Override
        public void onPagerIndexChangedTo(int newPagerIndex) {
        }
    }

    protected DialogFragment onDisplayCustomPreferenceDialog(Preference preference) {
        return null;
    }

    @Override
    public final void onDisplayPreferenceDialog(Preference preference) {
        if(preference instanceof PaidOnlyPreference) {
            getUiHelper().showDetailedMsg(R.string.alert_information, R.string.paid_feature_only);
            return;
        }
        DialogFragment f = onDisplayCustomPreferenceDialog(preference);
        if(f == null) {
            if (preference instanceof ServerConnectionsListPreference) {
                f = ServerConnectionsListPreferenceDialogFragmentCompat.newInstance(preference.getKey());
            } else if(preference instanceof UriPermissionsListPreference) {
                f = UriPermissionsListPreferenceDialogFragmentCompat.newInstance(preference.getKey());
            } else if (preference instanceof ServerAlbumListPreference) {
                f = ServerAlbumListPreferenceDialogFragmentCompat.newInstance(preference.getKey());
            } else if (preference instanceof IntListPreference) {
                f = MappedListPreferenceDialogFragmentCompat.newInstance(preference.getKey());
            } else if (preference instanceof StringListPreference) {
                f = MappedListPreferenceDialogFragmentCompat.newInstance(preference.getKey());
            } else if (preference instanceof EditableListPreference) {
                f = EditableListPreferenceDialogFragmentCompat.newInstance(preference.getKey());
            } else if (preference instanceof SecureEditTextPreference) {
                f = SecureCustomEditTextPreferenceDialogFragmentCompat.newInstance(preference.getKey());
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
            f.show(this.getParentFragmentManager(), DIALOG_FRAGMENT_TAG);
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }
}
