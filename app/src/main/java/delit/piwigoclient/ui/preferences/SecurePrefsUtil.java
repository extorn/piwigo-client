package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceDataStore;

import com.google.android.vending.licensing.AESObfuscator;
import com.google.android.vending.licensing.ValidationException;

import org.greenrobot.eventbus.EventBus;

import java.util.Random;

import delit.libs.core.util.Logging;
import delit.libs.ui.events.ShowMessageEvent;
import delit.piwigoclient.R;

/**
 * Created by gareth on 04/11/17.
 */

public class SecurePrefsUtil {

    private static final String TAG = "SecurePrefsUtil";

    private static SecurePrefsUtil instance;
    private final AESObfuscator oldObfuscator;
    private final AESObfuscator newObfuscator;

    private SecurePrefsUtil(@NonNull Context c, String appId) {
        // Try to use more data here. ANDROID_ID is a single point of attack.
        String deviceId = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ANDROID_ID);

        byte[] salt = new byte[20];
        // generate a salt that is reproducible for this device forever.
        new Random(deviceId.hashCode()).nextBytes(salt);
        oldObfuscator = new AESObfuscator(salt, c.getPackageName(), deviceId, true);
        newObfuscator = new AESObfuscator(salt, appId, deviceId, false); // enable error recording once everyone has migrated.
    }

    public synchronized static SecurePrefsUtil getInstance(@NonNull Context c, String appId) {
        if (instance == null) {
            instance = new SecurePrefsUtil(c, appId);
        }
        return instance;
    }

    public void writeSecurePreference(SharedPreferences prefs, String preferenceKey, byte[] preferenceValue) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(preferenceKey, encryptValue(preferenceKey, preferenceValue));
        editor.commit();
    }

    public void writeSecurePreference(SharedPreferences prefs, String preferenceKey, String preferenceValue) {
        SharedPreferences.Editor editor = prefs.edit();
        writeSecurePreference(editor, preferenceKey, preferenceValue);
        editor.commit();
    }

    public SharedPreferences.Editor writeSecurePreference(SharedPreferences.Editor editor, String preferenceKey, String preferenceValue) {
        editor.putString(preferenceKey, encryptValue(preferenceKey, preferenceValue));
        return editor;
    }

    public byte[] readSecurePreferenceRawBytes(Context context, SharedPreferences prefs, String preferenceKey, byte[] defaultValue) {
        String prefValue = prefs.getString(preferenceKey, null);
        if (prefValue == null || prefValue.length() == 0) {
            return defaultValue;
        }
        return decryptRawValue(prefs, preferenceKey, prefValue, defaultValue, context);
    }

    public String readSecureStringPreference(Context context, SharedPreferences prefs, String preferenceKey, String defaultValue) {
        String prefValue = prefs.getString(preferenceKey, defaultValue);
        if (prefValue == null || prefValue.length() == 0) {
            return prefValue;
        }
        return decryptValue(context, prefs, preferenceKey, prefValue, defaultValue);
    }

    public String decryptValue(PreferenceDataStore prefStore, String preferenceKey, String encryptedVal, String defaultValue, Context context) {
        try {
            return newObfuscator.unobfuscateString(encryptedVal, preferenceKey);
        } catch (ValidationException e) {
            try {
                String decrypted = oldObfuscator.unobfuscateString(encryptedVal, preferenceKey);
                Logging.recordException(new MigratingEncryptedPreferenceException());
                try {
                    prefStore.putString(preferenceKey, encryptValue(preferenceKey, decrypted));
                } catch (UnsupportedOperationException ex) {
                    Logging.log(Log.DEBUG, TAG, String.format("Unable to migrate preference with key %1$s to new encryption spec automatically", preferenceKey));
                }
                return decrypted;
            } catch (ValidationException e1) {
                // do nothing
            }
            String message = context.getString(R.string.preference_corrupt_please_re_type_pattern, preferenceKey, defaultValue);
            Logging.log(Log.DEBUG, TAG, message + " encypted value : BEGIN_" + encryptedVal + "_END");
            EventBus.getDefault().post(new ShowMessageEvent(ShowMessageEvent.TYPE_WARN, R.string.alert_warning, message));
            return defaultValue;
        }
    }

    public byte[] decryptRawValue(SharedPreferences prefs, String key, String encryptedVal, byte[] defaultValue, Context context) {
        try {
            return newObfuscator.unobfuscateBytes(encryptedVal, key);
        } catch (ValidationException e) {
            try {
                byte[] decrypted = oldObfuscator.unobfuscateBytes(encryptedVal, key);
                Logging.recordException(new MigratingEncryptedPreferenceException());
                writeSecurePreference(prefs, key, decrypted);
                return decrypted;
            } catch (ValidationException e1) {
                // do nothing
            }
            String message = context.getString(R.string.preference_corrupt_please_re_type_pattern, key, defaultValue);
            Logging.log(Log.DEBUG, TAG, message + " encypted value : BEGIN_" + encryptedVal + "_END");
            EventBus.getDefault().post(new ShowMessageEvent(ShowMessageEvent.TYPE_WARN, R.string.alert_warning, message));
            return defaultValue;
        }
    }

    public String decryptValue(Context context, SharedPreferences prefs, String key, String encryptedVal, String defaultValue) {
        try {
            return newObfuscator.unobfuscateString(encryptedVal, key);
        } catch (ValidationException e) {
            try {
                String decrypted = oldObfuscator.unobfuscateString(encryptedVal, key);
                Logging.recordException(new MigratingEncryptedPreferenceException());
                writeSecurePreference(prefs, key, decrypted);
                return decrypted;
            } catch (ValidationException e1) {
                // do nothing
            }
            String message = context.getString(R.string.preference_corrupt_please_re_type_pattern, key, defaultValue);
            Logging.log(Log.DEBUG, TAG, message + " encypted value : BEGIN_" + encryptedVal + "_END");
            EventBus.getDefault().post(new ShowMessageEvent(ShowMessageEvent.TYPE_WARN, R.string.alert_warning, message));
            return defaultValue;
        }
    }

    public String encryptValue(String key, byte[] value) {
        return newObfuscator.obfuscateBytes(value, key);
    }

    public String encryptValue(String key, String value) {
        return newObfuscator.obfuscateString(value, key);
    }

    private static final class MigratingEncryptedPreferenceException extends RuntimeException {
        private static final long serialVersionUID = -8568848604418315926L;
        // Use so we can see when to remove the old style obfuscator.
    }
}
