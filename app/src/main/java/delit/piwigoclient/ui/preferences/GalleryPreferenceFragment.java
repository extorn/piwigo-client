package delit.piwigoclient.ui.preferences;

import android.Manifest;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
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
import java.util.Locale;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.business.video.CacheUtils;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.ui.PicassoFactory;
import delit.piwigoclient.ui.common.fragment.MyPreferenceFragment;
import delit.piwigoclient.ui.common.preference.NumberPickerPreference;
import delit.piwigoclient.ui.events.ThemeAlteredEvent;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;
import delit.piwigoclient.util.DisplayUtils;

/**
 * Created by gareth on 12/05/17.
 */

public class GalleryPreferenceFragment extends MyPreferenceFragment {

    private static final String TAG = "Gallery Settings";
    private View view;

    private final Preference.OnPreferenceChangeListener videoCacheEnabledPrefListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(final Preference preference, Object value) {
            final Boolean val = (Boolean) value;
            if(Boolean.TRUE.equals(val)) {
                getUiHelper().runWithExtraPermissions(GalleryPreferenceFragment.this, Build.VERSION_CODES.BASE, Build.VERSION_CODES.KITKAT, Manifest.permission.WRITE_EXTERNAL_STORAGE, getString(R.string.alert_write_permission_needed_for_video_caching));
            } else {
                getPreferenceManager().findPreference(preference.getContext().getString(R.string.preference_video_cache_maxsize_mb_key)).setEnabled(false);
            }
            return true;
        }
    };

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its pkg value.
     */
    private final Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();
            PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());

            if (getView() != null && preference.getKey().equals(preference.getContext().getString(R.string.preference_gallery_item_thumbnail_size_key))) {
                if (sessionDetails != null && sessionDetails.isLoggedInWithFullSessionDetails() && !sessionDetails.getAvailableImageSizes().contains(stringValue)) {
                    getUiHelper().showOrQueueDialogMessage(R.string.alert_warning, getString(R.string.alert_warning_thumbnail_size_not_natively_supported_by_server));
                }
            } else if (getView() != null && preference.getKey().equals(preference.getContext().getString(R.string.preference_gallery_item_slideshow_image_size_key))) {
                if (sessionDetails != null && sessionDetails.isLoggedInWithFullSessionDetails() && !sessionDetails.getAvailableImageSizes().contains(stringValue)) {
                    getUiHelper().showOrQueueDialogMessage(R.string.alert_warning, getString(R.string.alert_warning_slideshow_image_size_not_natively_supported_by_server));
                }
            }
            return true;
        }
    };

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
        float screenWidth;
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
        float screenWidth ;
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
        if(view != null) {
            return view;
        }
        view = super.onCreateView(paramLayoutInflater, paramViewGroup, paramBundle);
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

        pref = (NumberPickerPreference) findPreference(R.string.preference_gallery_images_preferredColumnsLandscape_key);
        defaultVal = getDefaultImagesColumnCount(Configuration.ORIENTATION_LANDSCAPE);
        pref.updateDefaultValue(defaultVal);

        pref = (NumberPickerPreference) findPreference(R.string.preference_gallery_albums_preferredColumnsPortrait_key);
        defaultVal = getDefaultAlbumsColumnCount(Configuration.ORIENTATION_PORTRAIT);
        pref.updateDefaultValue(defaultVal);

        pref = (NumberPickerPreference) findPreference(R.string.preference_gallery_images_preferredColumnsPortrait_key);
        defaultVal = getDefaultImagesColumnCount(Configuration.ORIENTATION_PORTRAIT);
        pref.updateDefaultValue(defaultVal);

        findPreference(R.string.preference_gallery_item_thumbnail_size_key).setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
        findPreference(R.string.preference_gallery_item_slideshow_image_size_key).setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        Preference button = findPreference(R.string.preference_gallery_clearMemoryCache_key);
        button.setTitle(suffixCacheSize(getString(R.string.preference_gallery_clearMemoryCache_title), PicassoFactory.getInstance().getCacheSizeBytes()));
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
        return view;
    }

    private String suffixCacheSize(String basicString, long cacheSizeBytes) {
        double cacheBytes = cacheSizeBytes;
        long KB = 1024;
        long MB = KB * 1024;
        String spaceSuffix = " ";
        if(cacheBytes < KB) {
            spaceSuffix += String.format(Locale.getDefault(), "(%1$.0f Bytes)", cacheBytes);
        } else if(cacheBytes < MB) {
            double kb = (cacheBytes / KB);
            spaceSuffix += String.format(Locale.getDefault(), "(%1$.1f KB)", kb);
        } else {
            double mb = (cacheBytes / MB);
            spaceSuffix += String.format(Locale.getDefault(), "(%1$.1f MB)", mb);
        }
        return basicString + spaceSuffix;
    }

    private void setVideoCacheButtonText(Preference videoCacheFlushButton) {
        String newTitle = suffixCacheSize(getString(R.string.preference_gallery_clearVideoCache_title), CacheUtils.getVideoCacheSize(getContext()));
        videoCacheFlushButton.setTitle(newTitle);
    }

    @Override
    public void onStart() {
        super.onStart();
        Preference videoCacheEnabledPref = findPreference(R.string.preference_video_cache_enabled_key);
        videoCacheEnabledPref.setOnPreferenceChangeListener(videoCacheEnabledPrefListener);
        videoCacheEnabledPrefListener.onPreferenceChange(videoCacheEnabledPref, getBooleanPreferenceValue(videoCacheEnabledPref.getKey()));
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