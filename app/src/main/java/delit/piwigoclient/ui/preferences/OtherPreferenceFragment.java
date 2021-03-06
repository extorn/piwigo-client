package delit.piwigoclient.ui.preferences;

import android.content.res.Configuration;
import android.os.Bundle;

import delit.libs.ui.view.preference.NumberPickerPreference;
import delit.piwigoclient.R;
import delit.piwigoclient.business.OtherPreferences;
import delit.piwigoclient.ui.common.FragmentUIHelper;

/**
 * Created by gareth on 12/05/17.
 */

public class OtherPreferenceFragment<F extends OtherPreferenceFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends MyPreferenceFragment<F,FUIH> {

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