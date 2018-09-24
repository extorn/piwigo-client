package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v7.preference.DialogPreference;
import android.util.AttributeSet;

import java.util.ArrayList;
import java.util.List;

import delit.piwigoclient.R;
import delit.piwigoclient.util.CollectionUtils;
import delit.piwigoclient.util.ObjectUtils;

public class AutoUploadJobsPreference extends DialogPreference {

    private boolean mValueSet;
    private String mValue;

    public AutoUploadJobsPreference(Context context, AttributeSet attrs, int defStyleAttr,  int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initPreference(context, attrs);
    }

    public AutoUploadJobsPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPreference(context, attrs);
    }

    public AutoUploadJobsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPreference(context, attrs);
    }

    public AutoUploadJobsPreference(Context context) {
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
        return mValue;
    }

    /**
     * Sets the value of the key.
     *
     * @param value The value to set for the key.
     */
    public void setValue(String value) {
        // Always persist/notify the first time.
        boolean changed = !ObjectUtils.areEqual(this.mValue, value);
        if (!mValueSet || changed) {
            this.mValue = value;
            mValueSet = true;
            persistString(mValue);
            if (changed) {
                notifyChanged();
            }
        }
    }

    @Override
    public CharSequence getSummary() {
        int count = getUploadJobIdsFromValue().size();
        return getContext().getString(R.string.preference_data_upload_automatic_upload_jobs_summary, count);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        mValue = restoreValue ? getPersistedString("") : (String) defaultValue;
        setValue(mValue);
    }

    public ArrayList<Integer> getUploadJobIdsFromValue() {
        return CollectionUtils.integersFromCsvList(mValue);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            // No need to save instance state since it's persistent
            return superState;
        }

        final AutoUploadJobsPreference.SavedState myState = new AutoUploadJobsPreference.SavedState(superState);
        myState.value = getValue();
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(AutoUploadJobsPreference.SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        AutoUploadJobsPreference.SavedState myState = (AutoUploadJobsPreference.SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        setValue(myState.value);
    }


    public static class SavedState extends BaseSavedState {

        public static final Creator<AutoUploadJobsPreference.SavedState> CREATOR =
                new Creator<AutoUploadJobsPreference.SavedState>() {
                    public AutoUploadJobsPreference.SavedState createFromParcel(Parcel in) {
                        return new AutoUploadJobsPreference.SavedState(in);
                    }

                    public AutoUploadJobsPreference.SavedState[] newArray(int size) {
                        return new AutoUploadJobsPreference.SavedState[size];
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
