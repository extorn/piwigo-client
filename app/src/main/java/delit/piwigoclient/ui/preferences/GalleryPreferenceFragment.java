package delit.piwigoclient.ui.preferences;

import android.Manifest;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.view.MenuItem;
import android.view.View;

import com.crashlytics.android.Crashlytics;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;

import delit.piwigoclient.R;
import delit.piwigoclient.business.AlbumViewPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.business.video.CacheUtils;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.ui.PicassoFactory;
import delit.piwigoclient.ui.common.fragment.MyPreferenceFragment;
import delit.piwigoclient.ui.common.preference.NumberPickerPreference;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;
import delit.piwigoclient.util.IOUtils;

/**
 * Created by gareth on 12/05/17.
 */

public class GalleryPreferenceFragment extends MyPreferenceFragment {

    private static final String TAG = "Gallery Settings";
    private final Preference.OnPreferenceChangeListener videoCacheEnabledPrefListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(final Preference preference, Object value) {
            final Boolean val = (Boolean) value;
            if (Boolean.TRUE.equals(val)) {
                getUiHelper().runWithExtraPermissions(GalleryPreferenceFragment.this, Build.VERSION_CODES.BASE, Build.VERSION_CODES.KITKAT, Manifest.permission.WRITE_EXTERNAL_STORAGE, getString(R.string.alert_write_permission_needed_for_video_caching));
            } else {
                getPreferenceManager().findPreference(preference.getContext().getString(R.string.preference_video_cache_maxsize_mb_key)).setEnabled(false);
                getUiHelper().showToast(R.string.video_caching_disabled_not_recommended);
            }
            return true;
        }
    };
    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its pkg value.
     */
    private final Preference.OnPreferenceChangeListener selectedImageSizeNativeSupportCheckListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();
            PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());

            if (getView() != null) {
                if (preference.getKey().equals(preference.getContext().getString(R.string.preference_gallery_item_thumbnail_size_key))
                        || preference.getKey().equals(preference.getContext().getString(R.string.preference_gallery_album_thumbnail_size_key))) {
                    if (sessionDetails != null && sessionDetails.isLoggedInWithFullSessionDetails() && !sessionDetails.getAvailableImageSizes().contains(stringValue)) {
                        getUiHelper().showOrQueueDialogMessage(R.string.alert_warning, getString(R.string.alert_warning_thumbnail_size_not_natively_supported_by_server));
                    }
                } else if (preference.getKey().equals(preference.getContext().getString(R.string.preference_gallery_item_slideshow_image_size_key))) {
                    if (sessionDetails != null && sessionDetails.isLoggedInWithFullSessionDetails() && !sessionDetails.getAvailableImageSizes().contains(stringValue)) {
                        getUiHelper().showOrQueueDialogMessage(R.string.alert_warning, getString(R.string.alert_warning_slideshow_image_size_not_natively_supported_by_server));
                    }
                }
            }
            return true;
        }
    };
    private View view;

    // Not needed from API v23 and above
    public Context getContext() {
        return getActivity().getApplicationContext();
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

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_page_gallery, rootKey);
        setHasOptionsMenu(true);

        // Bind the summaries of EditText/List/Dialog/Ringtone activity_preferences
        // to their values. When their values change, their summaries are
        // updated to reflect the pkg value, per the Android Design
        // guidelines.

        NumberPickerPreference pref = (NumberPickerPreference) findPreference(R.string.preference_gallery_albums_preferredColumnsLandscape_key);
        int defaultVal = AlbumViewPreferences.getDefaultAlbumColumnCount(getActivity(), Configuration.ORIENTATION_LANDSCAPE);
        pref.updateDefaultValue(defaultVal);

        pref = (NumberPickerPreference) findPreference(R.string.preference_gallery_images_preferredColumnsLandscape_key);
        defaultVal = AlbumViewPreferences.getDefaultImagesColumnCount(getActivity(), Configuration.ORIENTATION_LANDSCAPE);
        pref.updateDefaultValue(defaultVal);

        pref = (NumberPickerPreference) findPreference(R.string.preference_gallery_albums_preferredColumnsPortrait_key);
        defaultVal = AlbumViewPreferences.getDefaultAlbumColumnCount(getActivity(), Configuration.ORIENTATION_PORTRAIT);
        pref.updateDefaultValue(defaultVal);

        pref = (NumberPickerPreference) findPreference(R.string.preference_gallery_images_preferredColumnsPortrait_key);
        defaultVal = AlbumViewPreferences.getDefaultImagesColumnCount(getActivity(), Configuration.ORIENTATION_PORTRAIT);
        pref.updateDefaultValue(defaultVal);

        findPreference(R.string.preference_gallery_album_thumbnail_size_key).setOnPreferenceChangeListener(selectedImageSizeNativeSupportCheckListener);
        findPreference(R.string.preference_gallery_item_thumbnail_size_key).setOnPreferenceChangeListener(selectedImageSizeNativeSupportCheckListener);
        findPreference(R.string.preference_gallery_item_slideshow_image_size_key).setOnPreferenceChangeListener(selectedImageSizeNativeSupportCheckListener);

        Preference button = findPreference(R.string.preference_gallery_clearMemoryCache_key);
        button.setTitle(suffixCacheSize(getString(R.string.preference_gallery_clearMemoryCache_title), PicassoFactory.getInstance().getCacheSizeBytes()));
        button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                PicassoFactory.getInstance().clearPicassoCache(getContext().getApplicationContext(), true);
                getUiHelper().showOrQueueDialogMessage(R.string.cacheCleared_title, getString(R.string.cacheCleared_message));
                preference.setTitle(suffixCacheSize(getString(R.string.preference_gallery_clearMemoryCache_title), PicassoFactory.getInstance().getCacheSizeBytes()));
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
                } catch (IOException e) {
                    Crashlytics.logException(e);
                    getUiHelper().showOrQueueDialogMessage(R.string.cacheCleared_title, getString(R.string.videoCacheClearFailed_message));
                }
                setVideoCacheButtonText(preference);
                return true;

            }
        });
    }

    private String suffixCacheSize(String basicString, long cacheSizeBytes) {
        return basicString + '(' + IOUtils.toNormalizedText(cacheSizeBytes) + ')';
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
        videoCacheEnabledPrefListener.onPreferenceChange(videoCacheEnabledPref, getBooleanPreferenceValue(videoCacheEnabledPref.getKey(), R.bool.preference_video_cache_enabled_default));
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