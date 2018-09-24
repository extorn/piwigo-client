package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v7.preference.DialogPreference;
import android.support.v7.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.AttributeSet;

import com.google.android.gms.common.util.Strings;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;

/**
 * Created by gareth on 15/07/17.
 */

public class ServerConnectionsListPreference extends DialogPreference {
    private String currentValue;

    public ServerConnectionsListPreference(Context context, AttributeSet attrs, int defStyleAttr,  int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initPreference(context, attrs);
    }

    public ServerConnectionsListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPreference(context, attrs);
    }

    public ServerConnectionsListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPreference(context, attrs);
    }

    public ServerConnectionsListPreference(Context context) {
        super(context);
        initPreference(context, null);
    }

    private void initPreference(Context context, AttributeSet attrs) {
    }

    /**
     * Returns the value of the key.
     *
     * @return The value of the key.
     */
    public String getValue() {
        return currentValue;
    }

    public void persistStringValue(String value) {
        final boolean changed = !TextUtils.equals(currentValue, value);
        if (changed) {
            String oldValue = currentValue;
            currentValue = value;
            persistString(value);

            if (changed) {
                notifyChanged();
            }
        }
    }

    @Override
    public CharSequence getSummary() {
        SharedPreferences prefs = getAppSharedPreferences();
        currentValue = getPersistedString(currentValue);
        ServerConnection activeConnection;
        if (currentValue != null) {
            ConnectionPreferences.ProfilePreferences selectedPref = ConnectionPreferences.getPreferences(currentValue);
            activeConnection = new ServerConnection(currentValue,
                    selectedPref.getPiwigoServerAddress(prefs, getContext()),
                    selectedPref.getPiwigoUsername(prefs, getContext()));
        } else {
            activeConnection = new ServerConnection(null, null, null);
        }
        return activeConnection.getSummary(getContext());
    }

    private SharedPreferences getAppSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
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
        myState.value = getValue();
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
        persistStringValue(myState.value);
    }

    public static class SavedState extends BaseSavedState {

        public static final Creator<SavedState> CREATOR =
                new Creator<ServerConnectionsListPreference.SavedState>() {
                    public ServerConnectionsListPreference.SavedState createFromParcel(Parcel in) {
                        return new ServerConnectionsListPreference.SavedState(in);
                    }

                    public ServerConnectionsListPreference.SavedState[] newArray(int size) {
                        return new ServerConnectionsListPreference.SavedState[size];
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

    protected static class ServerConnection {

        private String serverName;
        private String username;
        private String profileName;

        public ServerConnection(String profileName, String piwigoServerAddress, String piwigoUsername) {
            this.profileName = profileName;
            this.serverName = piwigoServerAddress;
            this.username = piwigoUsername;
        }

        public String getServerName() {
            return serverName;
        }

        public String getUsername() {
            return username;
        }

        public String getProfileName() {
            return profileName;
        }

        public String getSummary(Context c) {
            if (serverName == null) {
                return c.getString(R.string.server_connection_preference_summary_default);
            }
            String user = Strings.emptyToNull(username);
            if (user == null) {
                user = c.getString(R.string.server_connection_preference_user_guest);
            }
            return user + '@' + serverName;
        }
    }



}