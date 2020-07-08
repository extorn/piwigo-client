package delit.piwigoclient.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.ads.consent.AdProvider;
import com.google.ads.consent.ConsentForm;
import com.google.ads.consent.ConsentFormListener;
import com.google.ads.consent.ConsentInfoUpdateListener;
import com.google.ads.consent.ConsentInformation;
import com.google.ads.consent.ConsentStatus;
import com.google.ads.consent.DebugGeography;
import com.google.ads.mediation.admob.AdMobAdapter;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.reward.AdMetadataListener;
import com.google.android.gms.ads.reward.RewardItem;
import com.google.android.gms.ads.reward.RewardedVideoAd;
import com.google.android.gms.ads.reward.RewardedVideoAdListener;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import org.greenrobot.eventbus.EventBus;

import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.list.BaseRecyclerAdapter;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.ui.album.view.AbstractViewAlbumFragment;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.RewardUpdateEvent;
import delit.piwigoclient.ui.preferences.SecurePrefsUtil;

import static android.view.View.VISIBLE;

/**
 * Created by gareth on 12/07/17.
 */

public class AdsManager {

    private static final String TAG = "AdMan";
    public static final String BLOCK_MILLIS_PREF = "BLOCK_MILLIS";
    private static AdsManager instance;
    private InterstitialAd selectFileToUploadAd;
    private InterstitialAd albumBrowsingAd;
    private long lastShowedAdvert;
    private boolean showAds = true;
    private boolean appLicensed = false;
    private SharedPreferences prefs;
    private static int advertLoadFailures = 0;
    private boolean advertsDisabled;
    private boolean interstitialShowing;
    private Boolean lastAdPaid;
    private ConsentForm euConsentForm;
    private MyRewardedAdControl rewardVideoAd;
    private static final int STOPPED = -1;
    private static final int INITIALISING = 0;
    private static final int STARTED = 1;
    private int status = STOPPED;

    private AdsManager() {
    }

    public synchronized static AdsManager getInstance(Context context) {
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

    public MyRewardedAdControl getRewardedVideoAd(Context context, OnRewardEarnedListener onRewardListener) {
        // Use an activity context to get the rewarded video instance.
        RewardedVideoAd rewardedVideoAd = MobileAds.getRewardedVideoAdInstance(context);
        MyRewardedAdControl listener = new MyRewardedAdControl(context, context.getString(R.string.ad_id_reward_ad), rewardedVideoAd, onRewardListener);
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
                    Logging.log(Log.DEBUG, TAG, "Error parsing URI - adverts enabled");
                    showAds = true;
                }
            }
        }

        if (!appLicensed && showAds && status == STOPPED) {
            status = INITIALISING;
            getConsentInformationAndInitialiseAdverts(context);
        } else if (showAds && status == STARTED) {
            if (!selectFileToUploadAd.isLoading() && !selectFileToUploadAd.isLoaded()) {
                selectFileToUploadAd.loadAd(buildAdRequest(context));
            }
            if (!albumBrowsingAd.isLoading() && !albumBrowsingAd.isLoaded()) {
                albumBrowsingAd.loadAd(buildAdRequest(context));
            }
        }

        updateConsentInformation(getConsentInformation(context));
    }

    private void updateConsentInformation(ConsentInformation consentInformation) {
        String[] publisherIds = {"pub-1408465472557768"};
        consentInformation.requestConsentInfoUpdate(publisherIds, new ConsentInfoUpdateListener() {
            @Override
            public void onConsentInfoUpdated(ConsentStatus consentStatus) {
                // User's consent status successfully updated.
            }

            @Override
            public void onFailedToUpdateConsentInfo(String errorDescription) {
                // User's consent status failed to update.
                Logging.log(Log.ERROR, TAG, "ConsentUpdateFailed: "+errorDescription);
            }
        });
    }

    private void initAdService(Context context) {
        MobileAds.initialize(context, initializationStatus -> {
            initialiseAllAdverts(context);
        });
    }

    private void initialiseAllAdverts(Context context) {

        if(BuildConfig.DEBUG) {
            // Only treat devices as test devices if app is in debug mode
            List<String> testDeviceIds = Collections.singletonList("91A207EEC1618AE36FFA9D797319F482");
            RequestConfiguration configuration =
                    new RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build();
            MobileAds.setRequestConfiguration(configuration);
        }
        selectFileToUploadAd = new InterstitialAd(context);
        selectFileToUploadAd.setAdUnitId(context.getString(R.string.ad_id_uploads_interstitial));
        selectFileToUploadAd.loadAd(buildAdRequest(context));
        selectFileToUploadAd.setAdListener(new MyAdListener(context, selectFileToUploadAd));

        albumBrowsingAd = new InterstitialAd(context);
        albumBrowsingAd.setAdUnitId(context.getString(R.string.ad_id_album_interstitial));
        albumBrowsingAd.loadAd(buildAdRequest(context));
        albumBrowsingAd.setAdListener(new MyAdListener(context, albumBrowsingAd));
        status = STARTED;
    }

    public boolean hasAdvertLoadProblem(Context c) {
        if(advertLoadFailures > 10 || getPrefs(c).getLong(BLOCK_MILLIS_PREF, 0) > 0) {
            advertLoadFailures = 0;
            return true;
        }
        return false;
    }

    public boolean shouldShowAdverts() {
        return status == STARTED && showAds && !advertsDisabled;
    }

    private synchronized boolean acceptableToShowAdvert(Context context, InterstitialAd ad, long minDelayBetweenAds) {

        long currentTime = System.currentTimeMillis();

        if (showAds && !advertsDisabled && ad != null) {
            if (ad.isLoaded() && (null == lastAdPaid || !lastAdPaid || (currentTime - lastShowedAdvert) > minDelayBetweenAds)) {
                lastShowedAdvert = currentTime;
                return true;
            } else if (!ad.isLoaded() && !ad.isLoading()) {
                ad.loadAd(buildAdRequest(context));
            }
        }
        return false;
    }

    public boolean showFileToUploadAdvertIfAppropriate(Context context) {
        if (acceptableToShowAdvert(context, selectFileToUploadAd, 24 * 60 * 60000)) {
            showInterstitialAd(context, selectFileToUploadAd);
            // show every 24 hours
            return true;
        }
        return false;
    }

    private void showInterstitialAd(Context context, InterstitialAd ad) {
        interstitialShowing = true;
        if(lastAdPaid == null) {
            lastAdPaid = false;
        }
        ad.setOnPaidEventListener(adValue -> lastAdPaid = true);
        ad.setAdListener(new InterstitialAdListener(context));
        ad.show();
    }

    public boolean isInterstitialShowing() {
        return interstitialShowing;
    }

    public boolean isLastAdShownAndUnpaid() {
        return lastAdPaid != null && !lastAdPaid;
    }

    public boolean showAlbumBrowsingAdvertIfAppropriate(Context context) {
        if (acceptableToShowAdvert(context, albumBrowsingAd, 24 * 60 * 60000)) {
            showInterstitialAd(context, albumBrowsingAd);
            // show every 24 hours
            return true;
        }
        return false;
    }

    public void showPleadingMessageIfNeeded(FragmentUIHelper<AbstractViewAlbumFragment> uiHelper) {
        if(isLastAdShownAndUnpaid()) {
            lastAdPaid = null;
            uiHelper.showOrQueueDialogQuestion(R.string.alert_warning, uiHelper.getString(R.string.alert_advert_importance_message), View.NO_ID, R.string.button_ok, new AdvertPleadingListener(uiHelper));
        }
    }

    public void showPrivacyForm(Context context) {
        showConsentForm(context);
    }

    private void showConsentForm(Context context) {
        ConsentInformation consentInfo = getConsentInformation(context);
        if(consentInfo.isRequestLocationInEeaOrUnknown()) {
            buildEuConsentForm(context, consentInfo.getAdProviders());
        } else {
            buildNonEuConsentForm(context, consentInfo.getAdProviders());
        }
    }

    public void createRewardedVideoAd(Activity activity, long updateFrequency) {
        if(status != STARTED) {
            return;
        }
        rewardVideoAd = getRewardedVideoAd(activity, new AdsManager.OnRewardEarnedListener(activity, updateFrequency, BuildConfig.APPLICATION_ID));
    }

    public void pauseRewardVideoAd(Context context) {
        if(rewardVideoAd == null) {
            return;
        }
        rewardVideoAd.pause(context);
    }

    public void resumeRewardVideoAd(Context context) {
        if(rewardVideoAd == null) {
            return;
        }
        rewardVideoAd.resume(context);
    }

    public void destroyRewardVideoAd(Context context) {
        if(rewardVideoAd == null) {
            return;
        }
        rewardVideoAd.destroy(context);
    }

    public boolean hasRewardVideoAd(Context context) {
        return rewardVideoAd != null;
    }

    public boolean showRewardVideoAd(Context context) {
        if(rewardVideoAd == null) {
            return false;
        }
        return rewardVideoAd.show();
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
                loadAdvert(advertView.getContext());
            }
        }

        private void loadAdvert(Context context) {
            try {
                advertView.loadAd(AdsManager.getInstance(context).buildAdRequest(context));
            } catch(NullPointerException e) {
                if(BuildConfig.DEBUG) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    String adName = DisplayUtils.getResourceName(advertView.getContext(), advertView.getId());
                    FirebaseCrashlytics.getInstance().log("Advert was unable to load : " + adName);
                } else {
                    throw e;
                }
            }
        }

        @Override
        public void onAdFailedToLoad(int var1) {
            if (var1 == 3) {
                retries++;
            }
            if (retries < 3) {
                Logging.log(Log.DEBUG, "BannerAd", "advert load failed, retrying");
                loadAdvert(advertView.getContext());
            } else {
                Logging.log(Log.DEBUG, "BannerAd", "Gave up trying to load advert after 3 attempts");
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
                loadAdvert(advertView.getContext());
            }
        }
    }

    public static class OnRewardEarnedListener {


        private final Context context;
        private final SharedPreferences sharedPreferences;
        private final SecurePrefsUtil prefUtil;
        private final long rewardCountUpdateFrequency;

        public OnRewardEarnedListener(Context context, long rewardCountUpdateFrequency, String appId) {
            this.context = context;
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            prefUtil = SecurePrefsUtil.getInstance(context, appId);
            this.rewardCountUpdateFrequency = rewardCountUpdateFrequency;
        }

        public void onRewardEarned(RewardItem rewardItem, long adDisplayTimeMaximum) {

            byte[] oldVal = prefUtil.readSecurePreferenceRawBytes(context, sharedPreferences, context.getString(R.string.preference_advert_free_time_key), null);
            BigInteger endsAt = BigInteger.valueOf(System.currentTimeMillis());
            if (oldVal != null) {
                endsAt = endsAt.max(new BigInteger(oldVal));
            }
            long rewardTime = Math.min(rewardItem.getAmount() * 1000, adDisplayTimeMaximum * 10); // time declared in advert portal for each advert vs 10 * time ad watched for
            rewardTime = Math.min(rewardTime, 720000); // 12 minutes absolute maximum!
            rewardTime = Math.max(rewardTime, 360000); // 6 minutes absolute minimum!
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
        private final Context appCtx;
        private int retries;
        private boolean isLoading;
        private long adDisplayAt;
        private Handler h;
        private long adDisplayedFor;

        public MyRewardedAdControl(Context context, String advertId, RewardedVideoAd rewardedVideoAd, OnRewardEarnedListener listener) {
            this.appCtx = context.getApplicationContext();
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
                Logging.log(Log.DEBUG, "RewardVidAd", "advert load failed, retrying");
                loadAdvert();
            } else {
                Logging.log(Log.DEBUG, "RewardVidAd", "Gave up trying to load advert after 3 attempts");
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
                    h.post(() -> {
                        // always load adverts on the main thread!
                        loadAdvert();
                    });
                    return;
                }

                isLoading = true;
                rewardedVideoAd.loadAd(advertId, AdsManager.getInstance(appCtx).buildAdRequest(appCtx));
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
            SecurePrefsUtil prefUtil = SecurePrefsUtil.getInstance(c, BuildConfig.APPLICATION_ID);
            byte[] oldVal = prefUtil.readSecurePreferenceRawBytes(c, prefs, prefKey, null);
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
            AdsManager.getInstance(c).setAdvertsDisabled(!showAds);
            return !stoppedEarly && !showAds;
        }
    }

    static class MyAdListener extends AdListener {

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
            Runnable myRunnable = () -> ad.loadAd(AdsManager.getInstance(context).buildAdRequest(context));
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
                Logging.log(Log.DEBUG, "InterstitialAd", "advert load failed, retrying");
                loadAdvert();
            } else {
                Logging.log(Log.DEBUG, "InterstitialAd", "Gave up trying to load advert after 3 attempts");
                advertLoadFailures++;
                retries = 0;
            }
        }

        public void addAdCloseAction(Intent intent, int actionId) {
            onCloseIntent = intent;
            onCloseActionId = actionId;
        }
    }

    private static class AdvertPleadingListener extends delit.piwigoclient.ui.common.UIHelper.QuestionResultAdapter implements Parcelable {
        public AdvertPleadingListener(UIHelper uiHelper) {
            super(uiHelper);
        }

        protected AdvertPleadingListener(Parcel in) {
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

        public static final Creator<AdvertPleadingListener> CREATOR = new Creator<AdvertPleadingListener>() {
            @Override
            public AdvertPleadingListener createFromParcel(Parcel in) {
                return new AdvertPleadingListener(in);
            }

            @Override
            public AdvertPleadingListener[] newArray(int size) {
                return new AdvertPleadingListener[size];
            }
        };

        @Override
        public void onDismiss(AlertDialog dialog) {
            FirebaseAnalytics.getInstance(dialog.getContext()).logEvent("ad_warning_shown", null);
        }
    }

    private static class MyConsentFormListener extends ConsentFormListener {
        private final Context context;

        public MyConsentFormListener(Context context) {
            this.context = context;
        }

        @Override
        public void onConsentFormLoaded() {
            AdsManager.getInstance(context).showEuConsentForm();
        }

        @Override
        public void onConsentFormOpened() {
            // Consent form was displayed.
            Logging.log(Log.DEBUG,TAG, "EU Consent form opened");
        }

        @Override
        public void onConsentFormClosed(ConsentStatus consentStatus, Boolean userPrefersAdFree) {
            // Consent form was closed.
            Logging.log(Log.DEBUG,TAG, "EU Consent form closed");
            if(userPrefersAdFree) {
                AdsManager.getInstance(context).openPlayStoreForPaidApp(context);
            }
            AdsManager.getInstance(context).markReducedDataProcessingFlag(ConsentStatus.PERSONALIZED != consentStatus);
            AdsManager.getInstance(context).getConsentInformation(context).setConsentStatus(consentStatus);

            AdsManager.getInstance(context).initialiseAllAdverts(context);
        }

        @Override
        public void onConsentFormError(String errorDescription) {
            Logging.log(Log.DEBUG,TAG, "EU Consent form error : " + errorDescription);
        }
    }

    private void openPlayStoreForPaidApp(Context context) {
        final String appPackageName = context.getPackageName() + ".paid";
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
        } catch (android.content.ActivityNotFoundException anfe) {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
        }
    }

    private boolean isSendReducedDataProcessingFlag() {
        return prefs.getInt("gad_rdp", 0) == 1;
    }

    private void markReducedDataProcessingFlag(boolean enabled) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("gad_rdp", enabled ? 1 : 0);
        editor.commit();
    }

    //TODO Use this adrequest for all adverts.
    //TODO work out what to do with the IABUSPrivacy_String consent!
    private AdRequest buildAdRequest(Context context) {
        ConsentStatus consentStatus = getConsentInformation(context).getConsentStatus();
        Bundle networkExtrasBundle = new Bundle();
        if(consentStatus == ConsentStatus.PERSONALIZED) {
            networkExtrasBundle.putString("npa", "0");
        } else /*if(consentStatus == ConsentStatus.NON_PERSONALIZED) */{
            // default to non personalized if no consent available.
            networkExtrasBundle.putString("npa", "1");
        }
        networkExtrasBundle.putInt("rdp", isSendReducedDataProcessingFlag() ? 1 : 0);
        
        //networkExtrasBundle.putString("IABUSPrivacy_String","1YN"); //1YN is a sample - not real - find real code as per user selection
        return new AdRequest.Builder()
                .addNetworkExtrasBundle(AdMobAdapter.class, networkExtrasBundle)
                .build();
    }

    private class InterstitialAdListener extends AdListener {

        private final WeakReference<Context> contextRef;
        long startedAt = System.currentTimeMillis();

        private InterstitialAdListener(Context context) {
            this.contextRef = new WeakReference<>(context);
        }

        @Override
        public void onAdImpression() {
            super.onAdImpression();
            lastAdPaid = false;
        }

        @Override
        public void onAdClosed() {
            super.onAdClosed();
            if(lastAdPaid == null || !lastAdPaid) {
                long adShowingFor = System.currentTimeMillis() - startedAt;
                if (adShowingFor > 8000) {
                    lastAdPaid = Boolean.TRUE;
                    Context context = contextRef.get();
                    if(context != null) {
                        FirebaseAnalytics.getInstance(context).logEvent("ad_watched_minimum", null);
                    }
                }
            }
            interstitialShowing = false;
        }
    }

    private ConsentInformation getConsentInformation(Context context) {
        ConsentInformation consentInformation = ConsentInformation.getInstance(context);
        if(BuildConfig.DEBUG) {
            consentInformation.addTestDevice("91A207EEC1618AE36FFA9D797319F482"); //Nexus6 device.
            consentInformation.addTestDevice(AdRequest.DEVICE_ID_EMULATOR); //Emulators
            consentInformation.setDebugGeography(DebugGeography.DEBUG_GEOGRAPHY_EEA);
        }
        return consentInformation;
    }

    private void getConsentInformationAndInitialiseAdverts(Context context) {
        ConsentInformation consentInformation = getConsentInformation(context);
        List<AdProvider> adProviders = consentInformation.getAdProviders();
            String[] publisherIds = {"pub-1408465472557768"};
        consentInformation.requestConsentInfoUpdate(publisherIds, new ConsentInfoUpdateListener() {
            @Override
            public void onConsentInfoUpdated(ConsentStatus consentStatus) {
                // User's consent status successfully updated.
                if (ConsentInformation.getInstance(context).isRequestLocationInEeaOrUnknown()) {
                    switch (consentStatus) {
                        case UNKNOWN:
                            buildEuConsentForm(context, adProviders);
                            break;
                        case PERSONALIZED:
                            initAdService(context);
                            break;
                        case NON_PERSONALIZED:
                            initAdService(context);
                            break;
                    }
                } else {
                    buildNonEuConsentForm(context, adProviders);
                }
            }
            @Override
            public void onFailedToUpdateConsentInfo(String errorDescription) {
                // User's consent status failed to update.
            }
        });
    }

    private void buildNonEuConsentForm(Context context, List<AdProvider> adProviders) {
        if(!DisplayUtils.canShowDialog(context)) {
            return;
        }
        MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(new ContextThemeWrapper(context, R.style.Theme_App_EditPages));
        View view = LayoutInflater.from(dialogBuilder.getContext()).inflate(R.layout.layout_dialog_advert_consent_non_eu, null);
        SwitchMaterial personalisedAdsField = view.findViewById(R.id.personalised_adverts_field);
        personalisedAdsField.setChecked(isSendReducedDataProcessingFlag());
        TextView privacyField = view.findViewById(R.id.privacy_policy);
        TextView noAdvertsField = view.findViewById(R.id.no_adverts_field);
        noAdvertsField.setOnClickListener(v -> openPlayStoreForPaidApp(v.getContext()));
        privacyField.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = Uri.parse(v.getContext().getString(R.string.privacy_policy_uri));
            intent.setDataAndType(uri, "text/html");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            v.getContext().startActivity(Intent.createChooser(intent, v.getContext().getString(R.string.open_link)));
        });
        RecyclerView adProvidersListView = view.findViewById(R.id.ad_providers_list);
        adProvidersListView.setLayoutManager(new LinearLayoutManager(view.getContext()));
        RecyclerView.Adapter adProvidersAdapter = new AdProviderAdapter(view.getContext(), adProviders);
        adProvidersListView.setAdapter(adProvidersAdapter);

        dialogBuilder.setView(view);

        // Set up the buttons
        dialogBuilder.setPositiveButton(R.string.button_save, (dialog, which) -> {
                SwitchMaterial field = Objects.requireNonNull(((AlertDialog) dialog).getWindow()).getDecorView().findViewById(R.id.personalised_adverts_field);
                dialog.cancel();
                AdsManager.getInstance(context).markReducedDataProcessingFlag(!field.isChecked());
                updateConsentInformation(context, field.isChecked()?ConsentStatus.PERSONALIZED:ConsentStatus.NON_PERSONALIZED);
            });
        // don't allow the user to cancel as we need this set up before we can begin serving adverts.
        //dialogBuilder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());
        dialogBuilder.setCancelable(false);

        if(DisplayUtils.canShowDialog(context)) {
            dialogBuilder.show();
        }
    }

    private void updateConsentInformation(Context context, ConsentStatus consentStatus) {
        getConsentInformation(context).setConsentStatus(consentStatus);
        initialiseAllAdverts(context);
    }

    private void buildEuConsentForm(Context context, List<AdProvider> adProviders) {
        buildNonEuConsentForm(context, adProviders);
        /* This doesn't work - I don't think google intended this as anything other than perhaps an example for testing.
        URL privacyUrl = null;
        try {
            privacyUrl = new URL(context.getString(R.string.privacy_policy_uri));
        } catch (MalformedURLException e) {
            Logging.log(Log.ERROR, TAG, "Unable to build uri from privacy uri");
            Logging.recordException(e);
        }
        MyConsentFormListener listener = new MyConsentFormListener(context);
        this.euConsentForm = new ConsentForm.Builder(context, privacyUrl)
                .withListener(listener)
                .withPersonalizedAdsOption()
                .withNonPersonalizedAdsOption()
                .withAdFreeOption()
                .build();*/
    }

    private void showEuConsentForm() {
        euConsentForm.show();
    }

    private static class AdProviderAdapter extends BaseRecyclerAdapter<AdProvider, AdProviderViewHolder> {
        public AdProviderAdapter(Context context, List<AdProvider> items) {
            super(context, items, android.R.layout.simple_list_item_1);
        }

        @Override
        protected AdProviderViewHolder buildViewHolder(View view) {
            return new AdProviderViewHolder(view);
        }

        @Override
        protected void onBindViewHolder(AdProviderViewHolder viewHolder, AdProvider item) {
            viewHolder.itemNameField.setText(item.getName());
            viewHolder.itemNameField.setTextColor(DisplayUtils.getColor(viewHolder.itemNameField.getContext(), R.attr.colorSecondary));
            viewHolder.itemNameField.setTag(item.getPrivacyPolicyUrlString());
        }
    }

    private static class AdProviderViewHolder extends RecyclerView.ViewHolder {

        private final TextView itemNameField;

        public AdProviderViewHolder(@NonNull View itemView) {
            super(itemView);
            itemNameField = itemView.findViewById(android.R.id.text1);
            itemNameField.setOnClickListener(v -> openUrlLink(v.getContext(), v.getTag().toString()));
        }

        private void openUrlLink(Context context, String urlLink) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = Uri.parse(urlLink);
            intent.setDataAndType(uri, "text/html");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.open_link)));
        }
    }
}
