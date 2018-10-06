package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;

import delit.piwigoclient.R;
import delit.piwigoclient.business.OtherPreferences;
import delit.piwigoclient.ui.common.fragment.MyPreferenceFragment;
import delit.piwigoclient.ui.common.preference.NumberPickerPreference;

/**
 * Created by gareth on 12/05/17.
 */

public class OtherPreferenceFragment extends MyPreferenceFragment {

    // Not needed from API v23 and above
    public Context getContext() {
        return getActivity().getApplicationContext();
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