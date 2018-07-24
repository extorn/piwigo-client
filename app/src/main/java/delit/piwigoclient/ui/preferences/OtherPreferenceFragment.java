package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.fragment.MyPreferenceFragment;
import delit.piwigoclient.ui.common.preference.NumberPickerPreference;

/**
 * Created by gareth on 12/05/17.
 */

public class OtherPreferenceFragment extends MyPreferenceFragment {

    private static final String TAG = "Other Settings";
    private View view;

    // Not needed from API v23 and above
    public Context getContext() {
        return getActivity().getApplicationContext();
    }

    private float getScreenWidthInches() {
        DisplayMetrics dm = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);
        return (float)dm.widthPixels / dm.xdpi;
    }

    private float getScreenHeightInches() {
        DisplayMetrics dm = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);
        return (float)dm.heightPixels / dm.xdpi;
    }

    /**
     * @param orientationId {@link Configuration.ORIENTATION_LANDSCAPE} or {@link Configuration.ORIENTATION_PORTRAIT}
     * @return default column count
     */
    @SuppressWarnings("JavadocReference")
    private int getDefaultFilesColumnCount(int orientationId) {
        float screenWidth;
        if (getResources().getConfiguration().orientation == orientationId) {
            screenWidth = getScreenWidthInches();
        } else {
            screenWidth = getScreenHeightInches();
        }
        int columnsToShow = (int) Math.round(screenWidth - (screenWidth % 0.75)); // allow a minimum of 0.75 inch per column
        return Math.max(1,columnsToShow); // never allow less than one column by default.
    }

    /**
     * @param orientationId {@link Configuration.ORIENTATION_LANDSCAPE} or {@link Configuration.ORIENTATION_PORTRAIT}
     * @return default column count
     */
    @SuppressWarnings("JavadocReference")
    private int getDefaultFoldersColumnCount(int orientationId) {
        float screenWidth;
        if (getResources().getConfiguration().orientation == orientationId) {
            screenWidth = getScreenWidthInches();
        } else {
            screenWidth = getScreenHeightInches();
        }
        int columnsToShow = (int) Math.round(screenWidth - (screenWidth % 0.1)); // allow a minimum of 1 inch per column
        return Math.max(1,columnsToShow); // never allow less than one column by default.
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }


    @Override
    public View onCreateView(LayoutInflater paramLayoutInflater, ViewGroup paramViewGroup, Bundle paramBundle) {
        if(view != null) {
            return view;
        }
        view = super.onCreateView(paramLayoutInflater, paramViewGroup, paramBundle);
        addPreferencesFromResource(R.xml.pref_page_other);
        setHasOptionsMenu(true);

        NumberPickerPreference pref = (NumberPickerPreference) findPreference(R.string.preference_data_file_selector_preferredFileColumnsLandscape_key);
        int defaultVal = getDefaultFilesColumnCount(Configuration.ORIENTATION_LANDSCAPE);
        pref.updateDefaultValue(defaultVal);

        pref = (NumberPickerPreference) findPreference(R.string.preference_data_file_selector_preferredFileColumnsPortrait_key);
        defaultVal = getDefaultFilesColumnCount(Configuration.ORIENTATION_PORTRAIT);
        pref.updateDefaultValue(defaultVal);

        pref = (NumberPickerPreference) findPreference(R.string.preference_data_file_selector_preferredFolderColumnsLandscape_key);
        defaultVal = getDefaultFoldersColumnCount(Configuration.ORIENTATION_LANDSCAPE);
        pref.updateDefaultValue(defaultVal);

        pref = (NumberPickerPreference) findPreference(R.string.preference_data_file_selector_preferredFolderColumnsPortrait_key);
        defaultVal = getDefaultFoldersColumnCount(Configuration.ORIENTATION_PORTRAIT);
        pref.updateDefaultValue(defaultVal);
        return view;
    }

}