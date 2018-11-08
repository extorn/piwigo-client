package delit.piwigoclient.ui.common;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;

import com.google.android.vending.licensing.AESObfuscator;
import com.google.android.vending.licensing.BuildConfig;
import com.google.android.vending.licensing.LicenseChecker;
import com.google.android.vending.licensing.LicenseCheckerCallback;
import com.google.android.vending.licensing.Policy;
import com.google.android.vending.licensing.ServerManagedPolicy;

import java.util.Date;
import java.util.Random;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.util.PreferenceUtils;

/**
 * Created by gareth on 28/10/17.
 */

public class LicenceCheckingHelper {
    // Generated on google play site (specific to piwigoclient.paid) and copied here - this is the public services api key for my app
    private static final String BASE64_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqg+QahizYwmfOB47vGwGW+0fYjHxpnz/kYGIS/6jJeUwrdclCGEgmQZfbVfLZnQRpLw67sofp3yUwofFLFGYhWISvYyAgtuhyNlcaP5Ki2r7zyhxBcI+1xPFQI3kYb3rRuUMFEpYW+fERtMs2X9gnFlhAyqbw5mZX7I36LWBIPM2X2GUu7g4WXOcPayCocQFmk1u4Chz4Ca1M807Vk7AnI4cFPRsHfsuc3h9V+Zaqu2holNcQrvJhQ6yUMN0A5ip4RTKKGIogBcoVhv3Ye05BWqbzrnGPmIFvUGqRoh0dnrLL6oDHbnE5xpfNDU3hdnjv74vvDJKuJC05bYPxoOe2wIDAQAB";

    private static final String TAG = "LicenceCheckHelper";

    private MyLicenseCheckerCallback mLicenseCheckerCallback;
    private LicenseChecker mChecker;
    // A handler on the UI thread.
    private Handler mHandler;
    private MyActivity activity;
    private Date lastChecked;

    public void onCreate(MyActivity activity) {

        this.activity = activity;

        mHandler = new Handler();

        // Try to use more data here. ANDROID_ID is a single point of attack.
        String deviceId = Settings.Secure.getString(activity.getContentResolver(), Settings.Secure.ANDROID_ID);

        // Library calls this when it's done.
        mLicenseCheckerCallback = new MyLicenseCheckerCallback();
        // Construct the LicenseChecker with a policy
        String myPackageName = activity.getPackageName();

        //Force the licence response to be invalidated every time a new version is installed.
        byte[] salt = new byte[20];
        new Random(BuildConfig.VERSION_CODE).nextBytes(salt);

        mChecker = new LicenseChecker(
                activity, new ServerManagedPolicy(activity.getApplicationContext(),
                new AESObfuscator(salt, myPackageName, deviceId)),
                BASE64_PUBLIC_KEY);
        doVisualCheck();
    }

    private void showDialog(final boolean showRetryButton) {
        String msg = activity.getString(showRetryButton ? R.string.unlicensed_dialog_retry_body : R.string.unlicensed_dialog_body);
        activity.getUiHelper().showOrQueueDialogQuestion(R.string.unlicensed_dialog_title, msg, R.string.button_quit, showRetryButton ? R.string.button_retry : R.string.button_buy, new UIHelper.QuestionResultAdapter() {

            @Override
            public void onResult(androidx.appcompat.app.AlertDialog dialog, Boolean positiveAnswer) {
                if (Boolean.TRUE == positiveAnswer) {
                    if (showRetryButton) {
                        doCheck();
                    } else {
                        Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(
                                "http://market.android.com/details?id=" + activity.getPackageName()));
                        activity.startActivity(marketIntent);
                    }
                } else {
                    activity.finish();
                }
            }
        });
    }

    public void doSilentCheck() {
        doCheck();
    }

    private void doVisualCheck() {
        if(BuildConfig.DEBUG) {
            activity.getUiHelper().showToast(R.string.checking_license);
        }
        doCheck();
    }

    private synchronized void doCheck() {

        long maxInterval = 1000 * 60 * 60 * 6;
        // check again a maximum of every 6 hours apart.
        if (lastChecked == null || lastChecked.getTime() > System.currentTimeMillis() || lastChecked.getTime() + maxInterval < System.currentTimeMillis()) {
            lastChecked = new Date();
            if (mLicenseCheckerCallback.isOfflineAccessAllowed()) {
                final ConnectivityManager connMgr = (ConnectivityManager) activity.getApplicationContext()
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
                android.net.NetworkInfo activeNetworkInfo = connMgr.getActiveNetworkInfo();
                if (activeNetworkInfo == null || !activeNetworkInfo.isAvailable()) {
                    // allow access for the next 6 hours.
                    mLicenseCheckerCallback.allow(-1);
                    return;
                }
            }
            // normally, test access is allowed.
            mChecker.checkAccess(mLicenseCheckerCallback);
        }
    }

    private void displayResult(final String result) {
        mHandler.post(new Runnable() {
            public void run() {
                activity.getUiHelper().showOrQueueDialogMessage(R.string.alert_information, result);
            }
        });
    }

    private void displayDialog(final boolean showRetry) {
        mHandler.post(new Runnable() {
            public void run() {
                showDialog(showRetry);
            }
        });
    }

    protected void onDestroy() {
        mChecker.onDestroy();
    }

    private class MyLicenseCheckerCallback implements LicenseCheckerCallback {

        private boolean offlineAccessAllowed = true;

        public boolean isOfflineAccessAllowed() {
            return offlineAccessAllowed;
        }

        public void allow(int policyReason) {
            offlineAccessAllowed = policyReason >= 0;
            if (activity.isFinishing()) {
                // Don't update UI if Activity is finishing.
                return;
            }
            AdsManager.getInstance().setAppLicensed(true);
            // Should allow user access.
//            displayResult(activity.getString(R.string.allow));
        }

        public void dontAllow(int policyReason) {
            if (activity.isFinishing()) {
                // Don't update UI if Activity is finishing.
                return;
            }
            AdsManager.getInstance().setAppLicensed(false);
            if (policyReason == Policy.NOT_LICENSED) {
                PreferenceUtils.wipeAppPreferences(activity);
            }

//            displayResult(activity.getString(R.string.dont_allow));
            // Should not allow access. In most cases, the app should assume
            // the user has access unless it encounters this. If it does,
            // the app should inform the user of their unlicensed ways
            // and then either shut down the app or limit the user to a
            // restricted set of features.
            // In this example, we show a dialog that takes the user to Market.
            // If the reason for the lack of license is that the service is
            // unavailable or there is another problem, we display a
            // retry button on the dialog and a different message.
            displayDialog(policyReason == Policy.RETRY);
        }

        public void applicationError(int errorCode) {
            if (activity.isFinishing()) {
                // Don't update UI if Activity is finishing.
                return;
            }
            // This is a polite way of saying the developer made a mistake
            // while setting up or calling the license checker library.
            // Please examine the error code and fix the error.
            String result = String.format(activity.getString(R.string.application_error), errorCode);
            displayResult(result);
        }
    }
}
