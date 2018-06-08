package delit.piwigoclient.ui.common.preference;

/**
 * Created by gareth on 07/11/17.
 */

interface SecurePreference<T> {
    T encrypt(T value);

    T decrypt(T encryptedVal, T defaultReturnValue);
}
