package delit.piwigoclient.ui.preferences;

import android.Manifest;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;

import com.crashlytics.android.Crashlytics;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.net.URI;
import java.security.KeyStore;
import java.util.HashSet;
import java.util.Set;

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
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.fragment.MyPreferenceFragment;
import delit.piwigoclient.ui.common.preference.ClientCertificatePreference;
import delit.piwigoclient.ui.common.preference.EditableListPreference;
import delit.piwigoclient.ui.common.preference.TrustedCaCertificatesPreference;
import delit.piwigoclient.ui.events.PiwigoLoginSuccessEvent;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;
import delit.piwigoclient.util.IOUtils;
import delit.piwigoclient.util.ObjectUtils;
import delit.piwigoclient.util.SetUtils;
import delit.piwigoclient.util.X509Utils;

/**
 * Created by gareth on 12/05/17.
 */

public class ConnectionPreferenceFragment extends MyPreferenceFragment {

    private static final String TAG = "Connection Settings";
    private transient Preference.OnPreferenceChangeListener cacheLevelPrefListener = new CacheLevelPreferenceListener();
    private transient Preference.OnPreferenceChangeListener sessionInvalidationPrefListener = new SessionInvalidatingPrefListener();
    private transient Preference.OnPreferenceChangeListener serverAddressPrefListener = new ServerNamePreferenceListener();
    private boolean initialising = false;
    private String preferencesKey;

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

    private void buildPreferencesViewAndInitialise(String rootKey) {
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
        basicAuthPref.setOnPreferenceChangeListener(sessionInvalidationPrefListener);
        sessionInvalidationPrefListener.onPreferenceChange(basicAuthPref, getBooleanPreferenceValue(basicAuthPref.getKey(), R.bool.preference_server_use_basic_auth_default));
        findPreference(R.string.preference_server_basic_auth_username_key).setOnPreferenceChangeListener(sessionInvalidationPrefListener);
        findPreference(R.string.preference_server_basic_auth_password_key).setOnPreferenceChangeListener(sessionInvalidationPrefListener);

        EditableListPreference connectionProfilePref = (EditableListPreference) findPreference(R.string.preference_piwigo_connection_profile_key);
        connectionProfilePref.setListener(new EditableListPreference.EditableListPreferenceChangeAdapter() {

            @Override
            public void onItemSelectionChange(String oldSelection, String newSelection, boolean oldSelectionExists) {
                if (oldSelection != null) {
                    // clone the current working copy of prefs to the previous active selection
                    if (oldSelectionExists) {
                        ConnectionPreferences.clonePreferences(getPrefs(), getContext(), null, oldSelection);
                    }

                    // Now refresh the logged in session
                    refreshSession(newSelection);
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

        Preference clientCertPref = findPreference(R.string.preference_server_use_client_certs_key);
        clientCertPref.setOnPreferenceChangeListener(sessionInvalidationPrefListener);
        sessionInvalidationPrefListener.onPreferenceChange(clientCertPref, getBooleanPreferenceValue(clientCertPref.getKey(), R.bool.preference_server_use_client_certs_default));
//            ClientCertificatePreference clientCertsPref = (ClientCertificatePreference) findPreference(R.string.preference_select_client_certificate_key);

        Preference useCustomTrustedCertsPref = findPreference(R.string.preference_server_use_custom_trusted_ca_certs_key);
        useCustomTrustedCertsPref.setOnPreferenceChangeListener(sessionInvalidationPrefListener);

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
        clientCertificatePreference.setOnPreferenceChangeListener(sessionInvalidationPrefListener);

        findPreference(R.string.preference_server_ssl_certificate_hostname_verification_key).setOnPreferenceChangeListener(sessionInvalidationPrefListener);
        findPreference(R.string.preference_caching_level_key).setOnPreferenceChangeListener(sessionInvalidationPrefListener);
        findPreference(R.string.preference_caching_max_cache_entries_key).setOnPreferenceChangeListener(sessionInvalidationPrefListener);
        findPreference(R.string.preference_caching_max_cache_entry_size_key).setOnPreferenceChangeListener(sessionInvalidationPrefListener);

        Preference responseCacheFlushButton = findPreference(R.string.preference_caching_clearResponseCache_key);
        setResponseCacheButtonText(responseCacheFlushButton);
        responseCacheFlushButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                try {
                    CacheUtils.clearResponseCache(preference.getContext());
                    ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
                    refreshSession(null);
                    getUiHelper().showOrQueueDialogMessage(R.string.cacheCleared_title, getString(R.string.cacheCleared_message));
                } catch (IOException e) {
                    Crashlytics.logException(e);
                    getUiHelper().showOrQueueDialogMessage(R.string.cacheCleared_title, getString(R.string.cacheClearFailed_message));
                }
                setResponseCacheButtonText(preference);
                return true;

            }
        });

        findPreference(R.string.preference_server_connection_timeout_secs_key).setOnPreferenceChangeListener(sessionInvalidationPrefListener);
        findPreference(R.string.preference_server_connection_retries_key).setOnPreferenceChangeListener(sessionInvalidationPrefListener);

        Preference allowRedirectsPref = findPreference(R.string.preference_server_connection_allow_redirects_key);
        allowRedirectsPref.setOnPreferenceChangeListener(sessionInvalidationPrefListener);

        findPreference(R.string.preference_server_connection_max_redirects_key).setOnPreferenceChangeListener(sessionInvalidationPrefListener);

        Preference button = findPreference("piwigo_connection");
        button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
                refreshSession(null);
                return true;
            }
        });
    }

    private void setResponseCacheButtonText(Preference responseCacheFlushButton) {
        double cacheBytes = CacheUtils.getResponseCacheSize(getContext());
        String spaceSuffix = "(" + IOUtils.toNormalizedText(cacheBytes) + ")";
        responseCacheFlushButton.setTitle(getString(R.string.preference_caching_clearResponseCache_title) + spaceSuffix);
    }

    @Override
    public void onResume() {
        super.onResume();
        initialising = false;
    }

    @Override
    public void onPause() {
        initialising = true;
        super.onPause();
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
        if (savedInstanceState != null) {
        }
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
            long msgId = new HttpConnectionCleanup(connectionPrefs, getContext()).start();
            getUiHelper().addActionOnResponse(msgId, new OnHttpClientShutdownAction(null));
            getUiHelper().addActiveServiceCall(getString(R.string.loading_new_server_configuration), msgId);

            return true;
        }
        return false;
    }

    private void refreshSession(String loginAsProfileAfterLogout) {
        ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);
        if (sessionDetails != null && sessionDetails.isLoggedIn()) {
            getUiHelper().invokeActiveServiceCall(String.format(getString(R.string.logging_out_of_piwigo_pattern), sessionDetails.getServerUrl()), new LogoutResponseHandler(), new OnLogoutAction(loginAsProfileAfterLogout));
        } else if (HttpClientFactory.getInstance(getContext()).isInitialised(connectionPrefs)) {
            long msgId = new HttpConnectionCleanup(connectionPrefs, getContext()).start();
            getUiHelper().addActionOnResponse(msgId, new OnHttpClientShutdownAction(loginAsProfileAfterLogout));
            getUiHelper().addActiveServiceCall(getString(R.string.loading_new_server_configuration), msgId);
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
            getUiHelper().invokeActiveServiceCall(String.format(getString(R.string.logging_out_of_piwigo_pattern), sessionDetails.getServerUrl()), new LogoutResponseHandler(), new OnLogoutAction(false));
            return true;
        } else if (HttpClientFactory.getInstance(getContext()).isInitialised(connectionPrefs)) {
            long msgId = new HttpConnectionCleanup(connectionPrefs, getContext()).start();
            getUiHelper().addActionOnResponse(msgId, new OnHttpClientShutdownAction());
            getUiHelper().addActiveServiceCall(getString(R.string.loading_new_server_configuration), msgId);
            return true;
        }
        return false;
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    private class CacheLevelPreferenceListener implements Preference.OnPreferenceChangeListener {
        @Override
        public boolean onPreferenceChange(final Preference preference, Object value) {
            String currentPersistedValue = getPrefs().getString(preference.getKey(), null);
            final String newValue = (String) value;
            boolean valueChanged = (newValue != null && !newValue.equals(currentPersistedValue));

            if ("disk".equals(newValue) || valueChanged) {
                getUiHelper().runWithExtraPermissions(ConnectionPreferenceFragment.this, Build.VERSION_CODES.BASE, Build.VERSION_CODES.KITKAT, Manifest.permission.WRITE_EXTERNAL_STORAGE, getString(R.string.alert_write_permission_needed_for_caching_to_disk));
            } else if(valueChanged) {
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
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();
            String val = stringValue.toLowerCase();
            boolean isHttps = val.startsWith("https://");
            boolean isHttp = val.startsWith("http://");

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
                    getUiHelper().showOrQueueDialogMessage(R.string.alert_warning, getString(R.string.alert_no_scheme_specified));
                } else if (isHttp) {
                    getUiHelper().showOrQueueDialogMessage(R.string.alert_warning, getString(R.string.alert_http_scheme_specified));
                } else {
                    try {
                        URI uri = URI.create(val);
                    } catch (IllegalArgumentException e) {
                        getUiHelper().showOrQueueDialogMessage(R.string.alert_warning, getString(R.string.alert_invalid_uri_pattern, e.getMessage()));
                    }
                }

                // clear the existing session - it's not valid any more.
                logoutSession();
                AdsManager.getInstance().updateShowAdvertsSetting(getContext().getApplicationContext());
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    getListView().getAdapter().notifyDataSetChanged();
                }
            }

            return true;
        }
    }

    private static class OnLogoutAction extends UIHelper.Action<ConnectionPreferenceFragment,LogoutResponseHandler.PiwigoOnLogoutResponse> {
        private String loginAsProfileAfterLogout;
        private Boolean loginAgain;

        public OnLogoutAction(boolean loginAgain) {
            this.loginAgain = loginAgain;
        }

        public OnLogoutAction(String loginAsProfileAfterLogout) {
            this.loginAsProfileAfterLogout = loginAsProfileAfterLogout;
        }

        @Override
        public boolean onSuccess(UIHelper<ConnectionPreferenceFragment> uiHelper, LogoutResponseHandler.PiwigoOnLogoutResponse response) {
            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
            long msgId = new HttpConnectionCleanup(connectionPrefs, uiHelper.getContext()).start();
            if(loginAgain != null && !loginAgain) {
                uiHelper.addActionOnResponse(msgId, new OnHttpClientShutdownAction());
            } else {
                uiHelper.addActionOnResponse(msgId, new OnHttpClientShutdownAction(loginAsProfileAfterLogout));
            }
            uiHelper.addActiveServiceCall(uiHelper.getContext().getString(R.string.loading_new_server_configuration), msgId);
            return false;
        }

        @Override
        public boolean onFailure(UIHelper<ConnectionPreferenceFragment> uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
            PiwigoSessionDetails.logout(connectionPrefs, uiHelper.getContext());
            onSuccess(uiHelper, null);
            return false;
        }
    }

    private static class OnLoginAction extends UIHelper.Action<ConnectionPreferenceFragment,LoginResponseHandler.PiwigoOnLoginResponse> {
        @Override
        public boolean onSuccess(UIHelper<ConnectionPreferenceFragment> uiHelper, LoginResponseHandler.PiwigoOnLoginResponse response) {
            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
            if (PiwigoSessionDetails.isFullyLoggedIn(connectionPrefs)) {
                PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
                String msg = uiHelper.getContext().getString(R.string.alert_message_success_connectionTest, sessionDetails.getUserType());
                if (sessionDetails.getAvailableImageSizes().size() == 0) {
                    msg += '\n' + uiHelper.getContext().getString(R.string.alert_message_no_available_image_sizes);
                    uiHelper.showOrQueueDialogMessage(R.string.alert_title_connectionTest, msg);
                } else {
                    uiHelper.showToast(msg);
                }
                EventBus.getDefault().post(new PiwigoLoginSuccessEvent(response.getOldCredentials(), false));
            }
            return false;
        }
    }

    private static class OnHttpClientShutdownAction extends UIHelper.Action<ConnectionPreferenceFragment,HttpConnectionCleanup.HttpClientsShutdownResponse> {
        private String loginAsProfileAfterLogout;
        private boolean loginAgain = true;

        public OnHttpClientShutdownAction() {
            this.loginAgain = false;
        }

        public OnHttpClientShutdownAction(String loginAsProfileAfterLogout) {
            this.loginAsProfileAfterLogout = loginAsProfileAfterLogout;
        }

        @Override
        public boolean onSuccess(UIHelper<ConnectionPreferenceFragment> uiHelper, HttpConnectionCleanup.HttpClientsShutdownResponse response) {
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
                    HttpClientFactory.getInstance(uiHelper.getContext()).clearCachedClients(connectionPrefs);
                    uiHelper.invokeActiveServiceCall(String.format(uiHelper.getContext().getString(R.string.logging_in_to_piwigo_pattern), serverUri), new LoginResponseHandler(), new OnLoginAction());
                }
            }
            return retVal;
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
}