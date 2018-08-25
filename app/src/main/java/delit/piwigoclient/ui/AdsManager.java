package delit.piwigoclient.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;

import java.net.URI;

import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * Created by gareth on 12/07/17.
 */

public class AdsManager {

    private static final String TAG = "AdMan";
    public static final String BLOCK_MILLIS_PREF = "BLOCK_MILLIS";
    private static AdsManager instance;
    private transient InterstitialAd selectFileToUploadAd;
    private transient InterstitialAd albumBrowsingAd;
    private long lastShowedAdvert;
    private boolean showAds = true;
    private boolean appLicensed = false;
    private transient SharedPreferences prefs;
    private static int advertLoadFailures = 0;

    private AdsManager() {
    }

    public synchronized static AdsManager getInstance() {
        if (instance == null) {
            instance = new AdsManager();
        }
        return instance;
    }

    private SharedPreferences getPrefs(Context context) {
        if (prefs == null) {
            prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        }
        return prefs;
    }

    public void setAppLicensed(boolean appLicensed) {
        this.appLicensed = appLicensed;
    }

    public synchronized void updateShowAdvertsSetting(Context context) {
        String serverAddress = ConnectionPreferences.getActiveProfile().getTrimmedNonNullPiwigoServerAddress(getPrefs(context), context);
        showAds = !BuildConfig.PAID_VERSION;
        if (showAds) {
            // can we disable the ads another way?
            if (!serverAddress.equals("")) {
                try {
                    String host = URI.create(serverAddress).getHost();
                    showAds = BuildConfig.DEBUG || !"sail2port.ddns.net".equals(host);
                } catch (IllegalArgumentException e) {
                    Crashlytics.log(Log.DEBUG, TAG, "Error parsing URI - adverts enabled");
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
            selectFileToUploadAd.setAdListener(new MyAdListener(context, selectFileToUploadAd));

            albumBrowsingAd = new InterstitialAd(context);
            albumBrowsingAd.setAdUnitId(context.getString(R.string.ad_id_album_interstitial));
            albumBrowsingAd.loadAd(new AdRequest.Builder().build());
            albumBrowsingAd.setAdListener(new MyAdListener(context, albumBrowsingAd));
        } else if (showAds) {
            if (!selectFileToUploadAd.isLoading() && !selectFileToUploadAd.isLoaded()) {
                selectFileToUploadAd.loadAd(new AdRequest.Builder().build());
            }
            if (!albumBrowsingAd.isLoading() && !albumBrowsingAd.isLoaded()) {
                albumBrowsingAd.loadAd(new AdRequest.Builder().build());
            }
        }
    }

    public boolean hasAdvertLoadProblem(Context c) {
        if(advertLoadFailures > 10 || getPrefs(c).getLong(BLOCK_MILLIS_PREF, 0) > 0) {
            advertLoadFailures = 0;
            return true;
        }
        return false;
    }

    public boolean shouldShowAdverts() {
        return showAds;
    }

    private synchronized boolean acceptableToShowAdvert(InterstitialAd ad, long minDelayBetweenAds) {
        long currentTime = System.currentTimeMillis();
        if (showAds && ad != null) {
            if (ad.isLoaded() && currentTime - lastShowedAdvert > minDelayBetweenAds) {
                lastShowedAdvert = currentTime;
                return true;
            } else if (!ad.isLoaded() && !ad.isLoading()) {
                ad.loadAd(new AdRequest.Builder().build());
            }
        }
        return false;
    }

    public boolean showFileToUploadAdvertIfAppropriate() {
        if (acceptableToShowAdvert(selectFileToUploadAd, 24 * 60 * 60000)) {
            selectFileToUploadAd.show();
            // show every 24 hours
            return true;
        }
        return false;
    }

    public boolean showAlbumBrowsingAdvertIfAppropriate() {
        if (acceptableToShowAdvert(albumBrowsingAd, 24 * 60 * 60000)) {
            albumBrowsingAd.show();
            // show every 24 hours
            return true;
        }
        return false;
    }

    public static class MyBannerAdListener extends AdListener {
        public static final long DEFAULT_MIN_ADVERT_DISPLAY_TIME_MILLIS = 5000;
        private final long minimumAdVisibleTimeMillis;
        private AdView advertView;
        private int retries;
        private long adLastLoadedAt;
        private boolean lastAdLoadFailed;

        public MyBannerAdListener(AdView advertView) {
            this(advertView, DEFAULT_MIN_ADVERT_DISPLAY_TIME_MILLIS);
        }

        public MyBannerAdListener(AdView advertView, long minimumAdVisibleTimeMillis) {
            this.advertView = advertView;
            this.minimumAdVisibleTimeMillis = minimumAdVisibleTimeMillis;
            advertView.setVisibility(VISIBLE);
            advertView.setAdListener(this);
            if (!advertView.isLoading()) {
                loadAdvert();
            }
        }

        private void loadAdvert() {
            advertView.loadAd(new AdRequest.Builder().build());
        }

        @Override
        public void onAdFailedToLoad(int var1) {
            if (var1 == 3) {
                retries++;
            }
            if (retries < 3) {
                Crashlytics.log(Log.DEBUG, "BannerAd", "advert load failed, retrying");
                loadAdvert();
            } else {
                Crashlytics.log(Log.DEBUG, "BannerAd", "Gave up trying to load advert after 3 attempts");
                advertView.setVisibility(View.GONE);
                advertLoadFailures++;
                lastAdLoadFailed = true;
                retries = 0;
            }
        }

        @Override
        public void onAdLoaded() {
            retries = 0;
            lastAdLoadFailed = false;
            adLastLoadedAt = System.currentTimeMillis();
            advertLoadFailures = 0;
        }

        @Override
        public void onAdClosed() {
            super.onAdClosed();
//            loadAdvert();
        }

        public void replaceAd() {
            boolean adExpired = System.currentTimeMillis() - adLastLoadedAt > minimumAdVisibleTimeMillis;
            if(adExpired && lastAdLoadFailed) {
                advertView.setVisibility(VISIBLE);
            }
            if (!advertView.isLoading() && advertView.getVisibility() == View.VISIBLE && adExpired) {
                retries = 0;
                loadAdvert();
            }
        }
    }

    class MyAdListener extends AdListener {

        private final InterstitialAd ad;
        private final Context context;
        private int onCloseActionId = -1;
        private Intent onCloseIntent;
        private int retries;

        public MyAdListener(Context context, InterstitialAd ad) {
            this.ad = ad;
            this.context = context;
        }

        @Override
        public void onAdClosed() {
            if (onCloseIntent != null) {
                Activity activity = (Activity) context;
                activity.startActivityForResult(onCloseIntent, onCloseActionId);
                onCloseIntent = null;
                onCloseActionId = -1;
            }
            Handler mainHandler = new Handler(context.getMainLooper());
            Runnable myRunnable = new Runnable() {
                @Override
                public void run() {
                    ad.loadAd(new AdRequest.Builder().build());
                }
            };
            mainHandler.post(myRunnable);

        }

        private void loadAdvert() {
            ad.loadAd(new AdRequest.Builder().build());
        }

        @Override
        public void onAdLoaded() {
            retries = 0;
            advertLoadFailures = 0;
        }

        @Override
        public void onAdFailedToLoad(int var1) {
            if (var1 == 3) {
                retries++;
            }
            if (retries < 3) {
                Crashlytics.log(Log.DEBUG, "InterstitialAd", "advert load failed, retrying");
                loadAdvert();
            } else {
                Crashlytics.log(Log.DEBUG, "InterstitialAd", "Gave up trying to load advert after 3 attempts");
                advertLoadFailures++;
                retries = 0;
            }
        }

        public void addAdCloseAction(Intent intent, int actionId) {
            onCloseIntent = intent;
            onCloseActionId = actionId;
        }
    }
}
