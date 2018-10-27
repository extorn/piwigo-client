package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v7.preference.DialogPreference;
import android.text.TextUtils;
import android.util.AttributeSet;

import java.util.Locale;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItemStub;

/**
 * Created by gareth on 15/07/17.
 */

public class ServerAlbumListPreference extends DialogPreference {

    private String currentValue;
    // non state persistent values
    private String connectionProfileNamePreferenceKey;
    private SharedPreferences.OnSharedPreferenceChangeListener listener;

    public ServerAlbumListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initPreference(context, attrs);
    }

    public ServerAlbumListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPreference(context, attrs);
    }

    public ServerAlbumListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPreference(context, attrs);
    }

    public ServerAlbumListPreference(Context context) {
        super(context);
        initPreference(context, null);
    }

    private void initPreference(Context context, AttributeSet attrs) {
        final TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.ServerAlbumListPreference, 0, 0);
        connectionProfileNamePreferenceKey = a.getString(R.styleable.ServerAlbumListPreference_connectionProfileNameKey);
        a.recycle();
        listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if(key.equals(connectionProfileNamePreferenceKey)) {
                    setEnabled(null != sharedPreferences.getString(connectionProfileNamePreferenceKey, null));
                }
            }
        };
    }

    @Override
    public void onAttached() {
        getSharedPreferences().registerOnSharedPreferenceChangeListener(listener);
        listener.onSharedPreferenceChanged(getSharedPreferences(), connectionProfileNamePreferenceKey);
        super.onAttached();
    }

    @Override
    public void onDetached() {
        getSharedPreferences().unregisterOnSharedPreferenceChangeListener(listener);
        super.onDetached();
    }

    public void persistStringValue(String value)
    {
        final boolean changed = !TextUtils.equals(currentValue, value);
        if (changed) {
            String oldValue = currentValue;
            currentValue = value;
            persistString(value);
            notifyChanged();
        }
    }

//
//    /**
//     * Sets the value of the key.
//     *
//     * @param value The value to set for the key.
//     */
//    private void setValue(String value) {
//        // Always persist/notify the first time.
//        boolean changed = !ObjectUtils.areEqual(this.value, value);
//        if (!mValueSet || changed) {
//            this.value = value;
//            mValueSet = true;
//            persistString(this.value);
//            if (changed) {
//                notifyChanged();
//            }
//        }
//    }

    @Override
    public CharSequence getSummary() {
        currentValue = getPersistedString(currentValue);
        if (super.getSummary() == null) {
            return ServerAlbumPreference.getSelectedAlbumName(currentValue);
        } else {
            String albumName = ServerAlbumPreference.getSelectedAlbumName(currentValue);
            if (albumName != null) {
                return String.format(super.getSummary().toString(), albumName);
            } else {
                return getContext().getString(R.string.server_album_preference_summary_default);
            }
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        persistStringValue(restoreValue ? getPersistedString(currentValue) : (String) defaultValue);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            // No need to save instance state since it's persistent
            return superState;
        }

        final SavedState myState = new SavedState(superState);
        myState.value = this.currentValue;
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        this.currentValue = myState.value;
    }

    public long getSelectedAlbumId() {
        return ServerAlbumPreference.getSelectedAlbumId(currentValue);
    }

    public String getConnectionProfileNamePreferenceKey() {
        return connectionProfileNamePreferenceKey;
    }

    public static class ServerAlbumPreference {

        public static String toValue(@NonNull CategoryItemStub album) {
            return String.format(Locale.UK,"%1$d;%2$s", album.getId(), album.getName());
        }

        public static long getSelectedAlbumId(String preferenceValue) {
            if (preferenceValue != null) {
                return Long.valueOf(preferenceValue.split(";", 2)[0]);
            }
            return -1;
        }

        public static String getSelectedAlbumName(String preferenceValue) {
            if (preferenceValue != null) {
                return preferenceValue.split(";", 2)[1];
            }
            return null;
        }
    }

    public static class SavedState extends BaseSavedState {

        public static final Creator<SavedState> CREATOR =
                new Creator<ServerAlbumListPreference.SavedState>() {
                    public ServerAlbumListPreference.SavedState createFromParcel(Parcel in) {
                        return new ServerAlbumListPreference.SavedState(in);
                    }

                    public ServerAlbumListPreference.SavedState[] newArray(int size) {
                        return new ServerAlbumListPreference.SavedState[size];
                    }
                };

        private String value;

        public SavedState(Parcel source) {
            super(source);
            value = source.readString();
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(value);
        }
    }





}