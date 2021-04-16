package delit.piwigoclient.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
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
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.list.BaseRecyclerAdapter;
import delit.libs.util.SafeRunnable;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;
import delit.piwigoclient.ui.common.fragment.MyFragment;

import static android.view.View.VISIBLE;

/**
 * Created by gareth on 12/07/17.
 */

public class AdsManager {

    private static final String TAG = "AdMan";
    public static final String BLOCK_MILLIS_PREF = "BLOCK_MILLIS";
    private static AdsManager instance;
    private InterstitialAdLoader selectFileToUploadAdLoader;
    private InterstitialAdLoader albumBrowsingAdLoader;
    private long lastShowedAdvert;
    private boolean showAds = true;
    private boolean appLicensed = false;
    private SharedPreferences prefs;
    private static int advertLoadFailures = 0;
    private boolean advertsDisabled;
    private boolean interstitialShowing;
    private Boolean lastAdPaid;
    private ConsentForm euConsentForm;
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

    public synchronized void updateShowAdvertsSetting(Context context) {

        showAds = !BuildConfig.PAID_VERSION;

        if (!appLicensed && showAds && status == STOPPED) {
            status = INITIALISING;
            getConsentInformationAndInitialiseAdverts(context);
        } else if (showAds && status == STARTED) {
            selectFileToUploadAdLoader.loadAdvert(context, buildAdRequest(context));
            albumBrowsingAdLoader.loadAdvert(context, buildAdRequest(context));
        }

        updateConsentInformation(getConsentInformation(context));
    }

    private static class InterstitialAdLoader extends InterstitialAdLoadCallback {

        private final String adUnitId;
        private final InterstitialAdListener adListener;
        private InterstitialAd targetAd;
        private int retries = 0;

        public InterstitialAdLoader(String adUnitId, InterstitialAdListener listener) {
            this.adUnitId = adUnitId;
            this.adListener = listener;
        }

        public String getAdUnitId() {
            return adUnitId;
        }

        @Override
        public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
            // The mInterstitialAd reference will be null until
            // an ad is loaded.
            targetAd = interstitialAd;
            retries = 0;
            advertLoadFailures = 0;
            targetAd.setFullScreenContentCallback(new FullScreenContentCallback(){

                @Override
                public void onAdDismissedFullScreenContent() {
                    super.onAdDismissedFullScreenContent();
                    targetAd = null;
                    //// perform your code that you wants todo after ad dismissed or closed
                    onAdClosed();
                    adListener.onAdDismissedFullScreenContent();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(com.google.android.gms.ads.AdError adError) {
                    super.onAdFailedToShowFullScreenContent(adError);
                    targetAd = null;
                    /// perform your action here when ad will not load
                    onAdError();
                    adListener.onAdFailedToShowFullScreenContent(adError);
                }

                @Override
                public void onAdShowedFullScreenContent() {
                    super.onAdShowedFullScreenContent();
                    targetAd = null;
                    adListener.onAdShowedFullScreenContent();
                }
            });
        }

        public void onAdClosed() {
        }

        private void runLoad(@NonNull Context context, @NonNull AdRequest adRequest) {
            InterstitialAd.load(context, getAdUnitId(), adRequest, this);
        }

        public void loadAdvert(@NonNull Context context, @NonNull AdRequest adRequest) {
            if(isNeedsLoad()) {
                Handler mainHandler = new Handler(context.getMainLooper());
                Runnable myRunnable = new SafeRunnable(() -> runLoad(context, adRequest));
                mainHandler.post(myRunnable);
            }
        }

        @Override
        public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
            // Handle the error
            targetAd = null;

            onAdError();

        }

        private void onAdError() {
            retries++;
            if (retries < 3) {
                Logging.log(Log.DEBUG, "InterstitialAd", "advert load failed, retry needed");
            } else {
                Logging.log(Log.DEBUG, "InterstitialAd", "Gave up trying to load advert after 3 attempts");
                advertLoadFailures++;
                retries = 0;
            }
        }

        public boolean isNeedsLoad() {
            return targetAd == null;
        }

        public boolean isLoaded() {
            return targetAd != null;
        }

        public InterstitialAd getAd() {
            return targetAd;
        }
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
        MobileAds.initialize(context, initializationStatus -> initialiseAllAdverts(context));
    }

    private void initialiseAllAdverts(Context context) {

        if(BuildConfig.DEBUG) {
            // Only treat devices as test devices if app is in debug mode
            List<String> testDeviceIds = Collections.singletonList("91A207EEC1618AE36FFA9D797319F482");
            RequestConfiguration configuration =
                    new RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build();
            MobileAds.setRequestConfiguration(configuration);
        }
        selectFileToUploadAdLoader = new InterstitialAdLoader(context.getString(R.string.ad_id_uploads_interstitial), new InterstitialAdListener(context));
        albumBrowsingAdLoader = new InterstitialAdLoader(context.getString(R.string.ad_id_album_interstitial), new InterstitialAdListener(context));
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

    private synchronized boolean acceptableToShowAdvert(Context context, InterstitialAdLoader ad, long minDelayBetweenAds) {

        long currentTime = System.currentTimeMillis();

        if (showAds && !advertsDisabled && ad != null) {
            if (ad.isLoaded() && (null == lastAdPaid || !lastAdPaid || (currentTime - lastShowedAdvert) > minDelayBetweenAds)) {
                lastShowedAdvert = currentTime;
                return true;
            } else if (!ad.isLoaded()) {
                ad.loadAdvert(context, buildAdRequest(context));
            }
        }
        return false;
    }

    public void showFileToUploadAdvertIfAppropriate(Activity activity) {
        if (acceptableToShowAdvert(activity, selectFileToUploadAdLoader, 24 * 60 * 60000)) {
            showInterstitialAd(activity, selectFileToUploadAdLoader.getAd());
            // show every 24 hours
        }
    }

    private void showInterstitialAd(Activity activity, InterstitialAd ad) {
        interstitialShowing = true;
        if(lastAdPaid == null) {
            lastAdPaid = false;
        }
        ad.setOnPaidEventListener(adValue -> lastAdPaid = true);
        ad.show(activity);
    }

    public boolean isLastAdShownAndUnpaid() {
        return lastAdPaid != null && !lastAdPaid;
    }

    public void showAlbumBrowsingAdvertIfAppropriate(Activity activity) {
        if (acceptableToShowAdvert(activity, albumBrowsingAdLoader, 24 * 60 * 60000)) {
            showInterstitialAd(activity, albumBrowsingAdLoader.getAd());
            // show every 24 hours
        }
    }

    public <FUIH extends FragmentUIHelper<FUIH,F>,F extends MyFragment<F,FUIH>> void showPleadingMessageIfNeeded(FUIH uiHelper) {
        if(!interstitialShowing && isLastAdShownAndUnpaid()) {
            lastAdPaid = null;
            uiHelper.showOrQueueDialogQuestion(R.string.alert_warning, uiHelper.getString(R.string.alert_advert_importance_message), View.NO_ID, R.string.button_ok, new AdvertPleadingListener<>(uiHelper));
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

    private static class AdvertPleadingListener<F extends MyFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends QuestionResultAdapter<FUIH,F> implements Parcelable {
        public AdvertPleadingListener(FUIH uiHelper) {
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

        public static final Creator<AdvertPleadingListener<?,?>> CREATOR = new Creator<AdvertPleadingListener<?,?>>() {
            @Override
            public AdvertPleadingListener<?,?> createFromParcel(Parcel in) {
                return new AdvertPleadingListener<>(in);
            }

            @Override
            public AdvertPleadingListener<?,?>[] newArray(int size) {
                return new AdvertPleadingListener[size];
            }
        };

        @Override
        public void onDismiss(AlertDialog dialog) {
            Logging.logAnalyticEvent(dialog.getContext(),"ad_warning_shown", null);
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

    //FIXME work out what to do with the IABUSPrivacy_String consent!
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

    private class InterstitialAdListener extends FullScreenContentCallback {

        private final WeakReference<Context> contextRef;
        long startedAt = System.currentTimeMillis();

        private InterstitialAdListener(Context context) {
            this.contextRef = new WeakReference<>(context);
        }

        @Override
        public void onAdImpression() {
            super.onAdImpression();
            lastAdPaid = true;
        }

        @Override
        public void onAdDismissedFullScreenContent() {
            super.onAdDismissedFullScreenContent();
            if(lastAdPaid == null || !lastAdPaid) {
                long adShowingFor = System.currentTimeMillis() - startedAt;
                if (adShowingFor > 8000) {
                    lastAdPaid = Boolean.TRUE;
                    Context context = contextRef.get();
                    if(context != null) {
                        Logging.logAnalyticEvent(context,"ad_watched_minimum", null);
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
