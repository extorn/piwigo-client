package delit.piwigoclient.ui.preferences;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.fragment.MyPreferenceFragment;
import delit.libs.util.CollectionUtils;
import delit.libs.util.ProjectUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AppPreferences;

public class AppPreferenceFragment extends MyPreferenceFragment<AppPreferenceFragment> {

    public AppPreferenceFragment() {
    }

    public AppPreferenceFragment(int pagerIndex) {
        super(pagerIndex);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_page_app, rootKey);
        setHasOptionsMenu(true);

        Preference alwaysShowNavButtonsPref = findPreference(R.string.preference_app_always_show_nav_buttons_key);

        alwaysShowNavButtonsPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Boolean newVal = (Boolean) newValue;
                FragmentActivity fragmentActivity = getUiHelper().getParent().getActivity();
                DisplayUtils.setUiFlags(fragmentActivity, newVal, AppPreferences.isAlwaysShowStatusBar(getUiHelper().getPrefs(), getUiHelper().getContext()));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                    getUiHelper().getParent().requireView().requestApplyInsets();
                }
                return true;
            }
        });
        Preference alwaysShowStatusBarPref = findPreference(R.string.preference_app_always_show_status_bar_key);
        alwaysShowStatusBarPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Boolean newVal = (Boolean) newValue;
                FragmentActivity fragmentActivity = getUiHelper().getParent().getActivity();
                DisplayUtils.setUiFlags(fragmentActivity, AppPreferences.isAlwaysShowNavButtons(getUiHelper().getPrefs(), getUiHelper().getContext()), newVal);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                    getUiHelper().getParent().requireView().requestApplyInsets();
                }
                return true;
            }
        });
        ListPreference desiredLanguagePref = (ListPreference) findPreference(R.string.preference_app_desired_language_key);
        List<Locale> localeOptions = ProjectUtils.listLocalesWithUniqueTranslationOf(getContext(), R.string.album_create_failed);
        List<String> entries = new ArrayList<>(localeOptions.size());
        List<String> values = new ArrayList<>(localeOptions.size());
        for (Locale l : localeOptions) {
            entries.add(l.getDisplayName(l));
            values.add(l.toString());
        }
        desiredLanguagePref.setEntries(CollectionUtils.asStringArray(entries));
        desiredLanguagePref.setEntryValues(CollectionUtils.asStringArray(values));
        desiredLanguagePref.setDefaultValue(values.get(0));
        if(desiredLanguagePref.getValue() == null) {
            desiredLanguagePref.setValue(values.get(0));
        }
        desiredLanguagePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Locale newVal = new Locale((String) newValue);

                DisplayUtils.updateContext(getContext(), newVal);
//                getActivity().recreate(); (don't do this as the preference may not be saved)

                DisplayUtils.postOnUiThread(() -> {
                    Activity activity = getActivity();
                    if (activity != null) {
                        activity.recreate();
                    }
                });
                return true;
            }
        });

        Preference pref = findPreference(R.string.preference_app_clear_list_of_user_hints_shown_key);
        pref.setOnPreferenceClickListener(preference -> {
            AppPreferences.clearListOfShownHints(getPrefs(), preference.getContext());
            getUiHelper().showDetailedShortMsg(R.string.alert_information, R.string.alert_all_user_hints_will_be_shown_again);
            return true;
        });
    }
}
