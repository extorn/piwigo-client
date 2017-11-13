package delit.piwigoclient.business.video;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;

import com.loopj.android.http.PersistentCookieStore;
import com.squareup.picasso.UrlConnectionDownloader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;

import cz.msebera.android.httpclient.cookie.Cookie;
import delit.piwigoclient.R;
import delit.piwigoclient.ui.preferences.SecurePrefsUtil;

/**
 * Created by gareth on 18/05/17.
 */

public class CookieImageDownloader extends UrlConnectionDownloader {

    private final Context context;
    HttpURLConnection lastConn;

    public CookieImageDownloader(Context context) {
        super(context);
        this.context = context;
    }

    @Override
    protected HttpURLConnection openConnection(Uri path) throws IOException {
        HttpURLConnection conn = super.openConnection(path);

        PersistentCookieStore cookieStore = new PersistentCookieStore(context.getApplicationContext());

        List<Cookie> cookies = cookieStore.getCookies();
        if(cookies.size() > 0) {
            Cookie cookie = cookies.get(0);
            String cookieName = cookie.getName();
            String cookieValue = cookie.getValue();
            conn.setRequestProperty("Cookie", cookieName + "=" + cookieValue);
        }
        lastConn = conn;
        return conn;
    }

    @Override
    public Response load(Uri uri, boolean localCacheOnly) throws IOException {
        try {
            Response r = super.load(uri, localCacheOnly);
            if (r.getContentLength() == -1) {
                if (!lastConn.getURL().toString().equals(uri.toString())) {

                    if (lastConn.getResponseCode() < 300) {
                        // need to forcibly disconnect (pointless connection).
                        // This covers the weird result in a server 200 response that is actually a redirect!
                        lastConn.disconnect();
                    }
                    String newUrl = lastConn.getURL().toString();
                    if (newUrl.contains("/identification.php?redirect=")) {

                        int httpStatusCode = followRedirectAndLoginToServer(lastConn.getURL());
                        if (httpStatusCode == 200) {
                            // get the resource again directly - not this method to avoid infinite recursion
                            return super.load(uri, localCacheOnly);
                        }
                    } else {
                        return r;
                    }
                }
            }
            return r;
        } catch (ResponseException e) {
            throw e;
        }
    }

    private int followRedirectAndLoginToServer(URL url) throws IOException {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SecurePrefsUtil prefUtil = SecurePrefsUtil.getInstance(context);
        String username = prefUtil.readSecureStringPreference(prefs, context.getString(R.string.preference_piwigo_server_username_key), null);
        String password = prefUtil.readSecureStringPreference(prefs, context.getString(R.string.preference_piwigo_server_password_key), null);
        String path = lastConn.getURL().getPath();
        String query = lastConn.getURL().getQuery();

        String newUri = lastConn.getURL().toString();
        newUri = newUri.substring(0, newUri.indexOf(query) - 1);
        String redirectTo = query.substring("redirect=".length());

        openConnection(Uri.parse(newUri));
        StringBuilder postBody = new StringBuilder("login=Submit");
        postBody.append("&username=");
        postBody.append(URLEncoder.encode(username, "UTF-8"));
        postBody.append("&password=");
        postBody.append(URLEncoder.encode(password, "UTF-8"));
        postBody.append("&redirect=");
        postBody.append(redirectTo);
        lastConn.setRequestMethod("POST");
        OutputStream os = lastConn.getOutputStream();
        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(os, "UTF-8"));
        writer.write(postBody.toString());
        writer.flush();
        writer.close();
        os.close();

        int httpStatusCode = lastConn.getResponseCode();
        lastConn.disconnect();
        return httpStatusCode;
    }
}