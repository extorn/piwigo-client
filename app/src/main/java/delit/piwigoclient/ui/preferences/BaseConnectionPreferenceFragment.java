package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;
import androidx.recyclerview.widget.RecyclerView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.net.URI;
import java.security.KeyStore;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import delit.libs.core.util.Logging;
import delit.libs.ui.OwnedSafeAsyncTask;
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

public abstract class BaseConnectionPreferenceFragment<F extends BaseConnectionPreferenceFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends MyPreferenceFragment<F,FUIH> {
    private static final String TAG = "Connection Settings";
    protected Preference.OnPreferenceChangeListener httpConnectionEngineInvalidListener = new HttpConnectionEngineInvalidListener();
    private final Preference.OnPreferenceChangeListener cacheLevelPrefListener = new CacheLevelPreferenceListener();
    protected Preference.OnPreferenceChangeListener sessionInvalidationPrefListener = new SessionInvalidatingPrefListener();
    private final Preference.OnPreferenceChangeListener serverAddressPrefListener = new ServerNamePreferenceListener();
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

    /**
     * WARNING. This is called before the UIHelper is initialised. DONT try and use it.
     * @param rootKey
     */
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
        trustedCertsPref.setOnPreferenceChangeListener((preference, newValueObject) -> {
            KeyStore currentValue = ((TrustedCaCertificatesPreference) preference).getKeystore();
            KeyStore newValue = (KeyStore) newValueObject;
            Set<String> newAliases = X509Utils.listAliasesInStore(newValue);
            Set<String> removedCertThumbprints = SetUtils.difference(X509Utils.listAliasesInStore(currentValue), newAliases);
            if (removedCertThumbprints.size() > 0) {
                Set<String> preProcessedCerts = new HashSet<>(getPrefs().getStringSet(getString(R.string.preference_pre_user_notified_certificates_key), new HashSet<>(newAliases.size())));
                for (String removedThumbprint : removedCertThumbprints) {
                    preProcessedCerts.remove(removedThumbprint);
                }
                getPrefs().edit().putStringSet(getString(R.string.preference_pre_user_notified_certificates_key), preProcessedCerts).commit();
                forceHttpConnectionCleanupAndRebuild();
            }
            return true;
        });
        ClientCertificatePreference clientCertificatePreference = (ClientCertificatePreference) findPreference(R.string.preference_select_client_certificate_key);
        clientCertificatePreference.setOnPreferenceChangeListener(httpConnectionEngineInvalidListener);

        findPreference(R.string.preference_server_ssl_certificate_hostname_verification_key).setOnPreferenceChangeListener(httpConnectionEngineInvalidListener);
        findPreference(R.string.preference_caching_level_key).setOnPreferenceChangeListener(httpConnectionEngineInvalidListener);
        findPreference(R.string.preference_caching_max_cache_entries_key).setOnPreferenceChangeListener(httpConnectionEngineInvalidListener);
        findPreference(R.string.preference_caching_max_cache_entry_size_key).setOnPreferenceChangeListener(httpConnectionEngineInvalidListener);

        Preference responseCacheFlushButton = findPreference(R.string.preference_caching_clearResponseCache_key);
        responseCacheFlushButton.setOnPreferenceClickListener(new ResponseCacheFlushButtonListener(this));

        findPreference(R.string.preference_server_connection_timeout_secs_key).setOnPreferenceChangeListener(httpConnectionEngineInvalidListener);
        findPreference(R.string.preference_server_connection_retries_key).setOnPreferenceChangeListener(httpConnectionEngineInvalidListener);

        Preference allowRedirectsPref = findPreference(R.string.preference_server_connection_allow_redirects_key);
        allowRedirectsPref.setOnPreferenceChangeListener(httpConnectionEngineInvalidListener);

        findPreference(R.string.preference_server_connection_max_redirects_key).setOnPreferenceChangeListener(httpConnectionEngineInvalidListener);

        Preference button = findPreference(R.string.preference_test_server_connection_key);
        Drawable icon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_sync_black_24dp);
        if(icon != null) {
            DrawableCompat.setTint(icon, ContextCompat.getColor(requireContext(), R.color.app_secondary));
        }
        button.setIcon(icon);

        button.setOnPreferenceClickListener(preference -> {
            ConnectionPreferences.getActiveProfile();
            refreshSession(null);
            return true;
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
                            RecyclerView.Adapter<?> adapter = getListView().getAdapter();
                            if(adapter != null) {
                                adapter.notifyDataSetChanged();
                            }
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
        responseCacheButtonTextRetriever = UIHelper.<ResponseCacheButtonTextRetriever,Void>submitAsyncTask(new ResponseCacheButtonTextRetriever<>((F) this) );
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

    private static class ResponseCacheButtonTextRetriever<F extends BaseConnectionPreferenceFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends OwnedSafeAsyncTask<F, Void, Void, Long> {

        private Preference responseCacheFlushButton;

        public ResponseCacheButtonTextRetriever(F owner) {
            super(owner);
            withContext(owner.requireContext());
        }

        @Override
        protected Long doInBackgroundSafely(Void... nothing) {
            try {
                this.responseCacheFlushButton = getOwner().findPreference(R.string.preference_caching_clearResponseCache_key);
                return CacheUtils.getResponseCacheSize(responseCacheFlushButton.getContext());
            } catch(IllegalStateException e) {
                Logging.log(Log.WARN, TAG, "unable to retrieve response cache details");
                Logging.recordException(e);
            }
            return null;
        }

        @Override
        protected void onPostExecuteSafely(Long cacheBytes) {
            if(cacheBytes == null) {
                return; // no value to process
            }
            try {
                if (!isCancelled() && getOwner().isVisible()) {
                    String spaceSuffix = "(" + IOUtils.bytesToNormalizedText(cacheBytes) + ")";
//                String cacheLevel = ConnectionPreferences.getCacheLevel(fragment.getPrefs(), context);
//                if("memory".equals(cacheLevel)) {
//                    spaceSuffix += String.format(" + %1$d", CacheUtils.getItemsInResponseCache(context));
//                }
                    responseCacheFlushButton.setTitle(getOwner().getString(R.string.preference_caching_clearResponseCache_title) + spaceSuffix);
                }
            } finally {
                responseCacheFlushButton = null;
            }
        }

        @Override
        protected void onCancelledSafely() {
            responseCacheFlushButton = null;
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDetach() {
        EventBus.getDefault().unregister(this);
        super.onDetach();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setResponseCacheButtonText();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Load the preferences from an XML resource
        preferencesKey = rootKey;
        buildPreferencesViewAndInitialise(rootKey);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    private boolean forceHttpConnectionCleanupAndRebuild() {
        ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
        if (HttpClientFactory.getInstance(getContext()).isInitialised(connectionPrefs)) {
            long msgId = new HttpConnectionCleanup(connectionPrefs, getContext(), true).start();
            getUiHelper().addActionOnResponse(msgId, new OnHttpClientShutdownAction());
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
        setResponseCacheButtonText();
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
    protected BasicPiwigoResponseListener<FUIH,F> buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    private static class OnLogoutAction<FUIH extends FragmentUIHelper<FUIH,ConnectionPreferenceFragment>> extends UIHelper.Action<FUIH, ConnectionPreferenceFragment, LogoutResponseHandler.PiwigoOnLogoutResponse> implements Parcelable {
        private String loginAsProfileAfterLogout;
        private Boolean loginAgain;

        public OnLogoutAction(boolean loginAgain) {
            this.loginAgain = loginAgain;
        }

        public OnLogoutAction(String loginAsProfileAfterLogout) {
            this.loginAsProfileAfterLogout = loginAsProfileAfterLogout;
        }

        protected OnLogoutAction(Parcel in) {
            super(in);
            loginAsProfileAfterLogout = in.readString();
            byte tmpLoginAgain = in.readByte();
            loginAgain = tmpLoginAgain == 0 ? null : tmpLoginAgain == 1;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(loginAsProfileAfterLogout);
            dest.writeByte((byte) (loginAgain == null ? 0 : loginAgain ? 1 : 2));
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<OnLogoutAction<?>> CREATOR = new Creator<OnLogoutAction<?>>() {
            @Override
            public OnLogoutAction<?> createFromParcel(Parcel in) {
                return new OnLogoutAction<>(in);
            }

            @Override
            public OnLogoutAction<?>[] newArray(int size) {
                return new OnLogoutAction[size];
            }
        };

        @Override
        public boolean onSuccess(FUIH uiHelper, LogoutResponseHandler.PiwigoOnLogoutResponse response) {
            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
            long msgId = new HttpConnectionCleanup(connectionPrefs, uiHelper.getAppContext()).start();
            if(loginAgain != null && !loginAgain) {
                uiHelper.addActionOnResponse(msgId, new OnHttpClientShutdownAction());
            } else {
                uiHelper.addActionOnResponse(msgId, new OnHttpClientShutdownAction(loginAsProfileAfterLogout));
            }
            uiHelper.addActiveServiceCall(uiHelper.getAppContext().getString(R.string.loading_new_server_configuration), msgId, "httpShutdown");
            return false;
        }

        @Override
        public boolean onFailure(FUIH uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
            PiwigoSessionDetails.logout(connectionPrefs, uiHelper.getAppContext());
            onSuccess(uiHelper, null);
            return false;
        }
    }

    private static class OnLoginAction<FUIH extends FragmentUIHelper<FUIH,ConnectionPreferenceFragment>> extends UIHelper.Action<FUIH, ConnectionPreferenceFragment, LoginResponseHandler.PiwigoOnLoginResponse>implements Parcelable {

        protected OnLoginAction(){}

        protected OnLoginAction(Parcel in) {
            super(in);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<OnLoginAction<?>> CREATOR = new Creator<OnLoginAction<?>>() {
            @Override
            public OnLoginAction<?> createFromParcel(Parcel in) {
                return new OnLoginAction<>(in);
            }

            @Override
            public OnLoginAction<?>[] newArray(int size) {
                return new OnLoginAction[size];
            }
        };

        @Override
        public boolean onSuccess(FUIH uiHelper, LoginResponseHandler.PiwigoOnLoginResponse response) {
            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
            if (PiwigoSessionDetails.isFullyLoggedIn(connectionPrefs)) {
                PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
                String msg = uiHelper.getAppContext().getString(R.string.alert_message_success_connectionTest, sessionDetails.getUserType());
                if (sessionDetails.getAvailableImageSizes().size() == 0) {
                    msg += '\n' + uiHelper.getAppContext().getString(R.string.alert_message_no_available_image_sizes);
                    uiHelper.showDetailedMsg(R.string.alert_title_connectionTest, msg);
                } else {
                    uiHelper.showDetailedMsg(R.string.alert_title_connectionTest, msg);
                }
                EventBus.getDefault().post(new PiwigoLoginSuccessEvent(response.getOldCredentials(), false));
            }
            return false;
        }
    }

    private static class OnHttpClientShutdownAction<FUIH extends FragmentUIHelper<FUIH,BaseConnectionPreferenceFragment>> extends UIHelper.Action<FUIH, BaseConnectionPreferenceFragment, HttpConnectionCleanup.HttpClientsShutdownResponse> implements Parcelable {
        private String loginAsProfileAfterLogout;
        private boolean loginAgain = true;

        public OnHttpClientShutdownAction() {
            this.loginAgain = false;
        }

        public OnHttpClientShutdownAction(String loginAsProfileAfterLogout) {
            this.loginAsProfileAfterLogout = loginAsProfileAfterLogout;
        }

        protected OnHttpClientShutdownAction(Parcel in) {
            super(in);
            loginAsProfileAfterLogout = in.readString();
            loginAgain = in.readByte() != 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(loginAsProfileAfterLogout);
            dest.writeByte((byte) (loginAgain ? 1 : 0));
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<OnHttpClientShutdownAction<?>> CREATOR = new Creator<OnHttpClientShutdownAction<?>>() {
            @Override
            public OnHttpClientShutdownAction<?> createFromParcel(Parcel in) {
                return new OnHttpClientShutdownAction<>(in);
            }

            @Override
            public OnHttpClientShutdownAction<?>[] newArray(int size) {
                return new OnHttpClientShutdownAction[size];
            }
        };

        @Override
        public boolean onSuccess(FUIH uiHelper, HttpConnectionCleanup.HttpClientsShutdownResponse response) {
            boolean retVal = false;
            if(loginAsProfileAfterLogout != null) {
                // copy those profile values to the working app copy of prefs
                ConnectionPreferences.clonePreferences(uiHelper.getPrefs(), uiHelper.getAppContext(), loginAsProfileAfterLogout, null);
                retVal = true;
            }

            if(loginAgain) {
                ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
                String serverUri = connectionPrefs.getPiwigoServerAddress(uiHelper.getPrefs(), uiHelper.getAppContext());
                if ((serverUri == null || serverUri.trim().isEmpty())) {
                    if(loginAsProfileAfterLogout == null) {
                        // if we aren't swapping connection profiles, warn that a login is impossible.
                        uiHelper.showOrQueueDialogMessage(R.string.alert_error, uiHelper.getAppContext().getString(R.string.alert_warning_no_server_url_specified));
                    }
                } else {
                    uiHelper.invokeActiveServiceCall(String.format(uiHelper.getAppContext().getString(R.string.logging_in_to_piwigo_pattern), serverUri), new LoginResponseHandler(), new OnLoginAction());
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
                    Uri videoCacheFolder = Uri.fromFile(CacheUtils.getBasicCacheFolder(requireContext()));
                    String permission = IOUtils.getManifestFilePermissionsNeeded(requireContext(), videoCacheFolder, IOUtils.URI_PERMISSION_READ_WRITE);
                    getUiHelper().runWithExtraPermissions(BaseConnectionPreferenceFragment.this, Build.VERSION_CODES.BASE, Build.VERSION_CODES.Q, permission, getString(R.string.alert_write_permission_needed_for_caching_to_disk));
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
                    RecyclerView.Adapter<?> adapter = getListView().getAdapter();
                    if(adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
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
                    AdsManager.getInstance(getContext()).updateShowAdvertsSetting(getContext());
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                        RecyclerView.Adapter<?> adapter = getListView().getAdapter();
                        if(adapter != null) {
                            adapter.notifyDataSetChanged();
                        }
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
                        String prefValue = Objects.requireNonNull(sharedPreferences.getString(key, ""));
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

    private static class CustomPiwigoResponseListener<FUIH extends FragmentUIHelper<FUIH,BaseConnectionPreferenceFragment>> extends BasicPiwigoResponseListener<FUIH,BaseConnectionPreferenceFragment> {
        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {

            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();

            if (response instanceof HttpConnectionCleanup.HttpClientsShutdownResponse) {
                getParent().reloadConnectionProfilePrefs();
            }
        }
    }

    private static class ClearCacheInBackgroundTask extends OwnedSafeAsyncTask<BaseConnectionPreferenceFragment, Void, Void, Boolean> {

        public ClearCacheInBackgroundTask(BaseConnectionPreferenceFragment owner) {
            super(owner);
            withContext(owner.requireContext());
        }

        @Override
        protected Boolean doInBackgroundSafely(Void... nothing) {
            try {
                CacheUtils.clearResponseCache(getContext());
                getOwner().forceHttpConnectionCleanupAndRebuild();
                return Boolean.TRUE;
            } catch (SecurityException e) {
                Logging.recordException(e);
                return Boolean.FALSE;
            }
        }

        @Override
        protected void onPostExecuteSafely(Boolean aBoolean) {
            if (Boolean.TRUE.equals(aBoolean)) {
                getOwner().getUiHelper().showDetailedMsg(R.string.cacheCleared_title, getOwner().getString(R.string.cacheCleared_message));
            } else {
                getOwner().getUiHelper().showDetailedMsg(R.string.cacheCleared_title, getOwner().getString(R.string.cacheClearFailed_message));
            }
            getOwner().setResponseCacheButtonText();
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
            new ClearCacheInBackgroundTask(fragment).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            return true;

        }
    }
}
