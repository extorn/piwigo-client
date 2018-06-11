package delit.piwigoclient.piwigoApi;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

import com.loopj.android.http.AsyncHttpClient;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.util.HashMap;
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
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.business.video.CacheUtils;
import delit.piwigoclient.piwigoApi.http.CachingAsyncHttpClient;
import delit.piwigoclient.piwigoApi.http.CachingSyncHttpClient;
import delit.piwigoclient.piwigoApi.http.PersistentProfileCookieStore;
import delit.piwigoclient.piwigoApi.http.UntrustedCaCertificateInterceptingTrustStrategy;
import delit.piwigoclient.util.X509Utils;

/**
 * Created by gareth on 07/07/17.
 */

public class HttpClientFactory {

    private static final String TAG = "HttpClientFactory";
    private static final SecureRandom secureRandom = new SecureRandom();
    private static HttpClientFactory instance;
    private final HashMap<ConnectionPreferences.ProfilePreferences, PersistentProfileCookieStore> cookieStoreMap;
    private final SharedPreferences prefs;
    private final HashMap<ConnectionPreferences.ProfilePreferences, CachingAsyncHttpClient> asyncClientMap;
    private final HashMap<ConnectionPreferences.ProfilePreferences, CachingSyncHttpClient> syncClientMap;
    private final HashMap<ConnectionPreferences.ProfilePreferences, CachingSyncHttpClient> videoDownloadClientMap;

    public HttpClientFactory(Context c) {
        cookieStoreMap = new HashMap<>(3);
        prefs = PreferenceManager.getDefaultSharedPreferences(c.getApplicationContext());
        asyncClientMap = new HashMap<>(3);
        syncClientMap = new HashMap<>(3);
        videoDownloadClientMap = new HashMap<>(3);
    }

    public static HttpClientFactory getInstance(Context c) {
        synchronized (HttpClientFactory.class) {
            if (instance == null) {
                instance = new HttpClientFactory(c);
            }
        }
        return instance;
    }

    private static String[] split(final String s) {
        if (TextUtils.isBlank(s)) {
            return null;
        }
        return s.split(" *, *");
    }

    public void flushCookies(ConnectionPreferences.ProfilePreferences profile) {
        cookieStoreMap.get(profile).clear();
    }

    public synchronized void clearCachedClients(ConnectionPreferences.ProfilePreferences profile) {
        if (profile == null) {
            // clear ALL.
            HashSet<ConnectionPreferences.ProfilePreferences> keys = new HashSet<>();
            keys.addAll(asyncClientMap.keySet());
            keys.addAll(syncClientMap.keySet());
            keys.addAll(videoDownloadClientMap.keySet());
            for (ConnectionPreferences.ProfilePreferences aProfile : keys) {
                clearCachedClients(aProfile);
            }
            return;
        }
        try {
            closeClient(asyncClientMap.remove(profile));
        } catch (IOException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error closing asyncClient");
            }
        }
        try {
            closeClient(syncClientMap.remove(profile));
        } catch (IOException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error closing syncClient");
            }
        }
        try {
            closeClient(videoDownloadClientMap.remove(profile));
        } catch (IOException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error closing videoDownloadClient");
            }
        }
        flushCookies(profile);
    }

    private void closeClient(CachingAsyncHttpClient client) throws IOException {
        if (client != null) {
            client.cancelAllRequests(true);
            client.close();
        }
    }

    public CachingSyncHttpClient buildVideoDownloadSyncHttpClient(ConnectionPreferences.ProfilePreferences connectionPrefs, Context c) {
        CachingSyncHttpClient videoDownloadClient = videoDownloadClientMap.get(connectionPrefs);
        if (videoDownloadClient == null) {
            boolean forceDisableCache = true;
            // we use a custom cache solution for video data
            videoDownloadClient = buildSyncHttpClient(connectionPrefs, c, true);
            videoDownloadClientMap.put(connectionPrefs, videoDownloadClient);
        }
        return videoDownloadClient;
    }

    public CachingSyncHttpClient buildSyncHttpClient(ConnectionPreferences.ProfilePreferences connectionPrefs, Context c, boolean forceDisableCache) {
        return (CachingSyncHttpClient) buildHttpClient(connectionPrefs, c, false, forceDisableCache);
    }

    public synchronized CachingSyncHttpClient getSyncHttpClient(ConnectionPreferences.ProfilePreferences connectionPrefs, Context c) {
        CachingSyncHttpClient syncClient = syncClientMap.get(connectionPrefs);
        if (syncClient == null) {
            boolean forceDisableCache = false;
            syncClient = buildSyncHttpClient(connectionPrefs, c, false);
            syncClientMap.put(connectionPrefs, syncClient);
        }
        return syncClient;
    }

    public synchronized CachingAsyncHttpClient getAsyncHttpClient(ConnectionPreferences.ProfilePreferences connectionPrefs, Context c) {
        CachingAsyncHttpClient asyncClient = asyncClientMap.get(connectionPrefs);
        if (asyncClient == null) {
            boolean forceDisableCache = false;
            asyncClient = buildHttpClient(connectionPrefs, c, true, false);
            asyncClientMap.put(connectionPrefs, asyncClient);
        }
        return asyncClient;
    }

    protected CachingAsyncHttpClient buildHttpClient(ConnectionPreferences.ProfilePreferences connectionPrefs, Context c, boolean async, boolean forceDisableCache) {

        Context context = c.getApplicationContext();

        CachingAsyncHttpClient client;

        String piwigoServerUrl = connectionPrefs.getPiwigoServerAddress(prefs, context);

        if (piwigoServerUrl == null || piwigoServerUrl.trim().length() == 0) {
            return null;
        }

        SSLConnectionSocketFactory sslSocketFactory = buildHttpsSocketFactory(connectionPrefs, context);

        if (async) {
            client = new CachingAsyncHttpClient(sslSocketFactory);
        } else {
            client = new CachingSyncHttpClient(sslSocketFactory);
        }
        int connectTimeoutMillis = connectionPrefs.getServerConnectTimeout(prefs, context);
        client.setConnectTimeout(connectTimeoutMillis);
        client.setMaxConcurrentConnections(5);
        int connectRetries = connectionPrefs.getMaxServerConnectRetries(prefs, context);
        client.setMaxRetriesAndTimeout(connectRetries, AsyncHttpClient.DEFAULT_RETRY_SLEEP_TIME_MILLIS);
        boolean allowRedirects = connectionPrefs.getFollowHttpRedirects(prefs, context);
        int maxRedirects = connectionPrefs.getMaxHttpRedirects(prefs, context);
        client.setEnableRedirects(allowRedirects, maxRedirects);
        client.setCookieStore(getCookieStore(connectionPrefs, context));

        if (!forceDisableCache) {
            String cacheLevel = prefs.getString(context.getString(R.string.preference_caching_level_key), context.getResources().getString(R.string.preference_caching_level_default));
            if (cacheLevel.equals("disabled")) {
                client.setCacheSettings(null, 0, 0);
            } else {
                File cacheFolder = null;
                if (cacheLevel.equals("disk")) {
                    cacheFolder = CacheUtils.getBasicCacheFolder(context);
                }
                int maxCacheEntries = prefs.getInt(context.getString(R.string.preference_caching_max_cache_entries_key), context.getResources().getInteger(R.integer.preference_caching_max_cache_entries_default));
                int maxCacheEntrySize = prefs.getInt(context.getString(R.string.preference_caching_max_cache_entry_size_key), context.getResources().getInteger(R.integer.preference_caching_max_cache_entry_size_default));
                client.setCacheSettings(cacheFolder, maxCacheEntries, maxCacheEntrySize);
            }
        } else {
            client.setCacheSettings(null, 0, 0);
        }


        configureBasicServerAuthentication(connectionPrefs, context, client);

        return client;
    }

    private PersistentProfileCookieStore getCookieStore(ConnectionPreferences.ProfilePreferences connectionPrefs, Context context) {
        PersistentProfileCookieStore cookieStore = cookieStoreMap.get(connectionPrefs);
        if (cookieStore == null) {
            cookieStore = new PersistentProfileCookieStore(context.getApplicationContext(), connectionPrefs.getProfileId(prefs, context));
            cookieStoreMap.put(connectionPrefs, cookieStore);
        }
        return cookieStore;
    }

    private int extractPort(String serverAddress) {
        Pattern p = Pattern.compile("http[s]?://[^:]*:(\\d*).*");
        Matcher m = p.matcher(serverAddress);
        if (m.matches()) {
            String portStr = m.group(1);
            return Integer.parseInt(portStr);
        }
        return -1;
    }

    protected void configureBasicServerAuthentication(ConnectionPreferences.ProfilePreferences connectionPrefs, Context context, CachingAsyncHttpClient client) {
        if (connectionPrefs.getUseBasicAuthentication(prefs, context)) {
            String piwigoServerUrl = connectionPrefs.getPiwigoServerAddress(prefs, context);
            if (piwigoServerUrl != null) {
                Uri serverUri = Uri.parse(piwigoServerUrl);
                String username = connectionPrefs.getBasicAuthenticationUsername(prefs, context);
                String password = connectionPrefs.getBasicAuthenticationPassword(prefs, context);
                client.setBasicAuth(username, password, new AuthScope(serverUri.getHost(), serverUri.getPort(), null, "BASIC"));
                client.setBasicAuth(username, password, new AuthScope(serverUri.getHost(), serverUri.getPort(), null, "DIGEST"));
                client.setBasicAuth(username, password, new AuthScope(serverUri.getHost(), serverUri.getPort(), null, "KERBEROS"));
            }
        }
    }

    private SSLConnectionSocketFactory buildHttpsSocketFactory(ConnectionPreferences.ProfilePreferences connectionPrefs, Context context) {

        String hostnameVerificationLevelStr = connectionPrefs.getCertificateHostnameVerificationLevel(prefs, context);
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
        if (connectionPrefs.getUsePinnedServerCertificates(prefs, context)) {
            trustedCAKeystore = X509Utils.loadTrustedCaKeystore(context);
            Set<String> preNotifiedCerts = new HashSet<>(connectionPrefs.getUserPreNotifiedCerts(prefs, context));
            trustStrategy = new UntrustedCaCertificateInterceptingTrustStrategy(trustedCAKeystore, preNotifiedCerts);
        }
        KeyStore clientKeystore = null;
        if (connectionPrefs.getUseClientCertificates(prefs, context)) {
            clientKeystore = X509Utils.loadClientKeystore(context);
        }
        //TODO protect the key with a password?
        SSLContext sslContext = getCustomSSLContext(clientKeystore, new char[0], trustedCAKeystore, trustStrategy);

        if (sslContext == null) {
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

    private SSLContext getCustomSSLContext(KeyStore clientKeystore, char[] clientKeyPass, KeyStore trustedCAKeystore, TrustStrategy trustStrategy) {
        try {

            SSLContextBuilder contextBuilder = new SSLContextBuilder();

            contextBuilder.loadKeyMaterial(clientKeystore, clientKeyPass);
            contextBuilder.setSecureRandom(secureRandom);

            contextBuilder.loadTrustMaterial(trustedCAKeystore, trustStrategy);
            contextBuilder.useTLS();
            return contextBuilder.build();

        } catch (NoSuchAlgorithmException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error building sslContext", e);
            }
        } catch (UnrecoverableKeyException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error building sslContext", e);
            }
        } catch (KeyStoreException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error building sslContext", e);
            }
        } catch (KeyManagementException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error building sslContext", e);
            }
        }
        return null;
    }

    public boolean isInitialised(ConnectionPreferences.ProfilePreferences connectionProfile) {
        return asyncClientMap.containsKey(connectionProfile) || syncClientMap.containsKey(connectionProfile) || videoDownloadClientMap.containsKey(connectionProfile);
    }
}
