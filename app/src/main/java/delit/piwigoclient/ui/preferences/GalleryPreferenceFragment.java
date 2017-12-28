package delit.piwigoclient.ui.preferences;

import android.Manifest;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;

import delit.piwigoclient.R;
import delit.piwigoclient.business.video.CacheUtils;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.ui.PicassoFactory;
import delit.piwigoclient.ui.common.MyPreferenceFragment;
import delit.piwigoclient.ui.common.NumberPickerPreference;
import delit.piwigoclient.ui.events.ThemeAlteredEvent;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;
import delit.piwigoclient.util.DisplayUtils;

/**
 * Created by gareth on 12/05/17.
 */

public class GalleryPreferenceFragment extends MyPreferenceFragment {

    private static final String TAG = "Gallery Settings";

    private transient Preference.OnPreferenceChangeListener useMasonryViewPreferenceListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            final Boolean val = (Boolean) value;
            boolean enabled = Boolean.TRUE.equals(val);
            getPreferenceManager().findPreference(preference.getContext().getString(R.string.preference_gallery_albums_preferredColumnsLandscape_key)).setEnabled(!enabled);
            getPreferenceManager().findPreference(preference.getContext().getString(R.string.preference_gallery_albums_preferredColumnsPortrait_key)).setEnabled(!enabled);
            getPreferenceManager().findPreference(preference.getContext().getString(R.string.preference_gallery_show_large_thumbnail_key)).setEnabled(!enabled);
            getPreferenceManager().findPreference(preference.getContext().getString(R.string.preference_gallery_show_image_name_key)).setEnabled(!enabled);
            return true;
        }
    };

    private Preference.OnPreferenceChangeListener videoCacheEnabledPrefListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(final Preference preference, Object value) {
            final Boolean val = (Boolean) value;
            if(Boolean.TRUE.equals(val)) {
                getUiHelper().runWithExtraPermissions(GalleryPreferenceFragment.this, Build.VERSION_CODES.BASE, Build.VERSION_CODES.KITKAT, Manifest.permission.WRITE_EXTERNAL_STORAGE, getString(R.string.alert_write_permission_needed_for_video_caching));
            } else {
                getPreferenceManager().findPreference(preference.getContext().getString(R.string.preference_video_cache_maxsize_mb_key)).setEnabled(false);
            }

            Preference videoCacheFlushButton = findPreference(R.string.preference_gallery_clearVideoCache_key);
            videoCacheFlushButton.setEnabled(Boolean.TRUE.equals(val));
            return true;
        }
    };

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

            if (getView() != null && preference.getKey().equals(preference.getContext().getString(R.string.preference_gallery_item_thumbnail_size_key))) {
                if (PiwigoSessionDetails.isLoggedInWithSessionDetails() && !PiwigoSessionDetails.getInstance().getAvailableImageSizes().contains(value)) {
                    getUiHelper().showOrQueueDialogMessage(R.string.alert_warning, getString(R.string.alert_warning_thumbnail_size_not_natively_supported_by_server));
                }
            } else if (getView() != null && preference.getKey().equals(preference.getContext().getString(R.string.preference_gallery_item_slideshow_image_size_key))) {
                if (PiwigoSessionDetails.isLoggedInWithSessionDetails() && !PiwigoSessionDetails.getInstance().getAvailableImageSizes().contains(value)) {
                    getUiHelper().showOrQueueDialogMessage(R.string.alert_warning, getString(R.string.alert_warning_slideshow_image_size_not_natively_supported_by_server));
                }
            }

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

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(PermissionsWantedResponse event) {
        if (getUiHelper().completePermissionsWantedRequest(event)) {
            if (event.areAllPermissionsGranted()) {
                getPreferenceManager().findPreference(getString(R.string.preference_video_cache_maxsize_mb_key)).setEnabled(true);
            } else {
                Preference videoCacheEnabledPref = findPreference(R.string.preference_video_cache_enabled_key);
                ((SwitchPreference) videoCacheEnabledPref).setChecked(false);
                getPreferenceManager().findPreference(getString(R.string.preference_video_cache_maxsize_mb_key)).setEnabled(false);
                getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_information_video_caching_disabled));
            }
        }
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

    /**
     * @param orientationId {@link Configuration.ORIENTATION_LANDSCAPE} or {@link Configuration.ORIENTATION_PORTRAIT}
     * @return default column count
     */
    @SuppressWarnings("JavadocReference")
    private int getDefaultAlbumsColumnCount(int orientationId) {
        float screenWidth = 0;
        if (getResources().getConfiguration().orientation == orientationId) {
            screenWidth = getScreenWidthInches();
        } else {
            screenWidth = getScreenHeightInches();
        }
        int columnsToShow = Math.max(1, Math.round(screenWidth - (screenWidth % 3))); // allow a minimum of 3 inch per column
        return Math.max(1,columnsToShow); // never allow less than one column by default.
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }


    @Override
    public View onCreateView(LayoutInflater paramLayoutInflater, ViewGroup paramViewGroup, Bundle paramBundle) {
        View v = super.onCreateView(paramLayoutInflater, paramViewGroup, paramBundle);
        addPreferencesFromResource(R.xml.pref_page_gallery);
        setHasOptionsMenu(true);

        // Bind the summaries of EditText/List/Dialog/Ringtone activity_preferences
        // to their values. When their values change, their summaries are
        // updated to reflect the pkg value, per the Android Design
        // guidelines.

        SwitchPreference themePref = (SwitchPreference) findPreference(R.string.preference_gallery_use_dark_mode_key);
        themePref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                EventBus.getDefault().post(new ThemeAlteredEvent());
                return false;
            }
        });

        NumberPickerPreference pref = (NumberPickerPreference) findPreference(R.string.preference_gallery_albums_preferredColumnsLandscape_key);
        int defaultVal = getDefaultAlbumsColumnCount(Configuration.ORIENTATION_LANDSCAPE);
        pref.updateDefaultValue(defaultVal);
        bindIntPreferenceSummaryToValue(pref);

        pref = (NumberPickerPreference) findPreference(R.string.preference_gallery_images_preferredColumnsLandscape_key);
        defaultVal = getDefaultImagesColumnCount(Configuration.ORIENTATION_LANDSCAPE);
        pref.updateDefaultValue(defaultVal);
        bindIntPreferenceSummaryToValue(pref);

        pref = (NumberPickerPreference) findPreference(R.string.preference_gallery_albums_preferredColumnsPortrait_key);
        defaultVal = getDefaultAlbumsColumnCount(Configuration.ORIENTATION_PORTRAIT);
        pref.updateDefaultValue(defaultVal);
        bindIntPreferenceSummaryToValue(pref);

        pref = (NumberPickerPreference) findPreference(R.string.preference_gallery_images_preferredColumnsPortrait_key);
        defaultVal = getDefaultImagesColumnCount(Configuration.ORIENTATION_PORTRAIT);
        pref.updateDefaultValue(defaultVal);
        bindIntPreferenceSummaryToValue(pref);

        pref = (NumberPickerPreference) findPreference(R.string.preference_gallery_detail_sheet_offset_key);
        int stdOffsetDp = getResources().getInteger(R.integer.preference_gallery_detail_sheet_offset_default);
        stdOffsetDp += DisplayUtils.pxToDp(getContext(), DisplayUtils.getNavBarHeight(getContext()));
        defaultVal = stdOffsetDp;
        pref.updateDefaultValue(defaultVal);
        bindIntPreferenceSummaryToValue(pref);

        bindIntPreferenceSummaryToValue(findPreference(R.string.preference_gallery_recentlyAlteredAgeMillis_key));
        bindStringPreferenceSummaryToValue(findPreference(R.string.preference_piwigo_playable_media_extensions_key));
        bindListPreferenceSummaryToValue(findPreference(R.string.preference_gallery_sortOrder_key));
        bindStringPreferenceSummaryToValue(findPreference(R.string.preference_gallery_item_thumbnail_size_key));
        bindStringPreferenceSummaryToValue(findPreference(R.string.preference_gallery_item_slideshow_image_size_key));
        bindIntPreferenceSummaryToValue(findPreference(R.string.preference_album_request_pagesize_key));

        Preference button = findPreference(R.string.preference_gallery_clearMemoryCache_key);
        button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                PicassoFactory.getInstance().clearPicassoCache(getContext().getApplicationContext());
                getUiHelper().showOrQueueDialogMessage(R.string.cacheCleared_title, getString(R.string.cacheCleared_message));
                return true;

            }
        });

        Preference videoCacheFlushButton = findPreference(R.string.preference_gallery_clearVideoCache_key);
        setVideoCacheButtonText(videoCacheFlushButton);
        videoCacheFlushButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                try {
                    CacheUtils.clearVideoCache(getContext());
                    getUiHelper().showOrQueueDialogMessage(R.string.cacheCleared_title, getString(R.string.videoCacheCleared_message));
                } catch(IOException e) {
                    getUiHelper().showOrQueueDialogMessage(R.string.cacheCleared_title, getString(R.string.videoCacheClearFailed_message));
                }
                setVideoCacheButtonText(preference);
                return true;

            }
        });
        return v;
    }

    private void setVideoCacheButtonText(Preference videoCacheFlushButton) {
        double cacheBytes = CacheUtils.getVideoCacheSize(getContext());
        long KB = 1024;
        long MB = KB * 1024;
        String spaceSuffix = " ";
        if(cacheBytes < KB) {
            spaceSuffix += String.format("(%1$.0f Bytes)", cacheBytes);
        } else if(cacheBytes < MB) {
            double kb = (cacheBytes / KB);
            spaceSuffix += String.format("(%1$.1f KB)", kb);
        } else {
            double mb = (cacheBytes / MB);
            spaceSuffix += String.format("(%1$.1f MB)", mb);
        }
        videoCacheFlushButton.setTitle(getString(R.string.preference_gallery_clearVideoCache_title) + spaceSuffix);
    }

    @Override
    public void onStart() {
        super.onStart();
        Preference videoCacheEnabledPref = findPreference(R.string.preference_video_cache_enabled_key);
        videoCacheEnabledPref.setOnPreferenceChangeListener(videoCacheEnabledPrefListener);
        videoCacheEnabledPrefListener.onPreferenceChange(videoCacheEnabledPref, getBooleanPreferenceValue(videoCacheEnabledPref.getKey()));
        bindIntPreferenceSummaryToValue(findPreference(R.string.preference_video_cache_maxsize_mb_key));

        Preference useMasonryViewPref = findPreference(R.string.preference_gallery_masonry_view_key);
        useMasonryViewPref.setOnPreferenceChangeListener(useMasonryViewPreferenceListener);
        useMasonryViewPreferenceListener.onPreferenceChange(useMasonryViewPref, getBooleanPreferenceValue(useMasonryViewPref.getKey()));

    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDetach() {
        EventBus.getDefault().unregister(this);
        super.onDetach();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
//        int id = item.getItemId();
//        if (id == android.R.id.home) {
//            startActivity(pkg Intent(getActivity(), SettingsActivity.class));
//            return true;
//        }
//        return super.onOptionsItemSelected(item);
        return true;
    }
}