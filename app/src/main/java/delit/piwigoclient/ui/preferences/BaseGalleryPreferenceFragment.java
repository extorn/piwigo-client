package delit.piwigoclient.ui.preferences;

import android.content.res.Configuration;
import android.os.Bundle;

import androidx.preference.Preference;

import delit.libs.ui.view.preference.NumberPickerPreference;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AlbumViewPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;

/**
 * Created by gareth on 12/05/17.
 */

public class BaseGalleryPreferenceFragment extends MyPreferenceFragment<BaseGalleryPreferenceFragment> {

    private static final String TAG = "Gallery Settings";

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

    public BaseGalleryPreferenceFragment() {
    }

    public BaseGalleryPreferenceFragment(int pagerIndex) {
        super(pagerIndex);
    }



    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Load the preferences from an XML resource
//        preferencesKey = rootKey;
        buildPreferencesViewAndInitialise(rootKey);
    }

    protected void buildPreferencesViewAndInitialise(String rootKey) {
        setPreferencesFromResource(R.xml.pref_page_gallery, rootKey);
        setHasOptionsMenu(true);

        // Bind the summaries of EditText/List/Dialog/Ringtone activity_preferences
        // to their values. When their values change, their summaries are
        // updated to reflect the pkg value, per the Android Design
        // guidelines.

        NumberPickerPreference pref = (NumberPickerPreference) findPreference(R.string.preference_gallery_albums_preferredColumnsLandscape_key);
        int defaultVal = AlbumViewPreferences.getDefaultAlbumColumnCount(requireActivity(), Configuration.ORIENTATION_LANDSCAPE);
        pref.updateDefaultValue(defaultVal);

        pref = (NumberPickerPreference) findPreference(R.string.preference_gallery_images_preferredColumnsLandscape_key);
        defaultVal = AlbumViewPreferences.getDefaultImagesColumnCount(requireActivity(), Configuration.ORIENTATION_LANDSCAPE);
        pref.updateDefaultValue(defaultVal);

        pref = (NumberPickerPreference) findPreference(R.string.preference_gallery_albums_preferredColumnsPortrait_key);
        defaultVal = AlbumViewPreferences.getDefaultAlbumColumnCount(requireActivity(), Configuration.ORIENTATION_PORTRAIT);
        pref.updateDefaultValue(defaultVal);

        pref = (NumberPickerPreference) findPreference(R.string.preference_gallery_images_preferredColumnsPortrait_key);
        defaultVal = AlbumViewPreferences.getDefaultImagesColumnCount(requireActivity(), Configuration.ORIENTATION_PORTRAIT);
        pref.updateDefaultValue(defaultVal);

        findPreference(R.string.preference_gallery_album_thumbnail_size_key).setOnPreferenceChangeListener(selectedImageSizeNativeSupportCheckListener);
        findPreference(R.string.preference_gallery_item_thumbnail_size_key).setOnPreferenceChangeListener(selectedImageSizeNativeSupportCheckListener);
        findPreference(R.string.preference_gallery_item_slideshow_image_size_key).setOnPreferenceChangeListener(selectedImageSizeNativeSupportCheckListener);

    }
}