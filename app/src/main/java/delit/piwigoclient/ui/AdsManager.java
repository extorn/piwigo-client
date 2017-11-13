package delit.piwigoclient.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;

import java.net.URI;

import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;

/**
 * Created by gareth on 12/07/17.
 */

public class AdsManager {

    private final Context context;
    private transient InterstitialAd selectFileToUploadAd;
    private transient InterstitialAd albumBrowsingAd;
    private long lastShowedAdvert;
    private boolean showAds = true;
    private boolean appLicensed = false;
    private transient SharedPreferences prefs;
    private static AdsManager instance;

    private AdsManager(Context c) {
        this.context = c;
    }

    public synchronized static AdsManager getInstance() {
        return instance;
    }

    public synchronized static AdsManager getInstance(Context context) {
        if(instance == null) {
            instance = new AdsManager(context);
        }
        return instance;
    }

    private SharedPreferences getPrefs() {
        if(prefs == null) {
            prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        }
        return prefs;
    }

    public void setAppLicensed(boolean appLicensed) {
        this.appLicensed = appLicensed;
    }

    public synchronized void updateShowAdvertsSetting() {
        String serverAddress = getPrefs().getString(context.getString(R.string.preference_piwigo_server_address_key), null);
        showAds = !BuildConfig.PAID_VERSION;
        if(showAds) {
            // can we disable the ads another way?
            if (serverAddress != null && !serverAddress.trim().equals("")) {
                try {
                    String host = URI.create(serverAddress).getHost();
                    showAds = !"sail2port.ddns.net".equals(host);
                } catch (IllegalArgumentException e) {
                    showAds = true;
                }
            } else {
                showAds = true;
            }
        }

        if (!appLicensed && showAds && selectFileToUploadAd == null) {
            MobileAds.initialize(context, context.getString(R.string.ad_app_id));
            selectFileToUploadAd = new InterstitialAd(context);
            selectFileToUploadAd.setAdUnitId(context.getString(R.string.ad_id_uploads_interstitial));
            selectFileToUploadAd.loadAd(new AdRequest.Builder().build());
            selectFileToUploadAd.setAdListener(new MyAdListener(selectFileToUploadAd));

            albumBrowsingAd = new InterstitialAd(context);
            albumBrowsingAd.setAdUnitId(context.getString(R.string.ad_id_album_interstitial));
            albumBrowsingAd.loadAd(new AdRequest.Builder().build());
            albumBrowsingAd.setAdListener(new MyAdListener(albumBrowsingAd));
        }
    }

    public boolean shouldShowAdverts() {
        return showAds;
    }

    private synchronized boolean acceptableToShowAdvert(InterstitialAd ad, long minDelayBetweenAds) {
        long currentTime = System.currentTimeMillis();
        if (showAds && ad != null && ad.isLoaded() && currentTime - lastShowedAdvert > minDelayBetweenAds) {
            lastShowedAdvert = currentTime;
            return true;
        }
        return false;
    }

    public boolean showFileToUploadAdvertIfAppropriate() {
        if (acceptableToShowAdvert(selectFileToUploadAd, 24 * 60 * 60000)) {
            selectFileToUploadAd.show();
            // every 5 seconds
            return true;
        }
        return false;
    }

    public boolean showAlbumBrowsingAdvertIfAppropriate() {
        if (acceptableToShowAdvert(albumBrowsingAd, 24 * 60 * 60000)) {
            albumBrowsingAd.show();
            // show every 5 minutes.
            return true;
        }
        return false;
    }

    class MyAdListener extends AdListener {

        private final InterstitialAd ad;

        public MyAdListener(InterstitialAd ad) {
            this.ad = ad;
        }
        @Override
        public void onAdClosed() {
            ad.loadAd(new AdRequest.Builder().build());
        }
    }
}
