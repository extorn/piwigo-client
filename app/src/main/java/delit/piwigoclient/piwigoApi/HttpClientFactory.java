package delit.piwigoclient.piwigoApi;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.preference.PreferenceManager;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
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
import cz.msebera.android.httpclient.impl.client.cache.CacheConfig;
import cz.msebera.android.httpclient.util.TextUtils;
import delit.libs.http.UntrustedCaCertificateInterceptingTrustStrategy;
import delit.libs.util.X509Utils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.business.video.CacheUtils;
import delit.piwigoclient.piwigoApi.http.CachingAsyncHttpClient;
import delit.piwigoclient.piwigoApi.http.CachingSyncHttpClient;
import delit.piwigoclient.piwigoApi.http.PersistentProfileCookieStore;
import delit.piwigoclient.piwigoApi.http.cache.RestartableManagedHttpCacheStorage;

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
    private final HashMap<ConnectionPreferences.ProfilePreferences, CachingAsyncHttpClient> videoDownloadClientMap;
    private final HashMap<ConnectionPreferences.ProfilePreferences, CachingSyncHttpClient> videoDownloadSyncClientMap;
    private final Handler handler;
    private RestartableManagedHttpCacheStorage cacheStorage;

    public HttpClientFactory(Context c) {
        Looper eventLooper = Looper.myLooper() != null ? Looper.myLooper() : Looper.getMainLooper();
        handler = new Handler(eventLooper);
        cookieStoreMap = new HashMap<>(3);
        prefs = PreferenceManager.getDefaultSharedPreferences(c.getApplicationContext());
        asyncClientMap = new HashMap<>(3);
        syncClientMap = new HashMap<>(3);
        videoDownloadClientMap = new HashMap<>(3);
        videoDownloadSyncClientMap = new HashMap<>(3);
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
        PersistentProfileCookieStore cookieStore = cookieStoreMap.get(profile);
        if (cookieStore != null) {
            cookieStore.clear();
        }
    }

    public synchronized void cancelAllRunningHttpRequests(ConnectionPreferences.ProfilePreferences profile) {
        if (profile == null) {
            // clear ALL.
            HashSet<ConnectionPreferences.ProfilePreferences> keys = new HashSet<>();
            keys.addAll(asyncClientMap.keySet());
            keys.addAll(syncClientMap.keySet());
            keys.addAll(videoDownloadClientMap.keySet());
            keys.addAll(videoDownloadSyncClientMap.keySet());
            for (ConnectionPreferences.ProfilePreferences aProfile : keys) {
                cancelAllRunningHttpRequests(aProfile);
            }
            return;
        }
        cancelAllRequests(asyncClientMap.get(profile));
        cancelAllRequests(syncClientMap.get(profile));
        cancelAllRequests(videoDownloadClientMap.get(profile));
        cancelAllRequests(videoDownloadSyncClientMap.get(profile));
        flushCookies(profile);
    }

    private void cancelAllRequests(CachingAsyncHttpClient client) {
        if (client != null) {
            client.cancelAllRequests(true);
        }
    }

    public synchronized void clearCachedClients(ConnectionPreferences.ProfilePreferences profile) {
        if (profile == null) {
            // clear ALL.
            HashSet<ConnectionPreferences.ProfilePreferences> keys = new HashSet<>();
            keys.addAll(asyncClientMap.keySet());
            keys.addAll(syncClientMap.keySet());
            keys.addAll(videoDownloadClientMap.keySet());
            keys.addAll(videoDownloadSyncClientMap.keySet());
            for (ConnectionPreferences.ProfilePreferences aProfile : keys) {
                clearCachedClients(aProfile);
            }
            return;
        }
        try {
            closeClient(asyncClientMap.remove(profile));
        } catch (IOException e) {
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error closing asyncClient");
            }
        }
        try {
            closeClient(syncClientMap.remove(profile));
        } catch (IOException e) {
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error closing syncClient");
            }
        }
        try {
            closeClient(videoDownloadClientMap.remove(profile));
        } catch (IOException e) {
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error closing videoDownloadClient");
            }
        }
        try {
            closeClient(videoDownloadSyncClientMap.remove(profile));
        } catch (IOException e) {
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error closing sync videoDownloadClient");
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

    public CachingAsyncHttpClient getVideoDownloadASyncHttpClient(ConnectionPreferences.ProfilePreferences connectionPrefs, Context c) {
        CachingAsyncHttpClient videoDownloadClient = videoDownloadClientMap.get(connectionPrefs);
        if (videoDownloadClient == null) {
            // we use a custom cache solution for video data
            videoDownloadClient = buildHttpClient(connectionPrefs, c, true, true);
            videoDownloadClientMap.put(connectionPrefs, videoDownloadClient);
        }
        return videoDownloadClient;
    }

    public CachingSyncHttpClient buildSyncHttpClient(ConnectionPreferences.ProfilePreferences connectionPrefs, Context c, boolean forceDisableCache) {
        return (CachingSyncHttpClient) buildHttpClient(connectionPrefs, c, false, forceDisableCache);
    }

    public synchronized CachingSyncHttpClient getVideoDownloadSyncHttpClient(ConnectionPreferences.ProfilePreferences connectionPrefs, Context c) {
        CachingSyncHttpClient syncClient = videoDownloadSyncClientMap.get(connectionPrefs);
        if (syncClient == null) {
            syncClient = buildSyncHttpClient(connectionPrefs, c, true);
            videoDownloadSyncClientMap.put(connectionPrefs, syncClient);
        }
        return syncClient;
    }

    public synchronized CachingSyncHttpClient getSyncHttpClient(ConnectionPreferences.ProfilePreferences connectionPrefs, Context c) {
        CachingSyncHttpClient syncClient = syncClientMap.get(connectionPrefs);
        if (syncClient == null) {
            syncClient = buildSyncHttpClient(connectionPrefs, c, false);
            syncClientMap.put(connectionPrefs, syncClient);
        }
        return syncClient;
    }

    public synchronized CachingAsyncHttpClient getAsyncHttpClient(ConnectionPreferences.ProfilePreferences connectionPrefs, Context c) {
        CachingAsyncHttpClient asyncClient = asyncClientMap.get(connectionPrefs);
        if (asyncClient == null) {
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
        int connectTimeoutSecs = connectionPrefs.getServerConnectTimeout(prefs, context);
        int responseTimeoutSecs = connectionPrefs.getServerResponseTimeout(prefs, context);
        client.setConnectTimeout(connectTimeoutSecs * 1000);
        client.setResponseTimeout(responseTimeoutSecs * 1000);
        client.setMaxConcurrentConnections(5);
//        client.setAuthenticationPreemptive(true);
        int connectRetries = connectionPrefs.getMaxServerConnectRetries(prefs, context);
        client.setMaxRetriesAndTimeout(connectRetries, AsyncHttpClient.DEFAULT_RETRY_SLEEP_TIME_MILLIS);
        boolean allowRedirects = connectionPrefs.getFollowHttpRedirects(prefs, context);
        int maxRedirects = connectionPrefs.getMaxHttpRedirects(prefs, context);
        client.setEnableRedirects(allowRedirects, maxRedirects);
        client.setCookieStore(getCookieStore(connectionPrefs, context));
        client.setIgnoreServerCacheDirectives(connectionPrefs.isIgnoreServerCacheDirectives(prefs, context));
        client.setUserAgent("PiwigoClient_"+ BuildConfig.VERSION_NAME);

        if (!forceDisableCache) {
            String cacheLevel = ConnectionPreferences.getCacheLevel(prefs, context);
            if (cacheLevel.equals("disabled")) {
                CacheConfig cacheConfig = CacheConfig.custom()
                        .setMaxCacheEntries(0)
                        .setSharedCache(false)
                        .setMaxObjectSize(0)
                        .build();
                client.setCacheSettings(null, cacheConfig, null);
            } else {
                File cacheFolder = null;
                if (cacheLevel.equals("disk")) {
                    cacheFolder = CacheUtils.getBasicCacheFolder(context);
                }
                int maxCacheEntries = ConnectionPreferences.getMaxCacheEntries(prefs, context);

                int maxCacheEntrySize = ConnectionPreferences.getMaxCacheEntrySizeBytes(prefs, context);

                CacheConfig cacheConfig = CacheConfig.custom()
                        .setMaxCacheEntries(maxCacheEntries)
                        .setSharedCache(false)
                        .setMaxObjectSize(maxCacheEntrySize)
                        .build();

                client.setCacheSettings(cacheFolder, cacheConfig, getCacheStorage(cacheFolder, cacheConfig));
            }
        } else {
            CacheConfig cacheConfig = CacheConfig.custom()
                    .setMaxCacheEntries(0)
                    .setSharedCache(false)
                    .setMaxObjectSize(0)
                    .build();
            client.setCacheSettings(null, cacheConfig, null);
        }

        configureBasicServerAuthentication(connectionPrefs, context, client);

        return client;
    }

    private RestartableManagedHttpCacheStorage getCacheStorage(File cacheFolder, CacheConfig cacheConfig) {
        if (this.cacheStorage == null || !cacheStorage.isActive()) {
            this.cacheStorage = new RestartableManagedHttpCacheStorage(cacheFolder, cacheConfig, handler);
        } else if (cacheConfig.getMaxCacheEntries() != cacheStorage.getMaxCacheEntries()) {
            cacheStorage.setMaxCacheEntries(cacheConfig.getMaxCacheEntries());
        }
        return this.cacheStorage;
    }

    private PersistentProfileCookieStore getCookieStore(ConnectionPreferences.ProfilePreferences connectionPrefs, Context context) {
        synchronized (connectionPrefs) {
            PersistentProfileCookieStore cookieStore = cookieStoreMap.get(connectionPrefs);
            if (cookieStore == null) {
                cookieStore = new PersistentProfileCookieStore(context.getApplicationContext(), connectionPrefs.getAbsoluteProfileKey(prefs, context));
                cookieStoreMap.put(connectionPrefs, cookieStore);
            }
            return cookieStore;
        }
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
                Crashlytics.logException(e);
                e.printStackTrace();
            }
        }

        SSLConnectionSocketFactory sslSocketFactory = null;
        if (sslContext != null) {
            sslSocketFactory = new SSLConnectionSocketFactory(
                    sslContext,
                    split(System.getProperty("https.protocols")),
                    split(System.getProperty("https.cipherSuites")),
                    hostnameVerifier);
        }
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
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error building sslContext", e);
            }
        } catch (UnrecoverableKeyException e) {
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error building sslContext", e);
            }
        } catch (KeyStoreException e) {
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error building sslContext", e);
            }
        } catch (KeyManagementException e) {
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error building sslContext", e);
            }
        }
        return null;
    }

    public boolean isInitialised(ConnectionPreferences.ProfilePreferences connectionProfile) {
        return asyncClientMap.containsKey(connectionProfile) || syncClientMap.containsKey(connectionProfile) || videoDownloadClientMap.containsKey(connectionProfile) || videoDownloadSyncClientMap.containsKey(connectionProfile);
    }

    public void clearCache() {
        if (cacheStorage != null) {
            cacheStorage.shutdown();
        }
    }

    public long getItemsInResponseCache() {
        if (cacheStorage == null) {
            return 0;
        }
        return cacheStorage.getEntryCount();
    }
}
