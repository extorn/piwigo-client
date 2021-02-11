package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;

import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ListIterator;

import delit.libs.ui.view.preference.MyDialogPreference;
import delit.libs.util.X509Utils;

/**
 * Created by gareth on 15/07/17.
 */

public abstract class KeyStorePreference extends MyDialogPreference {

    public static final String BKS_FILE_SUFFIX = "bks";
    private boolean justKeysWanted;
    private KeyStore currentValue;


    private ArrayList<String> allowedCertificateFileTypes = new ArrayList<>(Arrays.asList("cer", "cert", "pem"));
    private ArrayList<String> allowedKeyFileTypes = new ArrayList<>(Arrays.asList("p12", "pkcs12", "pfx", BKS_FILE_SUFFIX));


    public KeyStorePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public KeyStorePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public KeyStorePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public KeyStorePreference(Context context) {
        super(context);
    }

    public void setJustKeysWanted(boolean keysWanted) {
        this.justKeysWanted = keysWanted;
    }

    public void setAllowedCertificateFileTypes(ArrayList<String> allowedCertificateFileTypes) {
        for (final ListIterator<String> i = allowedCertificateFileTypes.listIterator(); i.hasNext(); ) {
            String element = i.next();
            i.set(element.toLowerCase());
        }
        this.allowedCertificateFileTypes = allowedCertificateFileTypes;
    }

    /**
     *
     * @param allowedKeyFileTypes just file extension, i.e. bks NOT .bks
     */
    public void setAllowedKeyFileTypes(ArrayList<String> allowedKeyFileTypes) {
        for (final ListIterator<String> i = allowedCertificateFileTypes.listIterator(); i.hasNext(); ) {
            String element = i.next();
            i.set(element.toLowerCase());
        }
        this.allowedKeyFileTypes = allowedKeyFileTypes;
    }


    @Override
    protected void onSetInitialValue(Object defaultValue) {
        setKeystore(loadKeystore());
    }

    /**
     * Returns the value of the key.
     *
     * @return The value of the key.
     */
    public KeyStore getKeystore() {
        if(currentValue == null) {
            currentValue = loadKeystore();
        }
        return currentValue;
    }

    public void setKeystore(KeyStore keystore) {
        if(!X509Utils.areEqual(currentValue, keystore)) {
            saveKeystore(keystore);
            currentValue = keystore;
            if(currentValue != null) {
                notifyChanged();
            }
        }
    }

    protected abstract void saveKeystore(KeyStore keystore);

    protected abstract KeyStore loadKeystore();

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            // No need to save instance state since it's persistent
            return superState;
        }

        final KeystorePrefSavedState myState = new KeystorePrefSavedState(superState);
        myState.value = currentValue;
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(KeystorePrefSavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        KeystorePrefSavedState myState = (KeystorePrefSavedState) state;

        currentValue = myState.value;

        super.onRestoreInstanceState(myState.getSuperState());
    }

    public ArrayList<String> getAllowedCertificateFileTypes() {
        return allowedCertificateFileTypes;
    }

    public ArrayList<String> getAllowedKeyFileTypes() {
        return allowedKeyFileTypes;
    }

    public boolean isJustKeysWanted() {
        return justKeysWanted;
    }

    private static class KeystorePrefSavedState extends BaseSavedState {

        public static final Creator<KeystorePrefSavedState> CREATOR =
                new Creator<KeystorePrefSavedState>() {
                    public KeystorePrefSavedState createFromParcel(Parcel in) {
                        return new KeystorePrefSavedState(in);
                    }

                    public KeystorePrefSavedState[] newArray(int size) {
                        return new KeystorePrefSavedState[size];
                    }
                };
        private static final char[] ksPass = new char[]{'O', 'g', 'r', 'S', 'W', '1', 'n', 's', 'h', 'E', 'H', 'D', '8', 'b', 'v', 'c', '7', 't', 'Z', 'J'};
        private int ksByteCount;
        private String ksType;
        private KeyStore value;

        public KeystorePrefSavedState(Parcel source) {
            super(source);
            ksByteCount = source.readInt();
            ksType = source.readString();
            byte[] ksBytes = new byte[ksByteCount];
            source.readByteArray(ksBytes);
            value = X509Utils.deserialiseKeystore(ksBytes, ksPass, ksType);
        }

        public KeystorePrefSavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            byte[] ksBytes = X509Utils.serialiseKeystore(value, ksPass);
            dest.writeInt(ksBytes.length);
            dest.writeString(value.getType());
            dest.writeByteArray(ksBytes);
        }
    }





}