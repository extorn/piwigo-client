package delit.piwigoclient.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.reward.AdMetadataListener;
import com.google.android.gms.ads.reward.RewardItem;
import com.google.android.gms.ads.reward.RewardedVideoAd;
import com.google.android.gms.ads.reward.RewardedVideoAdListener;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.greenrobot.eventbus.EventBus;

import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.net.URI;
import java.util.Calendar;
import java.util.GregorianCalendar;

import delit.libs.ui.util.SecurePrefsUtil;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.ui.events.RewardUpdateEvent;

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
    private boolean advertsDisabled;

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

    public void setAdvertsDisabled(boolean advertsDisabled) {
        this.advertsDisabled = advertsDisabled;
    }

    public MyRewardedAdControl getRewardedVideoAd(Activity activity, OnRewardEarnedListener onRewardListener) {
        // Use an activity context to get the rewarded video instance.
        RewardedVideoAd rewardedVideoAd = MobileAds.getRewardedVideoAdInstance(activity);
        MyRewardedAdControl listener = new MyRewardedAdControl(activity.getString(R.string.ad_id_reward_ad), rewardedVideoAd, onRewardListener);
        rewardedVideoAd.setRewardedVideoAdListener(listener);
        listener.loadAdvert();
        return listener;
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
            }
        }

        if (!appLicensed && showAds && selectFileToUploadAd == null) {
            MobileAds.initialize(context, context.getString(R.string.ad_app_id));
            selectFileToUploadAd = new InterstitialAd(context);
            selectFileToUploadAd.setAdUnitId(context.getString(R.string.ad_id_uploads_interstitial));
            selectFileToUploadAd.loadAd(new AdRequest.Builder().addTestDevice("91A207EEC1618AE36FFA9D797319F482").build());
            selectFileToUploadAd.setAdListener(new MyAdListener(context, selectFileToUploadAd));

            albumBrowsingAd = new InterstitialAd(context);
            albumBrowsingAd.setAdUnitId(context.getString(R.string.ad_id_album_interstitial));
            albumBrowsingAd.loadAd(new AdRequest.Builder().addTestDevice("91A207EEC1618AE36FFA9D797319F482").build());
            albumBrowsingAd.setAdListener(new MyAdListener(context, albumBrowsingAd));
        } else if (showAds) {
            if (!selectFileToUploadAd.isLoading() && !selectFileToUploadAd.isLoaded()) {
                selectFileToUploadAd.loadAd(new AdRequest.Builder().addTestDevice("91A207EEC1618AE36FFA9D797319F482").build());
            }
            if (!albumBrowsingAd.isLoading() && !albumBrowsingAd.isLoaded()) {
                albumBrowsingAd.loadAd(new AdRequest.Builder().addTestDevice("91A207EEC1618AE36FFA9D797319F482").build());
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
        return showAds && !advertsDisabled;
    }

    private synchronized boolean acceptableToShowAdvert(InterstitialAd ad, long minDelayBetweenAds) {
        long currentTime = System.currentTimeMillis();
        Calendar c = GregorianCalendar.getInstance();
        c.set(Calendar.YEAR, 2020); // this year
        c.set(Calendar.MONTH, 5);//June
        c.set(Calendar.DAY_OF_MONTH, 1); // 1st

        if (Calendar.getInstance().getTime().after(c.getTime()) && showAds && !advertsDisabled && ad != null) {
            if (ad.isLoaded() && currentTime - lastShowedAdvert > minDelayBetweenAds) {
                lastShowedAdvert = currentTime;
                return true;
            } else if (!ad.isLoaded() && !ad.isLoading()) {
                ad.loadAd(new AdRequest.Builder().addTestDevice("91A207EEC1618AE36FFA9D797319F482").build());
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
            advertView.loadAd(new AdRequest.Builder().addTestDevice("91A207EEC1618AE36FFA9D797319F482").build());
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

    public static class OnRewardEarnedListener {


        private final Context context;
        private final SharedPreferences sharedPreferences;
        private final SecurePrefsUtil prefUtil;
        private final long rewardCountUpdateFrequency;

        public OnRewardEarnedListener(Context context, long rewardCountUpdateFrequency) {
            this.context = context;
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            prefUtil = SecurePrefsUtil.getInstance(context);
            this.rewardCountUpdateFrequency = rewardCountUpdateFrequency;
        }

        public void onRewardEarned(RewardItem rewardItem, long adDisplayTimeMaximum) {

            byte[] oldVal = prefUtil.readSecurePreferenceRawBytes(sharedPreferences, context.getString(R.string.preference_advert_free_time_key), null);
            BigInteger endsAt = BigInteger.valueOf(System.currentTimeMillis());
            if (oldVal != null) {
                endsAt = endsAt.max(new BigInteger(oldVal));
            }
            long rewardTime = Math.min(rewardItem.getAmount() * 1000, adDisplayTimeMaximum * 5); // time declared in advert portal for each advert vs 5 * time ad watched for
            rewardTime = Math.min(rewardTime, 300000); // 5 minutes absolute maximum!
            endsAt = endsAt.add(BigInteger.valueOf(rewardTime));

            long totalRewardTime = endsAt.longValue() - System.currentTimeMillis();
            Bundle bundle = new Bundle();
            bundle.putLong("Reward_Time_Added", (rewardTime / 1000));
            bundle.putLong("User_Reward_Time_Remaining", (totalRewardTime / 1000));
            bundle.putInt("app_version", BuildConfig.VERSION_CODE);
            bundle.putString("app_version_name", BuildConfig.VERSION_NAME);
            FirebaseAnalytics.getInstance(context).logEvent("User_Rewarded", bundle);
            prefUtil.writeSecurePreference(sharedPreferences, context.getString(R.string.preference_advert_free_time_key), endsAt.toByteArray());
            AdsManager.RewardCountDownAction action = AdsManager.RewardCountDownAction.getInstance(context, rewardCountUpdateFrequency);
            action.start();
        }
    }

    public static final class MyRewardedAdControl extends AdMetadataListener implements RewardedVideoAdListener {

        private final String advertId;
        private final RewardedVideoAd rewardedVideoAd;
        private final OnRewardEarnedListener listener;
        private int retries;
        private boolean isLoading;
        private long adDisplayAt;
        private Handler h;
        private long adDisplayedFor;

        public MyRewardedAdControl(String advertId, RewardedVideoAd rewardedVideoAd, OnRewardEarnedListener listener) {
            this.rewardedVideoAd = rewardedVideoAd;
            rewardedVideoAd.setAdMetadataListener(this);
            if (BuildConfig.DEBUG) {
                this.advertId = "ca-app-pub-3940256099942544/5224354917"; // test ID
            } else {
                this.advertId = advertId;
            }
            this.listener = listener;
            h = new Handler(Looper.getMainLooper());
        }

        @Override
        public void onAdMetadataChanged() {
        }

        @Override
        public void onRewardedVideoAdLoaded() {
            isLoading = false;
        }

        @Override
        public void onRewardedVideoAdOpened() {
            adDisplayedFor = 0;
            adDisplayAt = System.currentTimeMillis();
        }

        @Override
        public void onRewardedVideoStarted() {
        }

        @Override
        public void onRewardedVideoAdClosed() {
            loadAdvert();
        }

        public void pause(Context context) {
            rewardedVideoAd.pause(context);
        }

        public boolean show() {
            if (rewardedVideoAd.isLoaded()) {
                rewardedVideoAd.show();
                return true;
            }
            loadAdvert();
            return false;
        }

        public void resume(Context context) {
            rewardedVideoAd.resume(context);
        }

        public void destroy(Context context) {
            rewardedVideoAd.destroy(context);
        }

        @Override
        public void onRewarded(RewardItem rewardItem) {
            adDisplayedFor = System.currentTimeMillis() - adDisplayAt;
            listener.onRewardEarned(rewardItem, adDisplayedFor);


        }

        @Override
        public void onRewardedVideoAdLeftApplication() {

        }

        @Override
        public void onRewardedVideoAdFailedToLoad(int loadFailureCount) {
            isLoading = false;
            if (loadFailureCount == 3) {
                retries++;
            }
            if (retries < 3) {
                Crashlytics.log(Log.DEBUG, "RewardVidAd", "advert load failed, retrying");
                loadAdvert();
            } else {
                Crashlytics.log(Log.DEBUG, "RewardVidAd", "Gave up trying to load advert after 3 attempts");
                advertLoadFailures++;
                retries = 0;
            }
        }

        @Override
        public void onRewardedVideoCompleted() {
            loadAdvert();
        }

        void loadAdvert() {
            if (!rewardedVideoAd.isLoaded() || !isLoading) {
                if (Looper.myLooper() != Looper.getMainLooper()) {
                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            // always load adverts on the main thread!
                            loadAdvert();
                        }
                    });
                    return;
                }

                isLoading = true;
                rewardedVideoAd.loadAd(advertId,
                        new AdRequest.Builder().addTestDevice("91A207EEC1618AE36FFA9D797319F482").build());
            }
        }
    }

    public final static class RewardCountDownAction implements Runnable {

        private static RewardCountDownAction instance;
        private final WeakReference<Context> contextWeakReference;
        private final String prefKey;
        private final long callFrequency;
        private final Handler h;
        private boolean stopped;
        private long calledAt;

        public RewardCountDownAction(Context c, long callFrequency) {
            if (callFrequency < 1000) {
                throw new IllegalArgumentException("Cannot be called more frequently than 1000ms");
            }
            this.contextWeakReference = new WeakReference<>(c);
            prefKey = c.getString(R.string.preference_advert_free_time_key);
            this.callFrequency = callFrequency;
            h = new Handler(Looper.getMainLooper());
        }

        public static RewardCountDownAction getActiveInstance(Context c) {
            return instance;
        }

        public static RewardCountDownAction getInstance(Context c, long callFrequency) {
            if (instance == null) {
                instance = new RewardCountDownAction(c, callFrequency);
            }
            return instance;
        }

        public void start() {
            updatePreference(false, 0);
            runInMillis(callFrequency);
        }

        public void runInMillis(long delay) {
            synchronized (this) {
                stopped = false;
                calledAt = System.currentTimeMillis();
                h.postDelayed(this, delay);
            }
        }

        public void stop() {
            h.removeCallbacks(this);
            stopped = true;
            synchronized (this) {
                updatePreference(stopped);
            }
        }

        @Override
        public void run() {
            synchronized (this) {
                if (stopped) {
                    return;
                }
                boolean runAgain = updatePreference(stopped);
                if (runAgain) {
                    runInMillis(callFrequency);
                } else {
                    stopped = true;
                }
            }
        }

        private boolean updatePreference(boolean stoppedEarly) {
            long elapsedTime = Math.min(System.currentTimeMillis() - calledAt, callFrequency);
            return updatePreference(stoppedEarly, elapsedTime);
        }

        private boolean updatePreference(boolean stoppedEarly, long elapsedTime) {
            long now = System.currentTimeMillis();
            Context c = contextWeakReference.get();
            if (c == null) {
                // somehow, this context has died without killing this background thread. Kill the thread too.
                return false;
            }
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
            SecurePrefsUtil prefUtil = SecurePrefsUtil.getInstance(c);
            byte[] oldVal = prefUtil.readSecurePreferenceRawBytes(prefs, prefKey, null);
            boolean showAds = true;
            long timeRemainingWithoutAds = 0;
            if (oldVal != null) {
                BigInteger val = new BigInteger(oldVal);
                if (val.longValue() > now) {
                    val = val.subtract(BigInteger.valueOf(elapsedTime));
                    prefUtil.writeSecurePreference(prefs, prefKey, val.toByteArray());
                    timeRemainingWithoutAds = val.longValue() - now;
                    timeRemainingWithoutAds = Math.max(timeRemainingWithoutAds, 0);
                    showAds = timeRemainingWithoutAds <= 0;

                }
            }
            EventBus.getDefault().post(new RewardUpdateEvent(timeRemainingWithoutAds));
            AdsManager.getInstance().setAdvertsDisabled(!showAds);
            return !stoppedEarly && !showAds;
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
                    ad.loadAd(new AdRequest.Builder().addTestDevice("91A207EEC1618AE36FFA9D797319F482").build());
                }
            };
            mainHandler.post(myRunnable);

        }

        private void loadAdvert() {
            ad.loadAd(new AdRequest.Builder().addTestDevice("91A207EEC1618AE36FFA9D797319F482").build());
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
