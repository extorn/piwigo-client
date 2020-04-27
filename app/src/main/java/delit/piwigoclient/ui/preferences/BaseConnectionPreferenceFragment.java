package delit.piwigoclient.ui.preferences;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

import com.crashlytics.android.Crashlytics;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.net.URI;
import java.security.KeyStore;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import delit.libs.ui.view.fragment.MyPreferenceFragment;
import delit.libs.ui.view.preference.ClientCertificatePreference;
import delit.libs.ui.view.preference.EditableListPreference;
import delit.libs.ui.view.preference.TrustedCaCertificatesPreference;
import delit.libs.util.IOUtils;
import delit.libs.util.ObjectUtils;
import delit.libs.util.SetUtils;
import delit.libs.util.SharedPreferencesPreferenceChangedListener;
import delit.libs.util.X509Utils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.business.video.CacheUtils;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.HttpClientFactory;
import delit.piwigoclient.piwigoApi.HttpConnectionCleanup;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.LoginResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.LogoutResponseHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.PiwigoLoginSuccessEvent;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;

public abstract class BaseConnectionPreferenceFragment extends MyPreferenceFragment<BaseConnectionPreferenceFragment> {
    private static final String TAG = "Connection Settings";
    protected transient Preference.OnPreferenceChangeListener httpConnectionEngineInvalidListener = new HttpConnectionEngineInvalidListener();
    private transient Preference.OnPreferenceChangeListener cacheLevelPrefListener = new CacheLevelPreferenceListener();
    protected transient Preference.OnPreferenceChangeListener sessionInvalidationPrefListener = new SessionInvalidatingPrefListener();
    private transient Preference.OnPreferenceChangeListener serverAddressPrefListener = new ServerNamePreferenceListener();
    private boolean initialising = false;
    private String preferencesKey;
    private ResponseCacheButtonTextRetriever responseCacheButtonTextRetriever;

    public BaseConnectionPreferenceFragment(int pagerIndex) {
        super(pagerIndex);
    }

    public BaseConnectionPreferenceFragment() {
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(PermissionsWantedResponse event) {
        if (getUiHelper().completePermissionsWantedRequest(event)) {
            if (event.areAllPermissionsGranted()) {
                if (!initialising) {
                    // clear the existing session - it's not valid any more.
                    logoutSession();
                }
            } else {
                Preference cacheLevelPref = findPreference(R.string.preference_caching_level_key);
                ((ListPreference) cacheLevelPref).setValue("memory");
                cacheLevelPrefListener.onPreferenceChange(cacheLevelPref, getPreferenceValue(cacheLevelPref.getKey()));
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Preference cacheLevelPref = findPreference(R.string.preference_caching_level_key);
        cacheLevelPref.setOnPreferenceChangeListener(cacheLevelPrefListener);
        cacheLevelPrefListener.onPreferenceChange(cacheLevelPref, getPreferenceValue(cacheLevelPref.getKey()));
    }

    protected void buildPreferencesViewAndInitialise(String rootKey) {
        setPreferencesFromResource(R.xml.pref_page_connection, rootKey);
        setHasOptionsMenu(true);

        // Bind the summaries of EditText/List/Dialog/Ringtone activity_preferences
        // to their values. When their values change, their summaries are
        // updated to reflect the pkg value, per the Android Design
        // guidelines.
        initialising = true;
        Preference serverAddressPref = findPreference(R.string.preference_piwigo_server_address_key);
        serverAddressPref.setOnPreferenceChangeListener(serverAddressPrefListener);
        serverAddressPrefListener.onPreferenceChange(serverAddressPref, getPrefs().getString(serverAddressPref.getKey(), ""));
        findPreference(R.string.preference_piwigo_server_username_key).setOnPreferenceChangeListener(sessionInvalidationPrefListener);
        findPreference(R.string.preference_piwigo_server_password_key).setOnPreferenceChangeListener(sessionInvalidationPrefListener);

        Preference basicAuthPref = findPreference(R.string.preference_server_use_basic_auth_key);
        basicAuthPref.setOnPreferenceChangeListener(httpConnectionEngineInvalidListener);
        findPreference(R.string.preference_server_basic_auth_username_key).setOnPreferenceChangeListener(httpConnectionEngineInvalidListener);
        findPreference(R.string.preference_server_basic_auth_password_key).setOnPreferenceChangeListener(httpConnectionEngineInvalidListener);

        final EditableListPreference connectionProfilePref = (EditableListPreference) findPreference(R.string.preference_piwigo_connection_profile_key);
        String value = connectionProfilePref.getValue();
        connectionProfilePref.setListener(new EditableListPreference.EditableListPreferenceChangeAdapter() {

            @Override
            public void onItemSelectionChange(Set<String> oldSelection, Set<String> newSelection, boolean oldSelectionExists) {
                if (!oldSelection.isEmpty()) {
                    // clone the current working copy of prefs to the previous active selection
                    if (oldSelectionExists) {
                        String oldValue = oldSelection.iterator().next();
                        ConnectionPreferences.clonePreferences(getPrefs(), getContext(), null, oldValue);
                    }
                    String newValue = null;
                    if (newSelection.size() > 0) {
                        newValue = newSelection.iterator().next();
                    } else {
                        connectionProfilePref.addAndSelectItem("default");
                        newValue = "default";
                    }

                    // Now refresh the logged in session
                    refreshSession(newValue);
                }

            }

            @Override
            public void onItemAltered(EditableListPreference preference, String oldValue, String newValue) {
                String selectedProfileId = ConnectionPreferences.getActiveProfile().getProfileId(getPrefs(), getContext());
                boolean changingSelectedValue = false;
                if (ObjectUtils.areEqual(selectedProfileId, oldValue)) {
                    changingSelectedValue = true;
                    ConnectionPreferences.clonePreferences(getPrefs(), getContext(), null, oldValue);
                }
                ConnectionPreferences.clonePreferences(getPrefs(), getContext(), oldValue, newValue);
                ConnectionPreferences.deletePreferences(getPrefs(), getContext(), oldValue);
                if (changingSelectedValue) {
                    preference.setValue(newValue);
                }
            }

            @Override
            public void onItemRemoved(String oldValue) {
                ConnectionPreferences.deletePreferences(getPrefs(), getContext(), oldValue);
            }
        });
        if(value == null) {
            connectionProfilePref.addAndSelectItem("default");
        }

        Preference clientCertPref = findPreference(R.string.preference_server_use_client_certs_key);
        clientCertPref.setOnPreferenceChangeListener(httpConnectionEngineInvalidListener);
        //ClientCertificatePreference clientCertsPref = (ClientCertificatePreference) findPreference(R.string.preference_select_client_certificate_key);

        Preference useCustomTrustedCertsPref = findPreference(R.string.preference_server_use_custom_trusted_ca_certs_key);
        useCustomTrustedCertsPref.setOnPreferenceChangeListener(httpConnectionEngineInvalidListener);

        TrustedCaCertificatesPreference trustedCertsPref = (TrustedCaCertificatesPreference) findPreference(R.string.preference_select_trusted_certificate_key);
        trustedCertsPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValueObject) {
                KeyStore currentValue = ((TrustedCaCertificatesPreference) preference).getKeystore();
                KeyStore newValue = (KeyStore) newValueObject;
                Set<String> newAliases = X509Utils.listAliasesInStore(newValue);
                Set<String> removedCertThumbprints = SetUtils.difference(X509Utils.listAliasesInStore(currentValue), newAliases);
                if (removedCertThumbprints.size() > 0) {
                    Set<String> preProcessedCerts = getPrefs().getStringSet(getString(R.string.preference_pre_user_notified_certificates_key), new HashSet<String>(newAliases.size()));
                    for (String removedThumbprint : removedCertThumbprints) {
                        preProcessedCerts.remove(removedThumbprint);
                    }
                    getPrefs().edit().putStringSet(getString(R.string.preference_pre_user_notified_certificates_key), preProcessedCerts).commit();
                    forceHttpConnectionCleanupAndRebuild();
                }
                return true;
            }
        });
        ClientCertificatePreference clientCertificatePreference = (ClientCertificatePreference) findPreference(R.string.preference_select_client_certificate_key);
        clientCertificatePreference.setOnPreferenceChangeListener(httpConnectionEngineInvalidListener);

        findPreference(R.string.preference_server_ssl_certificate_hostname_verification_key).setOnPreferenceChangeListener(httpConnectionEngineInvalidListener);
        findPreference(R.string.preference_caching_level_key).setOnPreferenceChangeListener(httpConnectionEngineInvalidListener);
        findPreference(R.string.preference_caching_max_cache_entries_key).setOnPreferenceChangeListener(httpConnectionEngineInvalidListener);
        findPreference(R.string.preference_caching_max_cache_entry_size_key).setOnPreferenceChangeListener(httpConnectionEngineInvalidListener);

        Preference responseCacheFlushButton = findPreference(R.string.preference_caching_clearResponseCache_key);
        responseCacheFlushButton.setOnPreferenceClickListener(new ResponseCacheFlushButtonListener(this));
        setResponseCacheButtonText();

        findPreference(R.string.preference_server_connection_timeout_secs_key).setOnPreferenceChangeListener(httpConnectionEngineInvalidListener);
        findPreference(R.string.preference_server_connection_retries_key).setOnPreferenceChangeListener(httpConnectionEngineInvalidListener);

        Preference allowRedirectsPref = findPreference(R.string.preference_server_connection_allow_redirects_key);
        allowRedirectsPref.setOnPreferenceChangeListener(httpConnectionEngineInvalidListener);

        findPreference(R.string.preference_server_connection_max_redirects_key).setOnPreferenceChangeListener(httpConnectionEngineInvalidListener);

        Preference button = findPreference(R.string.preference_test_server_connection_key);
        Drawable icon = AppCompatResources.getDrawable(getContext(), R.drawable.ic_sync_black_24dp);
        DrawableCompat.setTint(icon, ContextCompat.getColor(getContext(), R.color.accent));
        button.setIcon(icon);

        button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
                refreshSession(null);
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

    }

    private class HttpConnectionEngineInvalidListener implements Preference.OnPreferenceChangeListener {

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            getPrefs().registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                    if (!initialising) {
                        forceHttpConnectionCleanupAndRebuild();
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                            getListView().getAdapter().notifyDataSetChanged();
                        }
                    }
                    getPrefs().unregisterOnSharedPreferenceChangeListener(this);
                }
            });
            return true;
        }
    }

    private void setResponseCacheButtonText() {
        if (responseCacheButtonTextRetriever != null && responseCacheButtonTextRetriever.getStatus() != AsyncTask.Status.FINISHED) {
            responseCacheButtonTextRetriever.cancel(true);
        }
        responseCacheButtonTextRetriever = UIHelper.submitAsyncTask(new ResponseCacheButtonTextRetriever(), this);
    }

    @Override
    public void onPause() {
        if (responseCacheButtonTextRetriever != null && responseCacheButtonTextRetriever.getStatus() != AsyncTask.Status.FINISHED) {
            responseCacheButtonTextRetriever.cancel(true);
        }
        initialising = true;
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        initialising = false;
    }

    private static class ResponseCacheButtonTextRetriever extends AsyncTask<BaseConnectionPreferenceFragment, Void, Long> {

        private Preference responseCacheFlushButton;
        private BaseConnectionPreferenceFragment fragment;

        @Override
        protected Long doInBackground(BaseConnectionPreferenceFragment[] params) {
            this.fragment = params[0];
            this.responseCacheFlushButton = fragment.findPreference(R.string.preference_caching_clearResponseCache_key);
            final long cacheBytes = CacheUtils.getResponseCacheSize(responseCacheFlushButton.getContext());
            return cacheBytes;
        }

        @Override
        protected void onPostExecute(Long cacheBytes) {
            try {
                if (!isCancelled() && fragment.isVisible()) {
                    String spaceSuffix = "(" + IOUtils.toNormalizedText(cacheBytes) + ")";
//                String cacheLevel = ConnectionPreferences.getCacheLevel(fragment.getPrefs(), context);
//                if("memory".equals(cacheLevel)) {
//                    spaceSuffix += String.format(" + %1$d", CacheUtils.getItemsInResponseCache(context));
//                }
                    responseCacheFlushButton.setTitle(fragment.getString(R.string.preference_caching_clearResponseCache_title) + spaceSuffix);
                }
            } finally {
                responseCacheFlushButton = null;
                fragment = null; // allow reference to be cleared
            }
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            responseCacheFlushButton = null;
            fragment = null; // allow reference to be cleared
        }
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
    public void onCreate(Bundle savedInstanceState) {
//        if (savedInstanceState != null) {
//        }
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Load the preferences from an XML resource
        preferencesKey = rootKey;
        buildPreferencesViewAndInitialise(rootKey);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    private boolean forceHttpConnectionCleanupAndRebuild() {
        ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
        if (HttpClientFactory.getInstance(getContext()).isInitialised(connectionPrefs)) {
            long msgId = new HttpConnectionCleanup(connectionPrefs, getContext(), true).start();
            getUiHelper().addActionOnResponse(msgId, new OnHttpClientShutdownAction(null));
            getUiHelper().addActiveServiceCall(getString(R.string.loading_new_server_configuration), msgId, "httpCleanup");

            return true;
        }
        return false;
    }

    private void refreshSession(String loginAsProfileAfterLogout) {
        ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);
        if (sessionDetails != null && sessionDetails.isLoggedIn()) {
            getUiHelper().invokeActiveServiceCall(getString(R.string.logging_out_of_piwigo_pattern, sessionDetails.getServerUrl()), new LogoutResponseHandler(), new OnLogoutAction(loginAsProfileAfterLogout));
        } else if (HttpClientFactory.getInstance(getContext()).isInitialised(connectionPrefs)) {
            long msgId = new HttpConnectionCleanup(connectionPrefs, getContext()).start();
            getUiHelper().addActionOnResponse(msgId, new OnHttpClientShutdownAction(loginAsProfileAfterLogout));
            getUiHelper().addActiveServiceCall(getString(R.string.loading_new_server_configuration), msgId, "httpShutdown");
        } else {
            new OnHttpClientShutdownAction(loginAsProfileAfterLogout).onSuccess(getUiHelper(), null);
            reloadConnectionProfilePrefs();
        }
    }

    private void reloadConnectionProfilePrefs() {
        // Refresh the displayed preferences with the new connection profile contents
        initialising = true;
        setPreferenceScreen(null);
        buildPreferencesViewAndInitialise(preferencesKey);
        initialising = false;
    }

    private boolean logoutSession() {
        ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);
        if (sessionDetails != null && sessionDetails.isLoggedIn()) {
            getUiHelper().invokeActiveServiceCall(getString(R.string.logging_out_of_piwigo_pattern, sessionDetails.getServerUrl()), new LogoutResponseHandler(), new OnLogoutAction(false));
            return true;
        } else if (HttpClientFactory.getInstance(getContext()).isInitialised(connectionPrefs)) {
            long msgId = new HttpConnectionCleanup(connectionPrefs, getContext()).start();
            getUiHelper().addActionOnResponse(msgId, new OnHttpClientShutdownAction());
            getUiHelper().addActiveServiceCall(getString(R.string.loading_new_server_configuration), msgId, "httpCleanup");
            return true;
        }
        return false;
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    private static class OnLogoutAction extends UIHelper.Action<FragmentUIHelper<ConnectionPreferenceFragment>, ConnectionPreferenceFragment, LogoutResponseHandler.PiwigoOnLogoutResponse> {
        private String loginAsProfileAfterLogout;
        private Boolean loginAgain;

        public OnLogoutAction(boolean loginAgain) {
            this.loginAgain = loginAgain;
        }

        public OnLogoutAction(String loginAsProfileAfterLogout) {
            this.loginAsProfileAfterLogout = loginAsProfileAfterLogout;
        }

        @Override
        public boolean onSuccess(FragmentUIHelper<ConnectionPreferenceFragment> uiHelper, LogoutResponseHandler.PiwigoOnLogoutResponse response) {
            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
            long msgId = new HttpConnectionCleanup(connectionPrefs, uiHelper.getContext()).start();
            if(loginAgain != null && !loginAgain) {
                uiHelper.addActionOnResponse(msgId, new OnHttpClientShutdownAction());
            } else {
                uiHelper.addActionOnResponse(msgId, new OnHttpClientShutdownAction(loginAsProfileAfterLogout));
            }
            uiHelper.addActiveServiceCall(uiHelper.getContext().getString(R.string.loading_new_server_configuration), msgId, "httpShutdown");
            return false;
        }

        @Override
        public boolean onFailure(FragmentUIHelper<ConnectionPreferenceFragment> uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
            PiwigoSessionDetails.logout(connectionPrefs, uiHelper.getContext());
            onSuccess(uiHelper, null);
            return false;
        }
    }

    private static class OnLoginAction extends UIHelper.Action<FragmentUIHelper<ConnectionPreferenceFragment>, ConnectionPreferenceFragment, LoginResponseHandler.PiwigoOnLoginResponse> {
        @Override
        public boolean onSuccess(FragmentUIHelper<ConnectionPreferenceFragment> uiHelper, LoginResponseHandler.PiwigoOnLoginResponse response) {
            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
            if (PiwigoSessionDetails.isFullyLoggedIn(connectionPrefs)) {
                PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
                String msg = uiHelper.getContext().getString(R.string.alert_message_success_connectionTest, sessionDetails.getUserType());
                if (sessionDetails.getAvailableImageSizes().size() == 0) {
                    msg += '\n' + uiHelper.getContext().getString(R.string.alert_message_no_available_image_sizes);
                    uiHelper.showDetailedMsg(R.string.alert_title_connectionTest, msg);
                } else {
                    uiHelper.showDetailedMsg(R.string.alert_title_connectionTest, msg);
                }
                EventBus.getDefault().post(new PiwigoLoginSuccessEvent(response.getOldCredentials(), false));
            }
            return false;
        }
    }

    private static class OnHttpClientShutdownAction extends UIHelper.Action<FragmentUIHelper<BaseConnectionPreferenceFragment>, BaseConnectionPreferenceFragment, HttpConnectionCleanup.HttpClientsShutdownResponse> {
        private String loginAsProfileAfterLogout;
        private boolean loginAgain = true;

        public OnHttpClientShutdownAction() {
            this.loginAgain = false;
        }

        public OnHttpClientShutdownAction(String loginAsProfileAfterLogout) {
            this.loginAsProfileAfterLogout = loginAsProfileAfterLogout;
        }

        @Override
        public boolean onSuccess(FragmentUIHelper<BaseConnectionPreferenceFragment> uiHelper, HttpConnectionCleanup.HttpClientsShutdownResponse response) {
            boolean retVal = false;
            if(loginAsProfileAfterLogout != null) {
                // copy those profile values to the working app copy of prefs
                ConnectionPreferences.clonePreferences(uiHelper.getPrefs(), uiHelper.getContext(), loginAsProfileAfterLogout, null);
                retVal = true;
            }

            if(loginAgain) {
                ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
                String serverUri = connectionPrefs.getPiwigoServerAddress(uiHelper.getPrefs(), uiHelper.getContext());
                if ((serverUri == null || serverUri.trim().isEmpty())) {
                    if(loginAsProfileAfterLogout == null) {
                        // if we aren't swapping connection profiles, warn that a login is impossible.
                        uiHelper.showOrQueueDialogMessage(R.string.alert_error, uiHelper.getContext().getString(R.string.alert_warning_no_server_url_specified));
                    }
                } else {
                    uiHelper.invokeActiveServiceCall(String.format(uiHelper.getContext().getString(R.string.logging_in_to_piwigo_pattern), serverUri), new LoginResponseHandler(), new OnLoginAction());
                }
            }
            return retVal;
        }
    }

    private class CacheLevelPreferenceListener implements Preference.OnPreferenceChangeListener {
        @Override
        public boolean onPreferenceChange(final Preference preference, Object value) {
            String currentPersistedValue = getPrefs().getString(preference.getKey(), null);
            final String newValue = (String) value;
            boolean valueChanged = (newValue != null && !newValue.equals(currentPersistedValue));

            if ("disk".equals(newValue) || valueChanged) {
                if (!initialising) {
                    getUiHelper().runWithExtraPermissions(BaseConnectionPreferenceFragment.this, Build.VERSION_CODES.BASE, Build.VERSION_CODES.KITKAT, Manifest.permission.WRITE_EXTERNAL_STORAGE, getString(R.string.alert_write_permission_needed_for_caching_to_disk));
                }
            } else {
                if (!initialising) {
                    // clear the existing session - it's not valid any more.
                    logoutSession();
                }
            }

            Preference responseCacheFlushButton = findPreference(R.string.preference_caching_clearResponseCache_key);
            responseCacheFlushButton.setEnabled("disk".equals(newValue));

            getPreferenceManager().findPreference(preference.getContext().getString(R.string.preference_caching_max_cache_entries_key)).setEnabled(!"disabled".equals(newValue));
            getPreferenceManager().findPreference(preference.getContext().getString(R.string.preference_caching_max_cache_entry_size_key)).setEnabled(!"disabled".equals(newValue));

            return true;
        }
    }

    private class SessionInvalidatingPrefListener implements Preference.OnPreferenceChangeListener {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {

            if (!initialising) {
                // clear the existing session - it's not valid any more.
                logoutSession();
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    getListView().getAdapter().notifyDataSetChanged();
                }
            }
            return true;
        }
    }

    private class ServerNamePreferenceListener implements Preference.OnPreferenceChangeListener {
        @Override
        public boolean onPreferenceChange(final Preference preference, Object value) {
            String stringValue = value.toString();
            String val = stringValue.toLowerCase();
            boolean isHttps = val.startsWith("https://");
            boolean isHttp = val.startsWith("http://");

            boolean autoTweakPreference = false;

            if (!initialising) {

                SwitchPreference p = (SwitchPreference) findPreference(R.string.preference_server_connection_force_https_key);
                if (isHttp) {
                    p.setEnabled(false);
                    getPrefs().edit().putBoolean(getString(R.string.preference_server_connection_force_https_key), false).commit();
                }
                if (isHttps) {
                    p.setEnabled(true);
                }

                if (!(isHttp || isHttps)) {
                    autoTweakPreference = true;
                } else if (isHttp) {
                    getUiHelper().showDetailedMsg(R.string.alert_warning, getString(R.string.alert_http_scheme_specified));
                } else {
                    try {
                        URI uri = URI.create(val);
                    } catch (IllegalArgumentException e) {
                        if(val.indexOf(' ') >= 0) {
                            autoTweakPreference = true;
                        } else {
                            getUiHelper().showDetailedMsg(R.string.alert_warning, getString(R.string.alert_invalid_uri_pattern, e.getMessage()));
                        }
                    }
                }

                if(!autoTweakPreference) {
                    // clear the existing session - it's not valid any more.
                    logoutSession();
                    AdsManager.getInstance().updateShowAdvertsSetting(getContext().getApplicationContext());
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                        getListView().getAdapter().notifyDataSetChanged();
                    }
                }
            }

            if(autoTweakPreference) {

                preference.getSharedPreferences().registerOnSharedPreferenceChangeListener(new SharedPreferencesPreferenceChangedListener(preference) {

                    @Override
                    public void onMonitoredPreferenceChanged(SharedPreferences sharedPreferences, Preference preference, String key) {
                        // now remove this listener again.
                        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);

                        // tweak user entered preference
                        String prefValue = sharedPreferences.getString(key, "");
                        prefValue = prefValue.replaceAll(" ", "");

                        String lowerPref = prefValue.toLowerCase();
                        if(!lowerPref.startsWith("http")) {
                            prefValue = "http://" + prefValue;
                        }

                        if(preference.callChangeListener(prefValue)) {
                            ((EditTextPreference)preference).setText(prefValue);
                        }
                    }
                });
            }

            return true;
        }
    }

    private class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {
        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {

            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();

            if (response instanceof HttpConnectionCleanup.HttpClientsShutdownResponse) {
                reloadConnectionProfilePrefs();
            }
        }
    }

    private static class ClearCacheInBackgroundTask extends AsyncTask<BaseConnectionPreferenceFragment, Void, Boolean> {

        private BaseConnectionPreferenceFragment fragment;

        @Override
        protected Boolean doInBackground(BaseConnectionPreferenceFragment... fragments) {
            fragment = fragments[0];
            try {
                CacheUtils.clearResponseCache(fragment.getContext());
                fragment.forceHttpConnectionCleanupAndRebuild();
                return Boolean.TRUE;
            } catch (SecurityException e) {
                Crashlytics.logException(e);
                return Boolean.FALSE;
            }
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            try {
                super.onPostExecute(aBoolean);
                if (Boolean.TRUE.equals(aBoolean)) {
                    fragment.getUiHelper().showDetailedMsg(R.string.cacheCleared_title, fragment.getString(R.string.cacheCleared_message));
                } else {
                    fragment.getUiHelper().showDetailedMsg(R.string.cacheCleared_title, fragment.getString(R.string.cacheClearFailed_message));
                }
                fragment.setResponseCacheButtonText();
            } finally {
                fragment = null;
            }
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            fragment = null;
        }
    }

    private static class ResponseCacheFlushButtonListener implements Preference.OnPreferenceClickListener {
        private final BaseConnectionPreferenceFragment fragment;

        public ResponseCacheFlushButtonListener(BaseConnectionPreferenceFragment fragment) {
            this.fragment = fragment;
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            fragment.getUiHelper().showDetailedMsg(R.string.cacheCleared_title, fragment.getString(R.string.cacheClearingStarted_message));
            new ClearCacheInBackgroundTask().execute(fragment);
            return true;

        }
    }
}
