package hotchemi.android.rate;

import android.app.Activity;
import android.content.Context;
import android.view.View;

import java.util.Date;

import delit.piwigoclient.R;

import static hotchemi.android.rate.MyRatingDialogManager.create;
import static hotchemi.android.rate.PreferenceHelper.getInstallDate;
import static hotchemi.android.rate.PreferenceHelper.getIsAgreeShowDialog;
import static hotchemi.android.rate.PreferenceHelper.getLaunchTimes;
import static hotchemi.android.rate.PreferenceHelper.getRemindInterval;
import static hotchemi.android.rate.PreferenceHelper.isFirstLaunch;
import static hotchemi.android.rate.PreferenceHelper.setInstallDate;

public final class MyAppRate {

    private static MyAppRate singleton;

    private final DialogOptions options = new DialogOptions();

    private int installDate = 10;

    private int launchTimes = 10;

    private int remindInterval = 1;

    private boolean isDebug = false;

    private MyAppRate() {
    }

    public static MyAppRate instance() {
        if (singleton == null) {
            synchronized (MyAppRate.class) {
                if (singleton == null) {
                    singleton = new MyAppRate();
                }
            }
        }
        return singleton;
    }

    public static boolean showRateDialogIfMeetsConditions(Activity activity) {
        boolean isMeetsConditions = singleton.isDebug || singleton.shouldShowRateDialog(activity.getApplicationContext());
        if (isMeetsConditions) {
            singleton.showRateDialog(activity);
        }
        return isMeetsConditions;
    }

    private static boolean isOverDate(long targetDate, int threshold) {
        return new Date().getTime() - targetDate >= threshold * 24 * 60 * 60 * 1000;
    }

    public MyAppRate setLaunchTimes(int launchTimes) {
        this.launchTimes = launchTimes;
        return this;
    }

    public MyAppRate setInstallDays(int installDate) {
        this.installDate = installDate;
        return this;
    }

    public MyAppRate setRemindInterval(int remindInterval) {
        this.remindInterval = remindInterval;
        return this;
    }

    public MyAppRate setShowLaterButton(boolean isShowNeutralButton) {
        options.setShowNeutralButton(isShowNeutralButton);
        return this;
    }

    public MyAppRate setShowNeverButton(boolean isShowNeverButton) {
        options.setShowNegativeButton(isShowNeverButton);
        return this;
    }

    public MyAppRate setShowTitle(boolean isShowTitle) {
        options.setShowTitle(isShowTitle);
        return this;
    }

    public MyAppRate clearAgreeShowDialog(Context context) {
        PreferenceHelper.setAgreeShowDialog(context, true);
        return this;
    }

    public MyAppRate clearSettingsParam(Context context) {
        PreferenceHelper.setAgreeShowDialog(context, true);
        PreferenceHelper.clearSharedPreferences(context);
        return this;
    }

    public MyAppRate setAgreeShowDialog(Context context, boolean clear) {
        PreferenceHelper.setAgreeShowDialog(context, clear);
        return this;
    }

    public MyAppRate setView(View view) {
        options.setView(view);
        return this;
    }

    public MyAppRate setOnClickButtonListener(OnClickButtonListener listener) {
        options.setListener(listener);
        return this;
    }

    public MyAppRate setTitle(int resourceId) {
        options.setTitleResId(resourceId);
        return this;
    }

    public MyAppRate setTitle(String title) {
        options.setTitleText(title);
        return this;
    }

    public MyAppRate setMessage(int resourceId) {
        options.setMessageResId(resourceId);
        return this;
    }

    public MyAppRate setMessage(String message) {
        options.setMessageText(message);
        return this;
    }

    public MyAppRate setTextRateNow(int resourceId) {
        options.setTextPositiveResId(resourceId);
        return this;
    }

    public MyAppRate setTextRateNow(String positiveText) {
        options.setPositiveText(positiveText);
        return this;
    }

    public MyAppRate setTextLater(int resourceId) {
        options.setTextNeutralResId(resourceId);
        return this;
    }

    public MyAppRate setTextLater(String neutralText) {
        options.setNeutralText(neutralText);
        return this;
    }

    public MyAppRate setTextNever(int resourceId) {
        options.setTextNegativeResId(resourceId);
        return this;
    }

    public MyAppRate setTextNever(String negativeText) {
        options.setNegativeText(negativeText);
        return this;
    }

    public MyAppRate setCancelable(boolean cancelable) {
        options.setCancelable(cancelable);
        return this;
    }

    public MyAppRate setStoreType(StoreType appstore) {
        options.setStoreType(appstore);
        return this;
    }

    public void monitor(Context context) {
        if (isFirstLaunch(context)) {
            setInstallDate(context);
        }
        PreferenceHelper.setLaunchTimes(context, getLaunchTimes(context) + 1);
    }

    public void showRateDialog(Activity activity) {
        if (!activity.isFinishing()) {
            create(activity, options, R.string.alert_error_no_app_available_to_rate_app).show();
        }
    }

    public boolean shouldShowRateDialog(Context context) {
        return getIsAgreeShowDialog(context) &&
                isOverLaunchTimes(context) &&
                isOverInstallDate(context) &&
                isOverRemindDate(context);
    }

    private boolean isOverLaunchTimes(Context context) {
        return getLaunchTimes(context) >= launchTimes;
    }

    private boolean isOverInstallDate(Context context) {
        return isOverDate(getInstallDate(context), installDate);
    }

    private boolean isOverRemindDate(Context context) {
        return isOverDate(getRemindInterval(context), remindInterval);
    }

    public boolean isDebug() {
        return isDebug;
    }

    public MyAppRate setDebug(boolean isDebug) {
        this.isDebug = isDebug;
        return this;
    }

}
