package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import delit.piwigoclient.ui.events.trackable.FolderSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.FolderSelectionNeededEvent;

public class LocalFoldersListPreference extends Preference {

    private int folderSelectActionId = -1;
    private String value;

    public LocalFoldersListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public LocalFoldersListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LocalFoldersListPreference(Context context) {
        super(context);
    }

    @Override
    public CharSequence getSummary() {
        CharSequence summaryPattern = super.getSummary();
        if(summaryPattern == null) {
            return null;
        }
        String summaryPatternStr = summaryPattern.toString();
        return String.format(summaryPatternStr, getValue());
    }

    @Override
    protected void onAttachedToActivity() {
        if(!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
        super.onAttachedToActivity();
    }

    @Override
    protected void onPrepareForRemoval() {
        EventBus.getDefault().unregister(this);
        super.onPrepareForRemoval();
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View v = super.onCreateView(parent);
        setOnPreferenceClickListener(new OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                requestFolderSelection();
                return true;
            }
        });
        return v;
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        setValue(restorePersistedValue ? getPersistedString(this.value) : (String) defaultValue);
    }

    protected String getValue() {
        value = getPersistedString(this.value);
        return value;
    }

    protected void setValue(String newValue) {
        this.value = newValue;
        SharedPreferences.Editor e = getEditor();
        e.putString(getKey(), newValue);
        e.commit();
    }

    private void requestFolderSelection() {
        FolderSelectionNeededEvent fileSelectNeededEvent = new FolderSelectionNeededEvent();
        fileSelectNeededEvent.setInitialFolder(getValue());
        folderSelectActionId = fileSelectNeededEvent.getActionId();
        EventBus.getDefault().post(fileSelectNeededEvent);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(FolderSelectionCompleteEvent event) {
        if(event.getActionId() != this.folderSelectActionId) {
            return;
        }
        folderSelectActionId = -1;

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
        myState.trackedRequest = folderSelectActionId;
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
        setValue(myState.value);
        folderSelectActionId = myState.trackedRequest;
    }


    private static class SavedState extends BaseSavedState {

        public static final Creator<SavedState> CREATOR =
                new Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
        private int trackedRequest;
        public String value;

        public SavedState(Parcel source) {
            super(source);
            trackedRequest = source.readInt();
            value = source.readString();
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(trackedRequest);
            dest.writeString(value);
        }
    }
    
}
