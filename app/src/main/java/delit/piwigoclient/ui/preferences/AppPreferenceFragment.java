package delit.piwigoclient.ui.preferences;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;

import androidx.fragment.app.FragmentActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

import com.crashlytics.android.Crashlytics;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.fragment.MyPreferenceFragment;
import delit.libs.util.CollectionUtils;
import delit.libs.util.IOUtils;
import delit.libs.util.ProjectUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AppPreferences;
import delit.piwigoclient.business.video.CacheUtils;
import delit.piwigoclient.ui.PicassoFactory;
import delit.piwigoclient.ui.common.preference.LocalFoldersListPreference;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;

public class AppPreferenceFragment extends MyPreferenceFragment<AppPreferenceFragment> {

    public AppPreferenceFragment() {
    }

    public AppPreferenceFragment(int pagerIndex) {
        super(pagerIndex);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_page_app, rootKey);
        setHasOptionsMenu(true);

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
        ListPreference desiredLanguagePref = (ListPreference) findPreference(R.string.preference_app_desired_language_key);
        List<Locale> localeOptions = ProjectUtils.listLocalesWithUniqueTranslationOf(getContext(), R.string.album_create_failed);
        List<String> entries = new ArrayList<>(localeOptions.size());
        List<String> values = new ArrayList<>(localeOptions.size());
        for (Locale l : localeOptions) {
            entries.add(l.getDisplayName(l));
            values.add(l.toString());
        }
        desiredLanguagePref.setEntries(CollectionUtils.asStringArray(entries));
        desiredLanguagePref.setEntryValues(CollectionUtils.asStringArray(values));
        desiredLanguagePref.setDefaultValue(values.get(0));
        if(desiredLanguagePref.getValue() == null) {
            desiredLanguagePref.setValue(values.get(0));
        }
        desiredLanguagePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Locale newVal = new Locale((String) newValue);

                DisplayUtils.updateContext(getContext(), newVal);
//                getActivity().recreate(); (don't do this as the preference may not be saved)

                DisplayUtils.postOnUiThread(() -> {
                    Activity activity = getActivity();
                    if (activity != null) {
                        activity.recreate();
                    }
                });
                return true;
            }
        });

        Preference pref = findPreference(R.string.preference_app_clear_list_of_user_hints_shown_key);
        pref.setOnPreferenceClickListener(preference -> {
            AppPreferences.clearListOfShownHints(getPrefs(), preference.getContext());
            getUiHelper().showDetailedShortMsg(R.string.alert_information, R.string.alert_all_user_hints_will_be_shown_again);
            return true;
        });

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

        LocalFoldersListPreference appDownloadFolder = (LocalFoldersListPreference) findPreference(R.string.preference_app_default_download_folder_key);
        if(!getPrefs().contains(appDownloadFolder.getKey())) {
            File downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            appDownloadFolder.setDefaultValue(downloadsFolder.getAbsolutePath());
            appDownloadFolder.persistStringValue(downloadsFolder.getAbsolutePath());
        }

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

    private String suffixCacheSize(String basicString, long cacheSizeBytes) {
        return basicString + '(' + IOUtils.toNormalizedText(cacheSizeBytes) + ')';
    }

    private void setVideoCacheButtonText(Preference videoCacheFlushButton) {
        String newTitle = suffixCacheSize(getString(R.string.preference_gallery_clearVideoCache_title), CacheUtils.getVideoCacheSize(getContext()));
        videoCacheFlushButton.setTitle(newTitle);
    }

    private final Preference.OnPreferenceChangeListener videoCacheEnabledPrefListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(final Preference preference, Object value) {
            final Boolean val = (Boolean) value;
            if (Boolean.TRUE.equals(val)) {
                getUiHelper().runWithExtraPermissions(AppPreferenceFragment.this, Build.VERSION_CODES.BASE, Build.VERSION_CODES.KITKAT, Manifest.permission.WRITE_EXTERNAL_STORAGE, getString(R.string.alert_write_permission_needed_for_video_caching));
            } else {
                getPreferenceManager().findPreference(preference.getContext().getString(R.string.preference_video_cache_maxsize_mb_key)).setEnabled(false);
                getUiHelper().showDetailedShortMsg(R.string.alert_warning, getString(R.string.video_caching_disabled_not_recommended));
            }
            return true;
        }
    };
}
