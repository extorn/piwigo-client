package delit.piwigoclient.ui.preferences;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceManager;

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
    private static final String STATE_RELOGIN_NEEDED = "loginNeeded";
    private transient Preference.OnPreferenceChangeListener cacheLevelPrefListener = new CacheLevelPreferenceListener();
    private transient Preference.OnPreferenceChangeListener trustedCertsAuthPreferenceListener = new TrustedCertsPrefListener();
    private transient Preference.OnPreferenceChangeListener simplePreferenceListener = new SimplePrefListener();
    private transient Preference.OnPreferenceChangeListener serverAddressPrefListener = new ServerNamePreferenceListener();
    private boolean initialising = false;
    private boolean loginOnLogout;
    private String preferencesKey;

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(PermissionsWantedResponse event) {
        if (getUiHelper().completePermissionsWantedRequest(event)) {
            if (event.areAllPermissionsGranted()) {
                if (!initialising) {
                    // clear the existing session - it's not valid any more.
                    forkLogoutIfNeeded();
                }
            } else {
                Preference cacheLevelPref = findPreference(R.string.preference_caching_level_key);
                ((ListPreference) cacheLevelPref).setValue("memory");
                cacheLevelPrefListener.onPreferenceChange(cacheLevelPref, getPreferenceValue(cacheLevelPref.getKey()));
            }
        }
    }

//
// private void writeObject(ObjectOutputStream out) throws IOException {
//    out.defaultWriteObject();
//}
// private void readObject(java.io.ObjectInputStream in)
//            throws IOException, ClassNotFoundException {
//        in.defaultReadObject();
//        cacheLevelPrefListener = new CacheLevelPreferenceListener();
//        trustedCertsAuthPreferenceListener = new TrustedCertsPrefListener();
//        simplePreferenceListener = new SimplePrefListener();
//        serverAddressPrefListener = new ServerNamePreferenceListener();
//    }

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
        findPreference(R.string.preference_piwigo_server_username_key).setOnPreferenceChangeListener(simplePreferenceListener);
        findPreference(R.string.preference_piwigo_server_password_key).setOnPreferenceChangeListener(simplePreferenceListener);

        Preference basicAuthPref = findPreference(R.string.preference_server_use_basic_auth_key);
        basicAuthPref.setOnPreferenceChangeListener(simplePreferenceListener);
        simplePreferenceListener.onPreferenceChange(basicAuthPref, getBooleanPreferenceValue(basicAuthPref.getKey()));
        findPreference(R.string.preference_server_basic_auth_username_key).setOnPreferenceChangeListener(simplePreferenceListener);
        findPreference(R.string.preference_server_basic_auth_password_key).setOnPreferenceChangeListener(simplePreferenceListener);

        EditableListPreference connectionProfilePref = (EditableListPreference) findPreference(R.string.preference_piwigo_connection_profile_key);
        connectionProfilePref.setListener(new EditableListPreference.EditableListPreferenceChangeAdapter() {

            @Override
            public void onItemSelectionChanged(EditableListPreference preference, String oldSelection, String newSelection, boolean oldSelectionExists) {
                if (oldSelection != null) {
                    // clone the current working copy of prefs to the previous active selection
                    if (oldSelectionExists) {
                        ConnectionPreferences.clonePreferences(getPrefs(), getContext(), null, oldSelection);
                    }
                    // copy those profile values to the working app copy of prefs
                    ConnectionPreferences.clonePreferences(getPrefs(), getContext(), newSelection, null);

                    // refresh all preference values on the page.
                    setPreferenceScreen(null);
                    buildPreferencesViewAndInitialise(preferencesKey);
                    ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
                    testLogin(connectionPrefs);
                    initialising = false;
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
        clientCertPref.setOnPreferenceChangeListener(simplePreferenceListener);
        simplePreferenceListener.onPreferenceChange(clientCertPref, getBooleanPreferenceValue(clientCertPref.getKey()));
//            ClientCertificatePreference clientCertsPref = (ClientCertificatePreference) findPreference(R.string.preference_select_client_certificate_key);

        Preference useCustomTrustedCertsPref = findPreference(R.string.preference_server_use_custom_trusted_ca_certs_key);
        useCustomTrustedCertsPref.setOnPreferenceChangeListener(trustedCertsAuthPreferenceListener);
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
                }
                return true;
            }
        });
        ClientCertificatePreference clientCertificatePreference = (ClientCertificatePreference) findPreference(R.string.preference_select_client_certificate_key);
        clientCertificatePreference.setOnPreferenceChangeListener(simplePreferenceListener);

        findPreference(R.string.preference_server_ssl_certificate_hostname_verification_key).setOnPreferenceChangeListener(simplePreferenceListener);
        findPreference(R.string.preference_caching_level_key).setOnPreferenceChangeListener(simplePreferenceListener);
        findPreference(R.string.preference_caching_max_cache_entries_key).setOnPreferenceChangeListener(simplePreferenceListener);
        findPreference(R.string.preference_caching_max_cache_entry_size_key).setOnPreferenceChangeListener(simplePreferenceListener);

        Preference responseCacheFlushButton = findPreference(R.string.preference_caching_clearResponseCache_key);
        setResponseCacheButtonText(responseCacheFlushButton);
        responseCacheFlushButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                try {
                    CacheUtils.clearResponseCache(preference.getContext());
                    ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
                    testLogin(connectionPrefs);
                    getUiHelper().showOrQueueDialogMessage(R.string.cacheCleared_title, getString(R.string.cacheCleared_message));
                } catch (IOException e) {
                    Crashlytics.logException(e);
                    getUiHelper().showOrQueueDialogMessage(R.string.cacheCleared_title, getString(R.string.cacheClearFailed_message));
                }
                setResponseCacheButtonText(preference);
                return true;

            }
        });

        findPreference(R.string.preference_server_socketTimeout_millisecs_key).setOnPreferenceChangeListener(simplePreferenceListener);
        findPreference(R.string.preference_server_connection_retries_key).setOnPreferenceChangeListener(simplePreferenceListener);

        Preference allowRedirectsPref = findPreference(R.string.preference_server_connection_allow_redirects_key);
        allowRedirectsPref.setOnPreferenceChangeListener(simplePreferenceListener);

        findPreference(R.string.preference_server_connection_max_redirects_key).setOnPreferenceChangeListener(simplePreferenceListener);

        Preference button = findPreference("piwigo_connection");
        button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
                testLogin(connectionPrefs);
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
            loginOnLogout = savedInstanceState.getBoolean(STATE_RELOGIN_NEEDED);
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
        outState.putBoolean(STATE_RELOGIN_NEEDED, loginOnLogout);
        super.onSaveInstanceState(outState);
    }

    private boolean forkLogoutIfNeeded() {
        ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);
        if (sessionDetails != null && sessionDetails.isLoggedIn()) {
            getUiHelper().addActiveServiceCall(String.format(getString(R.string.logging_out_of_piwigo_pattern), sessionDetails.getServerUrl()), new LogoutResponseHandler().invokeAsync(getContext()));
            return true;
        } else if (HttpClientFactory.getInstance(getContext()).isInitialised(connectionPrefs)) {
            getUiHelper().addActiveServiceCall(getString(R.string.loading_new_server_configuration), new HttpConnectionCleanup(connectionPrefs, getContext()).start());
            return true;
        }
        return false;
    }

    private void testLogin(ConnectionPreferences.ProfilePreferences connectionPrefs) {
        String serverUri = connectionPrefs.getPiwigoServerAddress(getPrefs(), getContext());
        if (serverUri == null || serverUri.trim().isEmpty()) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_warning_no_server_url_specified));
        } else {
            if (forkLogoutIfNeeded()) {
                loginOnLogout = true;
            } else {
                Context context = getContext();
                HttpClientFactory.getInstance(context).clearCachedClients(connectionPrefs);
                getUiHelper().addActiveServiceCall(String.format(getString(R.string.logging_in_to_piwigo_pattern), serverUri), new LoginResponseHandler().invokeAsync(context));
            }
        }
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    private void onLogin(PiwigoSessionDetails oldCredentials) {
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        String msg = getString(R.string.alert_message_success_connectionTest, sessionDetails.getUserType());
        if (sessionDetails.getAvailableImageSizes().size() == 0) {
            msg += '\n' + getString(R.string.alert_message_no_available_image_sizes);
            getUiHelper().showOrQueueDialogMessage(R.string.alert_title_connectionTest, msg);
        } else {
            getUiHelper().showToast(msg);
        }
        EventBus.getDefault().post(new PiwigoLoginSuccessEvent(oldCredentials, false));
    }

    private static class LoadCertificatesTask extends AsyncTask<Context, Object, Set<String>> {

        final long actionId = PiwigoResponseBufferingHandler.getNextHandlerId();
        private final UIHelper uiHelper;
        private final PreferenceManager preferenceManager;
        private final SharedPreferences prefs;

        public LoadCertificatesTask(UIHelper uiHelper, SharedPreferences prefs, PreferenceManager preferenceManager) {
            this.uiHelper = uiHelper;
            this.prefs = prefs;
            this.preferenceManager = preferenceManager;
        }

        public UIHelper getUiHelper() {
            return uiHelper;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            getUiHelper().addActiveServiceCall(R.string.alert_clearing_certificate_use_history, actionId);
        }

        @Override
        protected Set<String> doInBackground(Context... context) {
            KeyStore truststore = X509Utils.loadTrustedCaKeystore(context[0]);
            if (isCancelled()) {
                return null;
            }
            return X509Utils.listAliasesInStore(truststore);
        }

        @Override
        protected void onPostExecute(Set<String> aliases) {
            if (!isCancelled()) {
                prefs.edit().putStringSet(uiHelper.getContext().getString(R.string.preference_pre_user_notified_certificates_key), aliases).commit();
                preferenceManager.findPreference(uiHelper.getContext().getString(R.string.preference_select_trusted_certificate_key)).setEnabled(true);
                forkLogoutIfNeeded();
            }
            getUiHelper().onServiceCallComplete(actionId);
            getUiHelper().showOrQueueDialogMessage(R.string.alert_information, uiHelper.getContext().getString(R.string.alert_trusted_certificates_polling));
        }

        private boolean forkLogoutIfNeeded() {
            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
            PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);
            if (sessionDetails != null && sessionDetails.isLoggedIn()) {
                getUiHelper().addActiveServiceCall(String.format(getUiHelper().getContext().getString(R.string.logging_out_of_piwigo_pattern), sessionDetails.getServerUrl()), new LogoutResponseHandler().invokeAsync(getUiHelper().getContext()));
                return true;
            } else if (HttpClientFactory.getInstance(getUiHelper().getContext()).isInitialised(connectionPrefs)) {
                getUiHelper().addActiveServiceCall(getUiHelper().getContext().getString(R.string.loading_new_server_configuration), new HttpConnectionCleanup(connectionPrefs, getUiHelper().getContext()).start());
                return true;
            }
            return false;
        }
    }

    private class CacheLevelPreferenceListener implements Preference.OnPreferenceChangeListener {
        @Override
        public boolean onPreferenceChange(final Preference preference, Object value) {
            String currentPersistedValue = getPrefs().getString(preference.getKey(), null);
            final String newValue = (String) value;
            boolean valueChanged = (newValue != null && !newValue.equals(currentPersistedValue));

            if ("disk".equals(newValue) || valueChanged) {
                getUiHelper().runWithExtraPermissions(ConnectionPreferenceFragment.this, Build.VERSION_CODES.BASE, Build.VERSION_CODES.KITKAT, Manifest.permission.WRITE_EXTERNAL_STORAGE, getString(R.string.alert_write_permission_needed_for_caching_to_disk));
            }/* else if(valueChanged) {
                if (!initialising) {
                    // clear the existing session - it's not valid any more.
                    forkLogoutIfNeeded();
                }
            }*/

            Preference responseCacheFlushButton = findPreference(R.string.preference_caching_clearResponseCache_key);
            responseCacheFlushButton.setEnabled("disk".equals(newValue));

            getPreferenceManager().findPreference(preference.getContext().getString(R.string.preference_caching_max_cache_entries_key)).setEnabled(!"disabled".equals(newValue));
            getPreferenceManager().findPreference(preference.getContext().getString(R.string.preference_caching_max_cache_entry_size_key)).setEnabled(!"disabled".equals(newValue));

            return true;
        }
    }

    private class TrustedCertsPrefListener implements Preference.OnPreferenceChangeListener {
        AsyncTask<Context, Object, Set<String>> runningTask = null;

        @Override
        public boolean onPreferenceChange(final Preference preference, Object value) {
            final Boolean val = (Boolean) value;
            if (runningTask != null && !runningTask.getStatus().equals(AsyncTask.Status.FINISHED)) {
                runningTask.cancel(true);
            }

            if (val && !initialising) {
                // If we're enabling this feature, flush the list.

                runningTask = new LoadCertificatesTask(getUiHelper(), getPrefs(), preference.getPreferenceManager());
                runningTask.execute(getContext());
            } else {
                preference.getPreferenceManager().findPreference(preference.getContext().getString(R.string.preference_select_trusted_certificate_key)).setEnabled(val);
            }
            return true;
        }
    }

    private class SimplePrefListener implements Preference.OnPreferenceChangeListener {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {

            if (!initialising) {
                // clear the existing session - it's not valid any more.
                forkLogoutIfNeeded();
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
                forkLogoutIfNeeded();
                AdsManager.getInstance().updateShowAdvertsSetting(getContext().getApplicationContext());
            }

            return true;
        }
    }

    private class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {
        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {

            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();

            if (response instanceof LoginResponseHandler.PiwigoOnLoginResponse) {
                LoginResponseHandler.PiwigoOnLoginResponse rsp = (LoginResponseHandler.PiwigoOnLoginResponse) response;
                if (PiwigoSessionDetails.isFullyLoggedIn(connectionPrefs)) {
                    onLogin(rsp.getOldCredentials());
                }
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoOnLogoutResponse) {
                getUiHelper().addActiveServiceCall(getString(R.string.loading_new_server_configuration), new HttpConnectionCleanup(connectionPrefs, getContext()).start());
            } else if (response instanceof PiwigoResponseBufferingHandler.HttpClientsShutdownResponse) {
                if (loginOnLogout) {
                    loginOnLogout = false;
                    testLogin(connectionPrefs);
                }
            } else if (response instanceof PiwigoResponseBufferingHandler.ErrorResponse && LogoutResponseHandler.METHOD.equals(((PiwigoResponseBufferingHandler.BasePiwigoResponse) response).getPiwigoMethod())) {
                //TODO find a nicer way of this.
                // logout failed. Lets just wipe the login state manually for now.
                PiwigoSessionDetails.logout(connectionPrefs, getContext());
                if (loginOnLogout) {
                    loginOnLogout = false;
                    testLogin(connectionPrefs);
                }
            }
        }
    }
}