package delit.piwigoclient.ui.common.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;

import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.preference.DialogPreference;
import androidx.preference.PreferenceManager;

import delit.libs.ui.util.DisplayUtils;
import delit.piwigoclient.database.AppSettingsViewModel;

/**
 * Created by gareth on 15/07/17.
 */

public class UriPermissionsListPreference extends DialogPreference {


    private AppSettingsViewModel appSettingsViewModel;

    public UriPermissionsListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initPreference(context, attrs);
    }

    public UriPermissionsListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPreference(context, attrs);
    }

    public UriPermissionsListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPreference(context, attrs);
    }

    public UriPermissionsListPreference(Context context) {
        super(context);
        initPreference(context, null);
    }

    private void initPreference(Context context, AttributeSet attrs) {
        ViewModelStoreOwner viewModelProvider = DisplayUtils.getViewModelStoreOwner(context);
        appSettingsViewModel = new ViewModelProvider(viewModelProvider).get(AppSettingsViewModel.class);
    }

    @Override
    public void onDetached() {
        super.onDetached();
        appSettingsViewModel = null;
    }

    public AppSettingsViewModel getAppSettingsViewModel() {
        return appSettingsViewModel;
    }

    /**
     * Returns the value of the key.
     *
     * @return The value of the key.
     */
    public String getValue() {
        return null;//currentValue;
    }

    public void persistStringValue(String value) {

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
                new Creator<UriPermissionsListPreference.SavedState>() {
                    public UriPermissionsListPreference.SavedState createFromParcel(Parcel in) {
                        return new UriPermissionsListPreference.SavedState(in);
                    }

                    public UriPermissionsListPreference.SavedState[] newArray(int size) {
                        return new UriPermissionsListPreference.SavedState[size];
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