package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.MyPreferenceFragment;

/**
 * Created by gareth on 12/05/17.
 */

public class UploadPreferenceFragment extends MyPreferenceFragment {

    private static final String TAG = "Upload Settings";

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
    private int getDefaultImagesColumnCount(int orientationId) {
        float screenWidth = 0;
        if (getResources().getConfiguration().orientation == orientationId) {
            screenWidth = getScreenWidthInches();
        } else {
            screenWidth = getScreenHeightInches();
        }
        int columnsToShow = Math.max(1, Math.round(screenWidth - (screenWidth % 1))); // allow a minimum of 1 inch per column
        return Math.max(1,columnsToShow); // never allow less than one column by default.
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }


    @Override
    public View onCreateView(LayoutInflater paramLayoutInflater, ViewGroup paramViewGroup, Bundle paramBundle) {
        View v = super.onCreateView(paramLayoutInflater, paramViewGroup, paramBundle);
        addPreferencesFromResource(R.xml.pref_page_upload);
        setHasOptionsMenu(true);

        NumberPickerPreference pref = (NumberPickerPreference) findPreference(R.string.preference_data_upload_preferredColumnsLandscape_key);
        int defaultVal = getDefaultImagesColumnCount(Configuration.ORIENTATION_LANDSCAPE);
        pref.updateDefaultValue(defaultVal);

        pref = (NumberPickerPreference) findPreference(R.string.preference_data_upload_preferredColumnsPortrait_key);
        defaultVal = getDefaultImagesColumnCount(Configuration.ORIENTATION_PORTRAIT);
        pref.updateDefaultValue(defaultVal);
        return v;
    }

}