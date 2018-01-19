package delit.piwigoclient.piwigoApi;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.PersistentCookieStore;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;

import cz.msebera.android.httpclient.auth.AuthScope;
import cz.msebera.android.httpclient.conn.ssl.SSLConnectionSocketFactory;
import cz.msebera.android.httpclient.conn.ssl.SSLContextBuilder;
import cz.msebera.android.httpclient.conn.ssl.TrustStrategy;
import cz.msebera.android.httpclient.conn.ssl.X509HostnameVerifier;
import cz.msebera.android.httpclient.util.TextUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.video.CacheUtils;
import delit.piwigoclient.piwigoApi.http.CachingAsyncHttpClient;
import delit.piwigoclient.piwigoApi.http.CachingSyncHttpClient;
import delit.piwigoclient.piwigoApi.http.UntrustedCaCertificateInterceptingTrustStrategy;
import delit.piwigoclient.ui.preferences.SecurePrefsUtil;
import delit.piwigoclient.util.X509Utils;

/**
 * Created by gareth on 07/07/17.
 */

public class HttpClientFactory {

    private static final String TAG = "HttpClientFactory";
    private static HttpClientFactory instance;
    private final PersistentCookieStore cookieStore;

    protected SharedPreferences prefs;
    private final Context context;

    private CachingAsyncHttpClient asyncClient;
    private CachingSyncHttpClient syncClient;
    private CachingSyncHttpClient videoDownloadClient;
    private static final SecureRandom secureRandom = new SecureRandom();

    public HttpClientFactory(Context c) {
        context = c.getApplicationContext();
        cookieStore = new PersistentCookieStore(context);
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static HttpClientFactory getInstance(Context c) {
        synchronized (HttpClientFactory.class) {
            if(instance == null) {
                instance = new HttpClientFactory(c);
            }
        }
        return instance;
    }

    public void flushCookies() {
        cookieStore.clear();
    }

    public synchronized void clearCachedClients() {
        try {
            closeClient(asyncClient);
        } catch(IOException e) {
            Log.e(TAG, "Error closing asyncClient");
        }
        try {
            closeClient(syncClient);
        } catch(IOException e) {
            Log.e(TAG, "Error closing syncClient");
        }
        try {
            closeClient(videoDownloadClient);
        } catch(IOException e) {
            Log.e(TAG, "Error closing videoDownloadClient");
        }
        asyncClient = null;
        syncClient = null;
        videoDownloadClient = null;
        //now force reinstantiation of the factory to ensure new properties are loaded.
        instance = null;
    }

    private void closeClient(CachingAsyncHttpClient client) throws IOException {
        if(client != null) {
            client.cancelAllRequests(true);
            client.close();
        }
    }

    public CachingSyncHttpClient buildVideoDownloadSyncHttpClient() {
        if (videoDownloadClient == null) {
            boolean forceDisableCache = true;
            // we use a custom cache solution for video data
            videoDownloadClient = buildSyncHttpClient(forceDisableCache);
        }
        return videoDownloadClient;
    }

    public CachingSyncHttpClient buildSyncHttpClient(boolean forceDisableCache) {
        return (CachingSyncHttpClient) buildHttpClient(false, forceDisableCache);
    }

    public synchronized CachingSyncHttpClient getSyncHttpClient() {

        if (syncClient == null) {
            boolean forceDisableCache = false;
            syncClient = buildSyncHttpClient(forceDisableCache);
        }
        return syncClient;
    }

    public synchronized CachingAsyncHttpClient getAsyncHttpClient() {
        if (asyncClient == null) {
            boolean forceDisableCache = false;
            asyncClient = buildHttpClient(true, forceDisableCache);
        }
        return asyncClient;
    }


    protected CachingAsyncHttpClient buildHttpClient(boolean async, boolean forceDisableCache) {

        CachingAsyncHttpClient client;


        int httpPort = 80;
        int httpsPort = 443;
        String serverAddress = prefs.getString(context.getString(R.string.preference_piwigo_server_address_key), "").toLowerCase();

        if(serverAddress.trim().length() == 0) {
            return null;
        }

        int port = extractPort(serverAddress);
        if(port > 0) {
            if (serverAddress.startsWith("http://")) {
                httpPort = port;
            } else if (serverAddress.startsWith("https://")) {
                httpsPort = port;
            }
        }

        //TODO sort out port for connection factory stuff

        SSLConnectionSocketFactory sslSocketFactory = buildHttpsSocketFactory(context);

        if(async) {
            client = new CachingAsyncHttpClient(sslSocketFactory);
        } else {
            client = new CachingSyncHttpClient(sslSocketFactory);
        }
        int defaultConnectTimeoutMillis = context.getResources().getInteger(R.integer.preference_server_socketTimeout_millisecs_default);
        int connectTimeoutMillis = prefs.getInt(context.getString(R.string.preference_server_socketTimeout_millisecs_key), defaultConnectTimeoutMillis);
        client.setConnectTimeout(connectTimeoutMillis);
        client.setMaxConcurrentConnections(2);
        int defaultConnectRetries = context.getResources().getInteger(R.integer.preference_server_connection_retries_default);
        int connectRetries = prefs.getInt(context.getString(R.string.preference_server_connection_retries_key), defaultConnectRetries);
        client.setMaxRetriesAndTimeout(connectRetries, AsyncHttpClient.DEFAULT_RETRY_SLEEP_TIME_MILLIS);
        boolean defaultAllowRedirects = context.getResources().getBoolean(R.bool.preference_server_connection_allow_redirects_default);
        boolean allowRedirects = prefs.getBoolean(context.getString(R.string.preference_server_connection_allow_redirects_key), defaultAllowRedirects);
        int defaultMaxRedirects = context.getResources().getInteger(R.integer.preference_server_connection_max_redirects_default);
        int maxRedirects = prefs.getInt(context.getString(R.string.preference_server_connection_max_redirects_key), defaultMaxRedirects);
        client.setEnableRedirects(allowRedirects, maxRedirects);
        client.setCookieStore(cookieStore);

        if(!forceDisableCache) {
            String cacheLevel = prefs.getString(context.getString(R.string.preference_caching_level_key), context.getResources().getString(R.string.preference_caching_level_default));
            if(cacheLevel.equals("disabled")) {
                client.setCacheSettings(null, 0, 0);
            } else {
                File cacheFolder = null;
                if(cacheLevel.equals("disk")) {
                    cacheFolder = CacheUtils.getBasicCacheFolder(context);
                }
                int maxCacheEntries = prefs.getInt(context.getString(R.string.preference_caching_max_cache_entries_key), context.getResources().getInteger(R.integer.preference_caching_max_cache_entries_default));
                int maxCacheEntrySize = prefs.getInt(context.getString(R.string.preference_caching_max_cache_entry_size_key), context.getResources().getInteger(R.integer.preference_caching_max_cache_entry_size_default));
                client.setCacheSettings(cacheFolder, maxCacheEntries, maxCacheEntrySize);
            }
        } else {
            client.setCacheSettings(null, 0, 0);
        }


        configureBasicServerAuthentication(context, client);

        return client;
    }

    private int extractPort(String serverAddress) {
        Pattern p = Pattern.compile("http[s]?://[^:]*:(\\d*).*");
        Matcher m = p.matcher(serverAddress);
        if(m.matches()) {
            String portStr = m.group(1);
            return Integer.parseInt(portStr);
        }
        return -1;
    }

    protected void configureBasicServerAuthentication(Context context, CachingAsyncHttpClient client) {
        if (prefs.getBoolean(context.getString(R.string.preference_server_use_basic_auth_key), false)) {
            Uri serverUri = Uri.parse(prefs.getString(context.getString(R.string.preference_piwigo_server_address_key), ""));
            SecurePrefsUtil prefUtil = SecurePrefsUtil.getInstance(context);
            String username = prefUtil.readSecureStringPreference(prefs, context.getString(R.string.preference_server_basic_auth_username_key), "");
            String password = prefUtil.readSecureStringPreference(prefs, context.getString(R.string.preference_server_basic_auth_password_key), "");
            client.setBasicAuth(username, password, new AuthScope(serverUri.getHost(), serverUri.getPort(), null, "BASIC"));
            client.setBasicAuth(username, password, new AuthScope(serverUri.getHost(), serverUri.getPort(), null, "DIGEST"));
            client.setBasicAuth(username, password, new AuthScope(serverUri.getHost(), serverUri.getPort(), null, "KERBEROS"));
        }
    }


    private static String[] split(final String s) {
        if (TextUtils.isBlank(s)) {
            return null;
        }
        return s.split(" *, *");
    }

    private SSLConnectionSocketFactory buildHttpsSocketFactory(Context context) {

        String hostnameVerificationLevelStr = prefs.getString(context.getString(R.string.preference_server_ssl_certificate_hostname_verification_key), context.getResources().getString(R.string.preference_server_ssl_certificate_hostname_verification_default));
        int hostnameVerificationLevel = Integer.parseInt(hostnameVerificationLevelStr);
        X509HostnameVerifier hostnameVerifier;
        switch (hostnameVerificationLevel) {
            case 0:
                hostnameVerifier = SSLConnectionSocketFactory.STRICT_HOSTNAME_VERIFIER;
                break;
            case 1:
                hostnameVerifier = SSLConnectionSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER;
                break;
            default:
            case 2:
                hostnameVerifier = SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;
                break;
        }

        TrustStrategy trustStrategy = null;

        KeyStore trustedCAKeystore = null; // use the system keystore.
        if(prefs.getBoolean(context.getString(R.string.preference_server_use_custom_trusted_ca_certs_key), context.getResources().getBoolean(R.bool.preference_server_use_custom_trusted_ca_certs_default))) {
            trustedCAKeystore = X509Utils.loadTrustedCaKeystore(context);
            Set<String> preNotifiedCerts = new HashSet<>(prefs.getStringSet(context.getString(R.string.preference_pre_user_notified_certificates_key), new HashSet<String>()));
            trustStrategy = new UntrustedCaCertificateInterceptingTrustStrategy(trustedCAKeystore, preNotifiedCerts);
        }
        KeyStore clientKeystore = null;
        if(prefs.getBoolean(context.getString(R.string.preference_server_use_client_certs_key), context.getResources().getBoolean(R.bool.preference_server_use_client_certs_default))) {
            clientKeystore = X509Utils.loadClientKeystore(context);
        }
        //TODO protect the key with a password?
        SSLContext sslContext = getCustomSSLContext(clientKeystore, new char[0], trustedCAKeystore, trustStrategy);

        if(sslContext == null) {
            try {
                sslContext = SSLContext.getDefault();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }

        SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
                sslContext,
                split(System.getProperty("https.protocols")),
                split(System.getProperty("https.cipherSuites")),
                hostnameVerifier);

        return sslSocketFactory;
    }

    public SSLContext getCustomSSLContext(KeyStore clientKeystore, char[] clientKeyPass, KeyStore trustedCAKeystore, TrustStrategy trustStrategy) {
        try {

            SSLContextBuilder contextBuilder = new SSLContextBuilder();

            contextBuilder.loadKeyMaterial(clientKeystore, clientKeyPass);
            contextBuilder.setSecureRandom(secureRandom);

            contextBuilder.loadTrustMaterial(trustedCAKeystore, trustStrategy);
            contextBuilder.useTLS();
            return contextBuilder.build();

        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error building sslContext",e);
        } catch (UnrecoverableKeyException e) {
            Log.e(TAG, "Error building sslContext",e);
        } catch (KeyStoreException e) {
            Log.e(TAG, "Error building sslContext",e);
        } catch (KeyManagementException e) {
            Log.e(TAG, "Error building sslContext",e);
        }
        return null;
    }


    public boolean isInitialised() {
        return asyncClient != null || syncClient != null || videoDownloadClient != null;
    }
}
