package delit.piwigoclient.ui.common.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.google.android.vending.licensing.AESObfuscator;
import com.google.android.vending.licensing.ValidationException;

import org.greenrobot.eventbus.EventBus;

import java.util.Random;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.MyApplication;
import delit.piwigoclient.ui.events.ShowMessageEvent;

/**
 * Created by gareth on 04/11/17.
 */

public class SecurePrefsUtil {

    private static final String TAG = "SecurePrefsUtil";

    private static SecurePrefsUtil instance;
    private final AESObfuscator obfuscator;

    private SecurePrefsUtil(Context c) {
        // Try to use more data here. ANDROID_ID is a single point of attack.
        String deviceId = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ANDROID_ID);

        // Construct the LicenseChecker with a policy
        String myPackageName = c.getPackageName();

        byte[] salt = new byte[20];
        // generate a salt that is reproducible for this device forever.
        new Random(deviceId.hashCode()).nextBytes(salt);
        obfuscator = new AESObfuscator(salt, myPackageName, deviceId);
    }

    public synchronized static SecurePrefsUtil getInstance(Context c) {
        if (instance == null) {
            instance = new SecurePrefsUtil(c);
        }
        return instance;
    }

    public void writeSecurePreference(SharedPreferences prefs, String preferenceKey, String preferenceValue) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(preferenceKey, encryptValue(preferenceKey, preferenceValue));
        editor.commit();
    }

    public String readSecureStringPreference(SharedPreferences prefs, String preferenceKey, String defaultValue) {
        String prefValue = prefs.getString(preferenceKey, defaultValue);
        if (prefValue == null || prefValue.length() == 0) {
            return prefValue;
        }
        return decryptValue(preferenceKey, prefValue, defaultValue);
    }

    public String decryptValue(String key, String encryptedVal, String defaultValue) {
        try {
            return obfuscator.unobfuscate(encryptedVal, key);
        } catch (ValidationException e) {
            String message = MyApplication.getAppResources().getString(R.string.preference_corrupt_please_re_type_pattern, key, defaultValue);
            Crashlytics.log(Log.DEBUG, TAG, message + " encypted value : BEGIN_" + encryptedVal + "_END");
            EventBus.getDefault().post(new ShowMessageEvent(ShowMessageEvent.TYPE_WARN, R.string.alert_warning, message));
            return defaultValue;
        }
    }

    public String encryptValue(String key, String value) {
        return obfuscator.obfuscate(value, key);
    }
}
