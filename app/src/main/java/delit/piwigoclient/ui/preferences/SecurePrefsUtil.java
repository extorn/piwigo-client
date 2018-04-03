package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import com.google.android.vending.licensing.AESObfuscator;
import com.google.android.vending.licensing.ValidationException;

import java.util.Random;

/**
 * Created by gareth on 04/11/17.
 */

public class SecurePrefsUtil {

    private static final String TAG = "SecurePrefsUtil";

    private static SecurePrefsUtil instance;
    private final AESObfuscator obfuscator;

    public synchronized static SecurePrefsUtil getInstance(Context c) {
        if(instance == null) {
            instance = new SecurePrefsUtil(c);
        }
        return instance;
    }

    public SecurePrefsUtil(Context c) {
        // Try to use more data here. ANDROID_ID is a single point of attack.
        String deviceId = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ANDROID_ID);

        // Construct the LicenseChecker with a policy
        String myPackageName = c.getPackageName();

        byte[] salt = new byte[20];
        // generate a salt that is reproducible for this device forever.
        new Random(deviceId.hashCode()).nextBytes(salt);
        obfuscator = new AESObfuscator(salt, myPackageName, deviceId);
    }

    public void writeSecurePreference(SharedPreferences prefs, String preferenceKey, String preferenceValue) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(preferenceKey, encryptValue(preferenceKey, preferenceValue));
        editor.commit();
    }

    public String readSecureStringPreference(SharedPreferences prefs, String preferenceKey, String defaultValue) {
        String prefValue = prefs.getString(preferenceKey, defaultValue);
        if(prefValue == null) {
            return defaultValue;
        } else if(prefValue.length() == 0) {
            return prefValue;
        }
        return decryptValue(preferenceKey, prefValue, defaultValue);
    }

    public String decryptValue(String key, String encryptedVal, String defaultValue) {
        try {
            return obfuscator.unobfuscate(encryptedVal, key);
        } catch(ValidationException e) {
            if(delit.piwigoclient.BuildConfig.DEBUG) {
                Log.e(TAG, "Preference has been corrupted or is otherwise irretrievable. Returning default.", e);
            }
            return defaultValue;
        }
    }

    public String encryptValue(String key, String value) {
        return obfuscator.obfuscate(value, key);
    }
}
