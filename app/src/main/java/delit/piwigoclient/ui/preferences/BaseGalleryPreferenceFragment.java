package delit.piwigoclient.ui.preferences;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.fragment.app.FragmentActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

import com.crashlytics.android.Crashlytics;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.fragment.MyPreferenceFragment;
import delit.libs.ui.view.preference.EditableListPreference;
import delit.libs.ui.view.preference.NumberPickerPreference;
import delit.libs.util.CollectionUtils;
import delit.libs.util.IOUtils;
import delit.libs.util.ProjectUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AlbumViewPreferences;
import delit.piwigoclient.business.AppPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.business.video.CacheUtils;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.ui.PicassoFactory;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;

/**
 * Created by gareth on 12/05/17.
 */

public class BaseGalleryPreferenceFragment extends MyPreferenceFragment<BaseGalleryPreferenceFragment> {

    private static final String TAG = "Gallery Settings";
    private final Preference.OnPreferenceChangeListener videoCacheEnabledPrefListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(final Preference preference, Object value) {
            final Boolean val = (Boolean) value;
            if (Boolean.TRUE.equals(val)) {
                getUiHelper().runWithExtraPermissions(BaseGalleryPreferenceFragment.this, Build.VERSION_CODES.BASE, Build.VERSION_CODES.KITKAT, Manifest.permission.WRITE_EXTERNAL_STORAGE, getString(R.string.alert_write_permission_needed_for_video_caching));
            } else {
                getPreferenceManager().findPreference(preference.getContext().getString(R.string.preference_video_cache_maxsize_mb_key)).setEnabled(false);
                getUiHelper().showDetailedShortMsg(R.string.alert_warning, getString(R.string.video_caching_disabled_not_recommended));
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
                        getUiHelper().showDetailedMsg(R.string.alert_warning, getString(R.string.alert_warning_thumbnail_size_not_natively_supported_by_server));
                    }
                } else if (preference.getKey().equals(preference.getContext().getString(R.string.preference_gallery_item_slideshow_image_size_key))) {
                    if (sessionDetails != null && sessionDetails.isLoggedInWithFullSessionDetails() && !sessionDetails.getAvailableImageSizes().contains(stringValue)) {
                        getUiHelper().showDetailedMsg(R.string.alert_warning, getString(R.string.alert_warning_slideshow_image_size_not_natively_supported_by_server));
                    }
                }
            }
            return true;
        }
    };

    public BaseGalleryPreferenceFragment() {
    }

    public BaseGalleryPreferenceFragment(int pagerIndex) {
        super(pagerIndex);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(PermissionsWantedResponse event) {
        if (getUiHelper().completePermissionsWantedRequest(event)) {
            if (event.areAllPermissionsGranted()) {
                getPreferenceManager().findPreference(getString(R.string.preference_video_cache_maxsize_mb_key)).setEnabled(true);
            } else {
                Preference videoCacheEnabledPref = findPreference(R.string.preference_video_cache_enabled_key);
                ((SwitchPreference) videoCacheEnabledPref).setChecked(false);
                getPreferenceManager().findPreference(getString(R.string.preference_video_cache_maxsize_mb_key)).setEnabled(false);
                getUiHelper().showDetailedMsg(R.string.alert_information, getString(R.string.alert_information_video_caching_disabled));
            }
        }
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

        Preference button = findPreference(R.string.preference_gallery_clearMemoryCache_key);
        button.setTitle(suffixCacheSize(getString(R.string.preference_gallery_clearMemoryCache_title), PicassoFactory.getInstance().getCacheSizeBytes()));
        button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                PicassoFactory.getInstance().clearPicassoCache(getContext(), true);
                getUiHelper().showDetailedMsg(R.string.cacheCleared_title, getString(R.string.cacheCleared_message));
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
                    getUiHelper().showDetailedMsg(R.string.cacheCleared_title, getString(R.string.videoCacheCleared_message));
                } catch (IOException e) {
                    Crashlytics.logException(e);
                    getUiHelper().showDetailedMsg(R.string.cacheCleared_title, getString(R.string.videoCacheClearFailed_message));
                }
                setVideoCacheButtonText(preference);
                return true;

            }
        });

        EditableListPreference playableMultimediaExts = (EditableListPreference) findPreference(R.string.preference_piwigo_playable_media_extensions_key);
        playableMultimediaExts.setListener(new EditableListPreference.EditableListPreferenceChangeAdapter() {
            @Override
            public String filterUserInput(String value) {
                String val = value.toLowerCase();
                int dotIdx = val.indexOf('.');
                if (dotIdx >= 0) {
                    val = val.substring(dotIdx);
                }
                return val;
            }

            @Override
            public void onItemSelectionChange(Set<String> oldSelection, Set<String> newSelection, boolean oldSelectionExists) {
                super.onItemSelectionChange(oldSelection, newSelection, oldSelectionExists);
            }

            @Override
            public Set<String> filterNewUserSelection(Set<String> userSelectedItems) {
                return new TreeSet<>(userSelectedItems);
            }
        });

        ListPreference desiredLanguagePref = (ListPreference) findPreference(R.string.preference_app_desired_language_key);
        List<Locale> localeOptions = ProjectUtils.listLocalesWithUniqueTranslationOf(getContext(), R.string.album_create_failed);
        List<String> entries = new ArrayList<>(localeOptions.size());
        List<String> values = new ArrayList<>(localeOptions.size());
        for (Locale l : localeOptions) {
            entries.add(l.getDisplayName(l));
            values.add(l.toString());
        }
        desiredLanguagePref.setDefaultValue(values.get(0));
        desiredLanguagePref.setEntries(CollectionUtils.asStringArray(entries));
        desiredLanguagePref.setEntryValues(CollectionUtils.asStringArray(values));
        desiredLanguagePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Locale newVal = (Locale) newValue;
                getResources().getConfiguration().setLocale(newVal);
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

        Preference alwaysShowNavButtonsPref = findPreference(R.string.preference_app_always_show_nav_buttons_key);

        alwaysShowNavButtonsPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Boolean newVal = (Boolean) newValue;
                FragmentActivity fragmentActivity = getUiHelper().getParent().getActivity();
                DisplayUtils.setUiFlags(fragmentActivity, newVal, AppPreferences.isAlwaysShowStatusBar(getUiHelper().getPrefs(), getUiHelper().getContext()));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                    getUiHelper().getParent().requireView().requestApplyInsets();
                }
                return true;
            }
        });
        Preference alwaysShowStatusBarPref = findPreference(R.string.preference_app_always_show_status_bar_key);
        alwaysShowStatusBarPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Boolean newVal = (Boolean) newValue;
                FragmentActivity fragmentActivity = getUiHelper().getParent().getActivity();
                DisplayUtils.setUiFlags(fragmentActivity, AppPreferences.isAlwaysShowNavButtons(getUiHelper().getPrefs(), getUiHelper().getContext()), newVal);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                    getUiHelper().getParent().requireView().requestApplyInsets();
                }
                return true;
            }
        });
        Preference desiredLanguagePref = findPreference(R.string.preference_app_desired_language_key);
        desiredLanguagePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Locale newVal = new Locale((String) newValue);

                DisplayUtils.updateContext(getContext(), newVal);
//                getActivity().recreate();

                DisplayUtils.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Activity activity = getActivity();
                        if (activity != null) {
                            activity.recreate();
                        }
                    }
                }, 2000);
                return true;
            }
        });
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