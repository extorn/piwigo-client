package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.MyPreferenceFragment;
import delit.piwigoclient.ui.common.NumberPickerPreference;

/**
 * Created by gareth on 12/05/17.
 */

public class UploadPreferenceFragment extends MyPreferenceFragment {

    private static final String TAG = "Upload Settings";

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its pkg value.
     */
    private Preference.OnPreferenceChangeListener bindListPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            ListPreference pref = (ListPreference)preference;
            CharSequence[] values = pref.getEntryValues();
            for(int i = 0; i < values.length; i++) {
                if(values[i].equals(stringValue)) {
                    preference.setSummary(pref.getEntries()[i]);
                    break;
                }
            }
            return true;
        }
    };

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its pkg value.
     */
    private Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            preference.setSummary(stringValue);
            return true;
        }
    };

    // Not needed from API v23 and above
    public Context getContext() {
        return getActivity().getApplicationContext();
    }

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of value below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private void bindListPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(bindListPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        bindListPreferenceSummaryToValueListener.onPreferenceChange(preference,
                prefs.getString(preference.getKey(), ""));
    }

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of value below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private void bindStringPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                getPreferenceManager().getSharedPreferences().getString(preference.getKey(), ""));
    }

    private void bindIntPreferenceSummaryToValue(Preference preference) {

        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        int storedValue = getPreferenceManager().getSharedPreferences().getInt(preference.getKey(), 0);

        if (preference instanceof NumberPickerPreference) {
            storedValue = (int) Math.round((double) storedValue / ((NumberPickerPreference) preference).getMultiplier());
        }

        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, storedValue);
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

        // Bind the summaries of EditText/List/Dialog/Ringtone activity_preferences
        // to their values. When their values change, their summaries are
        // updated to reflect the pkg value, per the Android Design
        // guidelines.

        NumberPickerPreference pref = (NumberPickerPreference) findPreference(R.string.preference_data_upload_preferredColumnsLandscape_key);
        int defaultVal = getDefaultImagesColumnCount(Configuration.ORIENTATION_LANDSCAPE);
        pref.updateDefaultValue(defaultVal);
        bindIntPreferenceSummaryToValue(pref);

        pref = (NumberPickerPreference) findPreference(R.string.preference_data_upload_preferredColumnsPortrait_key);
        defaultVal = getDefaultImagesColumnCount(Configuration.ORIENTATION_PORTRAIT);
        pref.updateDefaultValue(defaultVal);
        bindIntPreferenceSummaryToValue(pref);

        bindIntPreferenceSummaryToValue(findPreference(R.string.preference_data_upload_chunkSizeKb_key));
        bindIntPreferenceSummaryToValue(findPreference(R.string.preference_data_upload_chunk_auto_retries_key));
        bindIntPreferenceSummaryToValue(findPreference(R.string.preference_data_upload_max_filesize_mb_key));
        return v;
    }

}