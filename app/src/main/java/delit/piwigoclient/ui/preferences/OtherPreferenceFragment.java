package delit.piwigoclient.ui.preferences;

import android.content.res.Configuration;
import android.os.Bundle;

import androidx.preference.Preference;

import delit.libs.ui.view.fragment.MyPreferenceFragment;
import delit.libs.ui.view.preference.NumberPickerPreference;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AppPreferences;
import delit.piwigoclient.business.OtherPreferences;

/**
 * Created by gareth on 12/05/17.
 */

public class OtherPreferenceFragment extends MyPreferenceFragment<OtherPreferenceFragment> {

    public OtherPreferenceFragment() {
    }

    public OtherPreferenceFragment(int pagerIndex) {
        super(pagerIndex);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_page_other, rootKey);
        setHasOptionsMenu(true);

        NumberPickerPreference pref = (NumberPickerPreference) findPreference(R.string.preference_data_file_selector_preferredFileColumnsLandscape_key);
        int defaultVal = OtherPreferences.getDefaultFilesColumnCount(getActivity(), Configuration.ORIENTATION_LANDSCAPE);
        pref.updateDefaultValue(defaultVal);

        pref = (NumberPickerPreference) findPreference(R.string.preference_data_file_selector_preferredFileColumnsPortrait_key);
        defaultVal = OtherPreferences.getDefaultFilesColumnCount(getActivity(), Configuration.ORIENTATION_PORTRAIT);
        pref.updateDefaultValue(defaultVal);

        pref = (NumberPickerPreference) findPreference(R.string.preference_data_file_selector_preferredFolderColumnsLandscape_key);
        defaultVal = OtherPreferences.getDefaultFoldersColumnCount(getActivity(), Configuration.ORIENTATION_LANDSCAPE);
        pref.updateDefaultValue(defaultVal);

        pref = (NumberPickerPreference) findPreference(R.string.preference_data_file_selector_preferredFolderColumnsPortrait_key);
        defaultVal = OtherPreferences.getDefaultFoldersColumnCount(getActivity(), Configuration.ORIENTATION_PORTRAIT);
        pref.updateDefaultValue(defaultVal);


    }
}