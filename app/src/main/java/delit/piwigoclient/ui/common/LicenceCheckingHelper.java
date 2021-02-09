package delit.piwigoclient.ui.common;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.vending.licensing.AESObfuscator;
import com.google.android.vending.licensing.BuildConfig;
import com.google.android.vending.licensing.LicenseChecker;
import com.google.android.vending.licensing.LicenseCheckerCallback;
import com.google.android.vending.licensing.Policy;
import com.google.android.vending.licensing.ServerManagedPolicy;
import com.google.firebase.installations.FirebaseInstallations;

import java.util.Date;
import java.util.Random;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.PreferenceUtils;
import delit.libs.util.SafeRunnable;
import delit.piwigoclient.R;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;

/**
 * Created by gareth on 28/10/17.
 */

public class LicenceCheckingHelper<T extends BaseMyActivity<T,UIH>,UIH extends ActivityUIHelper<UIH,T>> {
    // Generated on google play site (specific to piwigoclient.paid) and copied here - this is the public services api key for my app
    private static final String BASE64_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqg+QahizYwmfOB47vGwGW+0fYjHxpnz/kYGIS/6jJeUwrdclCGEgmQZfbVfLZnQRpLw67sofp3yUwofFLFGYhWISvYyAgtuhyNlcaP5Ki2r7zyhxBcI+1xPFQI3kYb3rRuUMFEpYW+fERtMs2X9gnFlhAyqbw5mZX7I36LWBIPM2X2GUu7g4WXOcPayCocQFmk1u4Chz4Ca1M807Vk7AnI4cFPRsHfsuc3h9V+Zaqu2holNcQrvJhQ6yUMN0A5ip4RTKKGIogBcoVhv3Ye05BWqbzrnGPmIFvUGqRoh0dnrLL6oDHbnE5xpfNDU3hdnjv74vvDJKuJC05bYPxoOe2wIDAQAB";

    private static final String TAG = "LicenceCheckHelper";

    private MyLicenseCheckerCallback mLicenseCheckerCallback;
    private LicenseChecker mChecker;
    // A handler on the UI thread.
    private Handler mHandler;
    private T activity;
    private Date lastChecked;

    public void onCreate(T activity) {

        this.activity = activity;

        mHandler = new Handler();

        Task<String> idTask = FirebaseInstallations.getInstance().getId(); //This is a globally unique id for the app installation instance.
        idTask.addOnSuccessListener(this::withInstallGuid);
        idTask.addOnFailureListener(e -> {
            Logging.log(Log.ERROR,TAG, "Unable to retrieve App Install GUID from Firebase");
            Logging.recordException(e);
            // Try to use more data here. ANDROID_ID is a single point of attack.
            @SuppressLint("HardwareIds")
            String deviceId = Settings.Secure.getString(activity.getContentResolver(), Settings.Secure.ANDROID_ID);
            withInstallGuid(deviceId);
        });

    }

    private void withInstallGuid(String appInstallGuid) {
        // Library calls this when it's done.
        mLicenseCheckerCallback = new MyLicenseCheckerCallback();

        //Force the licence response to be invalidated every time a new version is installed.
        byte[] salt = new byte[20];
        new Random(getVersionCode(activity)).nextBytes(salt);


        mChecker = new LicenseChecker(
                activity, new ServerManagedPolicy(activity.getApplicationContext(),
                new AESObfuscator(salt, delit.piwigoclient.BuildConfig.APPLICATION_ID, appInstallGuid, true)),
                BASE64_PUBLIC_KEY);
        doVisualCheck(activity.getApplicationContext());
    }

    private long getVersionCode(@NonNull BaseMyActivity<?,?> activity) {
        try {
            PackageInfo pInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            // fill the salt with new random data (seeded from the current app version number)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return pInfo.getLongVersionCode();
            } else {
                //noinspection deprecation
                return pInfo.versionCode;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Logging.log(Log.ERROR,TAG, "Unable to extract app version for activity : " + activity);
            return -1;
        }
    }

    private void showDialog(final boolean showRetryButton) {
        String msg = activity.getString(showRetryButton ? R.string.unlicensed_dialog_retry_body : R.string.unlicensed_dialog_body);
        activity.getUiHelper().showOrQueueDialogQuestion(R.string.unlicensed_dialog_title, msg, R.string.button_quit, showRetryButton ? R.string.button_retry : R.string.button_buy, new LicenceCheckAction<>(activity.getUiHelper(), showRetryButton));
    }

    private synchronized void forceCheck() {
        lastChecked = null;
        doCheck();
    }

    public void doSilentCheck() {
        doCheck();
    }

    private void doVisualCheck(Context applicationContext) {
        if (BuildConfig.DEBUG) {
            activity.getUiHelper().showDetailedShortMsg(R.string.alert_information, applicationContext.getString(R.string.checking_license));
        }
        doCheck();
    }

    private static class LicenceCheckAction<T extends ActivityUIHelper<T,R>,R extends BaseMyActivity<R,T>> extends QuestionResultAdapter<T,R> implements Parcelable {

        private final boolean allowRetry;

        public LicenceCheckAction(T uiHelper, boolean allowRetry) {
            super(uiHelper);
            this.allowRetry = allowRetry;
        }

        protected LicenceCheckAction(Parcel in) {
            super(in);
            allowRetry = in.readByte() != 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeByte((byte) (allowRetry ? 1 : 0));
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<LicenceCheckAction<?,?>> CREATOR = new Creator<LicenceCheckAction<?,?>>() {
            @Override
            public LicenceCheckAction<?,?> createFromParcel(Parcel in) {
                return new LicenceCheckAction<>(in);
            }

            @Override
            public LicenceCheckAction<?,?>[] newArray(int size) {
                return new LicenceCheckAction[size];
            }
        };

        @Override
        public void onResult(androidx.appcompat.app.AlertDialog dialog, Boolean positiveAnswer) {
            R activity = getUiHelper().getParent();
            if (Boolean.TRUE == positiveAnswer) {
                if (allowRetry) {
                    activity.getLicencingHelper().forceCheck();
                } else {
                    Logging.log(Log.DEBUG, TAG, "Starting Market Intent");
                    Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(
                            "http://market.android.com/details?id=" + activity.getPackageName()));
                    activity.startActivity(Intent.createChooser(marketIntent, ""));
                }
            } else {
                activity.finish();
            }
        }
    }

    private synchronized void doCheck() {
        if(mLicenseCheckerCallback == null || mChecker == null) {
            Logging.log(Log.DEBUG, TAG, "Skipping licence check");
            return;
        }
        long maxInterval = 1000 * 60 * 60 * 6;
        // check again a maximum of every 6 hours apart.
        if (lastChecked == null || lastChecked.getTime() > System.currentTimeMillis() || lastChecked.getTime() + maxInterval < System.currentTimeMillis()) {
            lastChecked = new Date();
            if (mLicenseCheckerCallback.isOfflineAccessAllowed()) {
                final ConnectivityManager connMgr = (ConnectivityManager) activity.getApplicationContext()
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
                android.net.NetworkInfo activeNetworkInfo = null;
                if(connMgr != null) {
                    activeNetworkInfo = connMgr.getActiveNetworkInfo();
                }
                if (activeNetworkInfo == null || !activeNetworkInfo.isAvailable()) {
                    // allow access for the next 6 hours.
                    mLicenseCheckerCallback.allow(-1);
                    Logging.log(Log.DEBUG, TAG, "Licence checked within allowable period");
                    return;
                }
            }
            // normally, test access is allowed.
            mChecker.checkAccess(mLicenseCheckerCallback);
        }
    }

    private void displayResult(final String result) {
        mHandler.post(new SafeRunnable(() -> activity.getUiHelper().showDetailedMsg(R.string.alert_information, result)));
    }

    private void displayDialog(final boolean showRetry) {
        mHandler.post(new SafeRunnable(() -> showDialog(showRetry)));
    }

    protected void onDestroy() {
        if(mChecker != null) {
            mChecker.onDestroy();
        }
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
            AdsManager.getInstance(activity).setAppLicensed(true);
            Logging.log(Log.DEBUG, TAG, "App confirmed as licensed");
            // Should allow user access.
//            displayResult(activity.getString(R.string.allow));
        }

        public void dontAllow(int policyReason) {
            if (activity.isFinishing()) {
                // Don't update UI if Activity is finishing.
                return;
            }
            AdsManager.getInstance(activity).setAppLicensed(false);
            if (policyReason == Policy.NOT_LICENSED) {
                Logging.log(Log.DEBUG, TAG, "App unlicensed - preferences wiped");
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
            Logging.log(Log.ERROR, TAG, "Licence check encountered error");
        }
    }
}
