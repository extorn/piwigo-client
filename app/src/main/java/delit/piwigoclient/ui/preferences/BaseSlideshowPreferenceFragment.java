package delit.piwigoclient.ui.preferences;

import android.content.res.Configuration;
import android.os.Bundle;

import androidx.preference.Preference;

import delit.libs.ui.view.preference.NumberPickerPreference;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AlbumViewPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.ui.common.FragmentUIHelper;

/**
 * Created by gareth on 12/05/17.
 */

public class BaseSlideshowPreferenceFragment<F extends BaseSlideshowPreferenceFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends MyPreferenceFragment<F,FUIH> {

    private static final String TAG = "Slideshow Settings";

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its pkg value.
     */
    private final Preference.OnPreferenceChangeListener selectedImageSizeNativeSupportCheckListener = (preference, value) -> {
        String stringValue = value.toString();
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());

        if (getView() != null) {
            if (preference.getKey().equals(preference.getContext().getString(R.string.preference_gallery_item_thumbnail_size_key))
                    || preference.getKey().equals(preference.getContext().getString(R.string.preference_gallery_album_thumbnail_size_key))) {
                if (sessionDetails != null && sessionDetails.isLoggedInWithFullSessionDetails() && !sessionDetails.getAvailableImageSizes().contains(stringValue)) {
                    getUiHelper().showDetailedMsg(R.string.alert_warning, getString(R.string.alert_warning_thumbnail_size_not_natively_supported_by_server));
                }
            } else if (preference.getKey().equals(preference.getContext().getString(R.string.preference_gallery_item_slideshow_image_size_key))) {
                if (sessionDetails != null && sessionDetails.isLoggedInWithFullSessionDetails() && !sessionDetails.getAvailableImageSizes().contains(stringValue)) {
                    getUiHelper().showDetailedMsg(R.string.alert_warning, getString(R.string.alert_warning_slideshow_image_size_not_natively_supported_by_server));
                }
            }
        }
        return true;
    };

    public BaseSlideshowPreferenceFragment() {
    }

    public BaseSlideshowPreferenceFragment(int pagerIndex) {
        super(pagerIndex);
    }



    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Load the preferences from an XML resource
//        preferencesKey = rootKey;
        buildPreferencesViewAndInitialise(rootKey);
    }

    protected void buildPreferencesViewAndInitialise(String rootKey) {
        setPreferencesFromResource(R.xml.pref_page_slideshow, rootKey);
        setHasOptionsMenu(true);

        findPreference(R.string.preference_gallery_item_slideshow_image_size_key).setOnPreferenceChangeListener(selectedImageSizeNativeSupportCheckListener);

    }
}