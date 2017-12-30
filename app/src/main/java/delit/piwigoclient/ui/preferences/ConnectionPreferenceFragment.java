package delit.piwigoclient.ui.preferences;

import android.Manifest;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.security.KeyStore;
import java.util.HashSet;
import java.util.Set;

import delit.piwigoclient.R;
import delit.piwigoclient.business.video.CacheUtils;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.HttpClientFactory;
import delit.piwigoclient.piwigoApi.PiwigoAccessService;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.LogoutResponseHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.MyPreferenceFragment;
import delit.piwigoclient.ui.common.NumberPickerPreference;
import delit.piwigoclient.ui.events.PiwigoLoginSuccessEvent;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;
import delit.piwigoclient.util.SetUtils;
import delit.piwigoclient.util.X509Utils;

/**
 * Created by gareth on 12/05/17.
 */

public class ConnectionPreferenceFragment extends MyPreferenceFragment {

    private static final String TAG = "Connection Settings";
    private static final String STATE_RELOGIN_NEEDED = "loginNeeded";
    private boolean initialising = false;
    private boolean loginOnLogout;

    private transient Preference.OnPreferenceChangeListener clientCertificateAuthPreferenceListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            Boolean val = (Boolean) value;
            preference.getPreferenceManager().findPreference(preference.getContext().getString(R.string.preference_select_client_certificate_key)).setEnabled(val);

            if (!initialising) {
                // clear the existing session - it's not valid any more.
                forkLogoutIfNeeded();
            }

            return true;
        }
    };

    private transient Preference.OnPreferenceChangeListener trustedCertsAuthPreferenceListener = new Preference.OnPreferenceChangeListener() {

        AsyncTask<Context, Object, Set<String>> runningTask = null;

        @Override
        public boolean onPreferenceChange(final Preference preference, Object value) {
            final Boolean val = (Boolean) value;
            if(runningTask != null && !runningTask.getStatus().equals(AsyncTask.Status.FINISHED)) {
                runningTask.cancel(true);
            }

            if(val && !initialising) {
                // If we're enabling this feature, flush the list.

                final long actionId = PiwigoResponseBufferingHandler.getNextHandlerId();

                runningTask = new AsyncTask<Context, Object, Set<String>>() {

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
                        Set<String> aliases = X509Utils.listAliasesInStore(truststore);
                        return aliases;
                    }

                    @Override
                    protected void onPostExecute(Set<String> aliases) {
                        if (!isCancelled()) {
                            prefs.edit().putStringSet(getString(R.string.preference_pre_user_notified_certificates_key), aliases).commit();
                            preference.getPreferenceManager().findPreference(preference.getContext().getString(R.string.preference_select_trusted_certificate_key)).setEnabled(true);
                            if (!initialising) {
                                // clear the existing session - it's not valid any more.
                                forkLogoutIfNeeded();
                            }
                        }
                        getUiHelper().onServiceCallComplete(actionId);
                        getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_trusted_certificates_polling));
                    }
                };
                runningTask.execute(getContext());
            } else {
                preference.getPreferenceManager().findPreference(preference.getContext().getString(R.string.preference_select_trusted_certificate_key)).setEnabled(val);
            }
            return true;
        }
    };
    private transient Preference.OnPreferenceChangeListener cacheLevelPrefListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(final Preference preference, Object value) {
            String currentPersistedValue = prefs.getString(preference.getKey(), null);
            final String newValue = (String) value;
            boolean valueChanged = (newValue != null && !newValue.equals(currentPersistedValue));

            if ("disk".equals(newValue) || valueChanged) {
                getUiHelper().runWithExtraPermissions(ConnectionPreferenceFragment.this, Build.VERSION_CODES.BASE, Build.VERSION_CODES.KITKAT, Manifest.permission.WRITE_EXTERNAL_STORAGE, getString(R.string.alert_write_permission_needed_for_caching_to_disk));
            } else if(valueChanged) {
                if (!initialising) {
                    // clear the existing session - it's not valid any more.
                    forkLogoutIfNeeded();
                }
            }

            Preference responseCacheFlushButton = findPreference(R.string.preference_caching_clearResponseCache_key);
            responseCacheFlushButton.setEnabled("disk".equals(newValue));

            getPreferenceManager().findPreference(preference.getContext().getString(R.string.preference_caching_max_cache_entries_key)).setEnabled(!"disabled".equals(newValue));
            getPreferenceManager().findPreference(preference.getContext().getString(R.string.preference_caching_max_cache_entry_size_key)).setEnabled(!"disabled".equals(newValue));
            updateSummary(preference, newValue);


            return true;
        }


        private void updateSummary(Preference preference, String newValue) {
            ListPreference pref = (ListPreference)preference;
            CharSequence[] values = pref.getEntryValues();
            for(int i = 0; i < values.length; i++) {
                if(values[i].equals(newValue)) {
                    preference.setSummary(pref.getEntries()[i]);
                    break;
                }
            }
        }
    };

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

    private transient Preference.OnPreferenceChangeListener basicAuthPreferenceListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            Boolean val = (Boolean) value;
            preference.getPreferenceManager().findPreference(preference.getContext().getString(R.string.preference_server_basic_auth_username_key)).setEnabled(val);
            preference.getPreferenceManager().findPreference(preference.getContext().getString(R.string.preference_server_basic_auth_password_key)).setEnabled(val);

            if (!initialising) {
                // clear the existing session - it's not valid any more.
                forkLogoutIfNeeded();
            }

            return true;
        }
    };

    private transient Preference.OnPreferenceChangeListener simplePreferenceListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {

            if (!initialising) {
                // clear the existing session - it's not valid any more.
                forkLogoutIfNeeded();
            }
            return true;
        }
    };

    private transient Preference.OnPreferenceChangeListener serverAddressPrefListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();
            preference.setSummary(stringValue);
            String val = stringValue.toLowerCase();
            boolean isHttps = val.startsWith("https://");
            boolean isHttp = val.startsWith("http://");

            if (!initialising) {
                if(!(isHttp || isHttps)) {
                    getUiHelper().showOrQueueDialogMessage(R.string.alert_warning, getString(R.string.alert_no_scheme_specified));
                } else if(isHttp) {
                    getUiHelper().showOrQueueDialogMessage(R.string.alert_warning, getString(R.string.alert_http_scheme_specified));
                }

                // clear the existing session - it's not valid any more.
                forkLogoutIfNeeded();
                AdsManager.getInstance().updateShowAdvertsSetting();
            }

            return true;
        }
    };

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its pkg value.
     */
    private transient Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if(initialising && preference instanceof SecurePreference) {
                stringValue = ((SecurePreference<String>)preference).decrypt(stringValue, "");
            }

            if (preference.getKey().toLowerCase().contains("password")) {
                // For all other activity_preferences, set the summary to the value's
                // simple string representation.
                //noinspection ReplaceAllDot
                preference.setSummary(stringValue.replaceAll(".", "*"));
            } else {
                preference.setSummary(stringValue);
            }


            if (!initialising) {
                // clear the existing session - it's not valid any more.
                forkLogoutIfNeeded();
            }

            return true;
        }
    };

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its pkg value.
     */
    private transient Preference.OnPreferenceChangeListener bindListPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
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

            if (!initialising) {
                // clear the existing session - it's not valid any more.
                forkLogoutIfNeeded();
            }

            return true;
        }
    };


    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of value below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                prefs.getString(preference.getKey(), ""));
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

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of value below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private void bindListPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(bindListPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        bindListPreferenceSummaryToValueListener.onPreferenceChange(preference,
                prefs.getString(preference.getKey(), ""));
    }

    @Override
    public void onStart() {
        super.onStart();
        Preference cacheLevelPref = findPreference(R.string.preference_caching_level_key);
        cacheLevelPref.setOnPreferenceChangeListener(cacheLevelPrefListener);
        cacheLevelPrefListener.onPreferenceChange(cacheLevelPref, getPreferenceValue(cacheLevelPref.getKey()));
    }

    @Override
    public View onCreateView(LayoutInflater paramLayoutInflater, ViewGroup paramViewGroup, Bundle paramBundle) {
        View v = super.onCreateView(paramLayoutInflater, paramViewGroup, paramBundle);
        addPreferencesFromResource(R.xml.pref_page_connection);
        setHasOptionsMenu(true);

        // Bind the summaries of EditText/List/Dialog/Ringtone activity_preferences
        // to their values. When their values change, their summaries are
        // updated to reflect the pkg value, per the Android Design
        // guidelines.
        initialising = true;
        Preference serverAddressPref = findPreference(R.string.preference_piwigo_server_address_key);
        serverAddressPref.setOnPreferenceChangeListener(serverAddressPrefListener);
        serverAddressPrefListener.onPreferenceChange(serverAddressPref, prefs.getString(serverAddressPref.getKey(), ""));
        bindPreferenceSummaryToValue(findPreference(R.string.preference_piwigo_server_username_key));
        bindPreferenceSummaryToValue(findPreference(R.string.preference_piwigo_server_password_key));

        Preference basicAuthPref = findPreference(R.string.preference_server_use_basic_auth_key);
        basicAuthPref.setOnPreferenceChangeListener(basicAuthPreferenceListener);
        basicAuthPreferenceListener.onPreferenceChange(basicAuthPref, getBooleanPreferenceValue(basicAuthPref.getKey()));
        bindPreferenceSummaryToValue(findPreference(R.string.preference_server_basic_auth_username_key));
        bindPreferenceSummaryToValue(findPreference(R.string.preference_server_basic_auth_password_key));

        Preference clientCertPref = findPreference(R.string.preference_server_use_client_certs_key);
        clientCertPref.setOnPreferenceChangeListener(clientCertificateAuthPreferenceListener);
        clientCertificateAuthPreferenceListener.onPreferenceChange(clientCertPref, getBooleanPreferenceValue(clientCertPref.getKey()));
//            ClientCertificatePreference clientCertsPref = (ClientCertificatePreference) findPreference(R.string.preference_select_client_certificate_key);

        Preference useCustomTrustedCertsPref = findPreference(R.string.preference_server_use_custom_trusted_ca_certs_key);
        useCustomTrustedCertsPref.setOnPreferenceChangeListener(trustedCertsAuthPreferenceListener);
        TrustedCaCertificatesPreference trustedCertsPref = (TrustedCaCertificatesPreference) findPreference(R.string.preference_select_trusted_certificate_key);
        trustedCertsPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValueObject) {
                KeyStore currentValue = ((TrustedCaCertificatesPreference) preference).getValue();
                KeyStore newValue = (KeyStore) newValueObject;
                Set<String> newAliases = X509Utils.listAliasesInStore(newValue);
                Set<String> removedCertThumbprints = SetUtils.difference(X509Utils.listAliasesInStore(currentValue), newAliases);
                if(removedCertThumbprints.size() > 0) {
                    Set<String> preProcessedCerts = prefs.getStringSet(getString(R.string.preference_pre_user_notified_certificates_key), new HashSet<String>(newAliases.size()));
                    for (String removedThumbprint : removedCertThumbprints) {
                        preProcessedCerts.remove(removedThumbprint);
                    }
                    prefs.edit().putStringSet(getString(R.string.preference_pre_user_notified_certificates_key), preProcessedCerts).commit();
                }
                return true;
            }
        });
        ClientCertificatePreference clientCertificatePreference = (ClientCertificatePreference)findPreference(R.string.preference_select_client_certificate_key);
        clientCertificatePreference.setOnPreferenceChangeListener(simplePreferenceListener);

        bindListPreferenceSummaryToValue(findPreference(R.string.preference_server_ssl_certificate_hostname_verification_key));
        bindListPreferenceSummaryToValue(findPreference(R.string.preference_caching_level_key));
        bindIntPreferenceSummaryToValue(findPreference(R.string.preference_caching_max_cache_entries_key));
        bindIntPreferenceSummaryToValue(findPreference(R.string.preference_caching_max_cache_entry_size_key));

        Preference responseCacheFlushButton = findPreference(R.string.preference_caching_clearResponseCache_key);
        setResponseCacheButtonText(responseCacheFlushButton);
        responseCacheFlushButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                try {
                    CacheUtils.clearResponseCache(getContext());
                    testLogin();
                    getUiHelper().showOrQueueDialogMessage(R.string.cacheCleared_title, getString(R.string.cacheCleared_message));
                } catch(IOException e) {
                    getUiHelper().showOrQueueDialogMessage(R.string.cacheCleared_title, getString(R.string.cacheClearFailed_message));
                }
                setResponseCacheButtonText(preference);
                return true;

            }
        });

        bindIntPreferenceSummaryToValue(findPreference(R.string.preference_server_socketTimeout_millisecs_key));
        bindIntPreferenceSummaryToValue(findPreference(R.string.preference_server_connection_retries_key));

        Preference allowRedirectsPref = findPreference(R.string.preference_server_connection_allow_redirects_key);
        allowRedirectsPref.setOnPreferenceChangeListener(simplePreferenceListener);

        Preference button = findPreference("piwigo_connection");
        button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                testLogin();
                return true;
            }
        });
        return v;
    }

    private void setResponseCacheButtonText(Preference responseCacheFlushButton) {
        double cacheBytes = CacheUtils.getResponseCacheSize(getContext());
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
        if(savedInstanceState != null) {
            loginOnLogout = savedInstanceState.getBoolean(STATE_RELOGIN_NEEDED);
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(STATE_RELOGIN_NEEDED, loginOnLogout);
        super.onSaveInstanceState(outState);
    }

    private boolean forkLogoutIfNeeded() {
        String serverUri = prefs.getString(getString(R.string.preference_piwigo_server_address_key), null);
        if (PiwigoSessionDetails.isLoggedIn()) {
            getUiHelper().addActiveServiceCall(String.format(getString(R.string.logging_out_of_piwigo_pattern), serverUri), PiwigoAccessService.startActionLogout(getContext()));
            return true;
        } else if(HttpClientFactory.getInstance(getContext()).isInitialised()) {
            getUiHelper().addActiveServiceCall(getString(R.string.loading_new_server_configuration), PiwigoAccessService.startActionCleanupHttpConnections(getContext()));
            return true;
        }
        return false;
    }


    private void testLogin() {
        String serverUri = prefs.getString(getString(R.string.preference_piwigo_server_address_key), null);
        if(forkLogoutIfNeeded()) {
            loginOnLogout = true;
        } else {
            HttpClientFactory.getInstance(getContext()).clearCachedClients();
            getUiHelper().addActiveServiceCall(String.format(getString(R.string.logging_in_to_piwigo_pattern), serverUri), PiwigoAccessService.startActionLogin(getContext()));
        }
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    private class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {
        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (response instanceof PiwigoResponseBufferingHandler.PiwigoOnLoginResponse) {
                PiwigoResponseBufferingHandler.PiwigoOnLoginResponse rsp = (PiwigoResponseBufferingHandler.PiwigoOnLoginResponse) response;
                if(rsp.isSessionRetrieved() && rsp.isUserDetailsRetrieved()) {
                    onLogin();
                }
            } else if(response instanceof PiwigoResponseBufferingHandler.PiwigoOnLogoutResponse) {
                if(loginOnLogout) {
                    loginOnLogout = false;
                    testLogin();
                }
            } else if(response instanceof PiwigoResponseBufferingHandler.HttpClientsShutdownResponse) {
                if(loginOnLogout) {
                    loginOnLogout = false;
                    testLogin();
                }
            } else if (response instanceof PiwigoResponseBufferingHandler.ErrorResponse && ((PiwigoResponseBufferingHandler.BasePiwigoResponse)response).getPiwigoMethod().equals(LogoutResponseHandler.METHOD)) {
                //TODO find a nicer way of this.
                // logout failed. Lets just wipe the login state manually for now.
                PiwigoSessionDetails.logout();
                if(loginOnLogout) {
                    loginOnLogout = false;
                    testLogin();
                }

            }
        }
    }


    public void onLogin() {
        getUiHelper().showOrQueueDialogMessage(R.string.alert_title_connectionTest, getString(R.string.alert_success) + '\n' + getString(R.string.alert_userTypePrefix) + ':' + PiwigoSessionDetails.getInstance().getUserType());
        EventBus.getDefault().post(new PiwigoLoginSuccessEvent(false));
    }
}