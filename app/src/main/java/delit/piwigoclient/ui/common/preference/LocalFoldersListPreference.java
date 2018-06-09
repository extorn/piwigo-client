package delit.piwigoclient.ui.common.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;

import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionNeededEvent;

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
    protected void onBindView(View view) {
        super.onBindView(view);
        setOnPreferenceClickListener(new OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                requestFolderSelection();
                return true;
            }
        });
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
        String initialFolder = getValue();
        ArrayList<String> selection = new ArrayList<>();
        if(initialFolder == null) {
            initialFolder = Environment.getExternalStorageDirectory().getAbsolutePath();
        } else {
            File initialSelection = new File(initialFolder);
            if(initialSelection.exists()) {
                initialFolder = initialSelection.getParentFile().getAbsolutePath();
                selection.add(initialSelection.getAbsolutePath());
            } else {
                while(!initialSelection.exists()) {
                    initialSelection = initialSelection.getParentFile();
                }
                initialFolder = initialSelection.getAbsolutePath();
            }
        }
        FileSelectionNeededEvent fileSelectNeededEvent = new FileSelectionNeededEvent(false, true, false);
        fileSelectNeededEvent.withInitialFolder(initialFolder);
        fileSelectNeededEvent.withVisibleContent(FileSelectionNeededEvent.ALPHABETICAL);
        fileSelectNeededEvent.withInitialSelection(selection);
        folderSelectActionId = fileSelectNeededEvent.getActionId();
        EventBus.getDefault().post(fileSelectNeededEvent);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(FileSelectionCompleteEvent event) {
        if(event.getActionId() != this.folderSelectActionId) {
            return;
        }
        folderSelectActionId = -1;
        File selectedFile = event.getSelectedFiles().get(0);
        if(selectedFile.isDirectory()) {
            setValue(selectedFile.getAbsolutePath());
        }
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


    public static class SavedState extends BaseSavedState {

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