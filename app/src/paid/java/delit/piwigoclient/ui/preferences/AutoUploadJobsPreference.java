package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;

import androidx.preference.PreferenceDataStore;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import delit.libs.ui.util.ParcelUtils;
import delit.libs.ui.view.preference.MyDialogPreference;
import delit.libs.util.CollectionUtils;
import delit.libs.util.ObjectUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.PiwigoUtils;
import delit.piwigoclient.ui.events.trackable.AutoUploadJobViewCompleteEvent;
import delit.piwigoclient.ui.events.trackable.TrackableEventManager;
import delit.piwigoclient.ui.events.trackable.TrackableRequestEvent;

public class AutoUploadJobsPreference extends MyDialogPreference {

    private boolean mValueSet;
    private String mValue;
    private ActiveState currentState;

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
        setPositiveButtonText(R.string.gallery_details_save_button);
        currentState = new ActiveState(); // init this to store all user progress.
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
        setValue(value, false);
    }

    protected boolean persistString(String value, boolean force) {
        if (!shouldPersist()) {
            return false;
        }
        if(force) {
            PreferenceDataStore dataStore = getPreferenceDataStore();
            if (dataStore != null) {
                dataStore.putString(getKey(), value);
            } else {
                SharedPreferences.Editor editor = getPreferenceManager().getSharedPreferences().edit();
                editor.putString(getKey(), value);
                editor.commit();
            }
            return true;
        }
        return super.persistString(value);
    }

    /**
     * Sets the value of the key.
     *
     * @param value The value to set for the key.
     * @param jobContentsHaveChanged if true will force a change notification to be posted
     */
    public void setValue(String value, boolean jobContentsHaveChanged) {
        // Always persist/notify the first time.
        boolean changed = !ObjectUtils.areEqual(this.mValue, value);
        if (!mValueSet || changed || jobContentsHaveChanged) {
            this.mValue = value;
            mValueSet = true;
            persistString(mValue, jobContentsHaveChanged);
        }
        if (changed || jobContentsHaveChanged) {
            notifyChanged();
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

    private boolean hasChanged(AutoUploadJobConfig cfg) {
        String newSummary = cfg.getSummary(getSharedPreferences(), getContext());
        return !ObjectUtils.areEqual(getActiveState().getActiveUploadJobConfigSummary(), newSummary);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AutoUploadJobViewCompleteEvent event) {
        if(!getActiveState().isTrackingEvent(event)) {
            return;
        }
        AutoUploadJobConfig cfg = new AutoUploadJobConfig(event.getJobId());
        if(hasChanged(cfg) || !getActiveState().hasUploadJobId(cfg.getJobId())) {
            getActiveState().setJobsHaveChanged(true);
        }
        addAutoUploadJobConfigToListIfNew(cfg);
        onClick();
    }

    @Override
    protected void onClick() {
        if(getActiveState() == null) {
            this.currentState = new ActiveState();
            getActiveState().setJobIds(getUploadJobIdsFromValue()); // we're not in the middle of changing things.
        }
        if(!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
            getActiveState().setJobIds(getUploadJobIdsFromValue()); // we're not in the middle of changing things.
        }
        super.onClick();
    }

    private void addAutoUploadJobConfigToListIfNew(AutoUploadJobConfig cfg) {
        if(cfg.exists(getContext())) {
            getActiveState().addJobCfg(cfg);
        }
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
        myState.activeState = currentState;
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
        currentState = myState.activeState;
    }

    public void onUserActionSaveChanges() {
        String val = CollectionUtils.toCsvList(getActiveState().getUploadJobIds());

        if (callChangeListener(val)) {
            // persist any changes
            setValue(val, getActiveState().isJobsHaveChanged());
            // delete any deleted.
            deletePrefsFiles(getActiveState().deletedItems);
            // this is used to track items added newly
            getActiveState().addedItems.clear();
        } else {
            onUserActionCancelChanges();
        }
        EventBus.getDefault().unregister(this);
    }

    public void onUserActionCancelChanges() {
        // revert the changes.
        getActiveState().undeleteAll();
        // delete any newly created job prefs files as we're reverting changes
        deletePrefsFiles(getActiveState().addedItems);
    }

    private void deletePrefsFiles(ArrayList<AutoUploadJobConfig> addedItems) {
        for (AutoUploadJobConfig addedJob : addedItems) {
            addedJob.deletePreferences(getContext());
        }
        addedItems.clear();
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
        private ActiveState activeState;

        public SavedState(Parcel source) {
            super(source);
            value = source.readString();
            activeState = source.readParcelable(SavedState.class.getClassLoader());
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(value);
            dest.writeParcelable(activeState, flags);
        }
    }

    protected ActiveState getActiveState() {
        return currentState;
    }

    protected static class ActiveState implements Parcelable {
        private boolean jobsHaveChanged;
        private TrackableEventManager trackableEventManager = new TrackableEventManager();
        private HashSet<AutoUploadJobConfig> currentItems = new HashSet<>();
        private ArrayList<AutoUploadJobConfig> deletedItems = new ArrayList<>();
        private ArrayList<AutoUploadJobConfig> addedItems = new ArrayList<>();
        private String activeUploadJobConfigSummary;

        protected ActiveState(){}

        protected ActiveState(Parcel in) {
            jobsHaveChanged = in.readByte() != 0;
            currentItems = ParcelUtils.readHashSet(in, getClass().getClassLoader());
            deletedItems = in.createTypedArrayList(AutoUploadJobConfig.CREATOR);
            addedItems = in.createTypedArrayList(AutoUploadJobConfig.CREATOR);
            activeUploadJobConfigSummary = in.readString();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeByte((byte) (jobsHaveChanged ? 1 : 0));
            ParcelUtils.writeSet(dest, currentItems);
            dest.writeTypedList(deletedItems);
            dest.writeTypedList(addedItems);
            dest.writeString(activeUploadJobConfigSummary);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<ActiveState> CREATOR = new Creator<ActiveState>() {
            @Override
            public ActiveState createFromParcel(Parcel in) {
                return new ActiveState(in);
            }

            @Override
            public ActiveState[] newArray(int size) {
                return new ActiveState[size];
            }
        };

        public boolean isJobsHaveChanged() {
            return jobsHaveChanged;
        }

        public void postTrackableEvent(TrackableRequestEvent event) {
            trackableEventManager.postTrackedEvent(event);
        }

        public void setJobsHaveChanged(boolean jobsHaveChanged) {
            this.jobsHaveChanged = jobsHaveChanged;
        }

        public void recordItemForDelete(AutoUploadJobConfig item) {
            deletedItems.add(item);
            currentItems.remove(item);
        }

        /**
         * @return A sorted set
         */
        public SortedSet<Long> getUploadJobIds() {
            return new TreeSet<>(PiwigoUtils.toSetOfIds(currentItems));
        }

        /**
         * @return An unsorted set
         */
        public Set<Long> getDeletedUploadJobIds() {
            return PiwigoUtils.toSetOfIds(deletedItems);
        }

        public void setActiveUploadJobConfigSummaryText(String activeJobText) {
            activeUploadJobConfigSummary = activeJobText;
        }

        public boolean isJobConfigShowing() {
            return activeUploadJobConfigSummary != null;
        }

        public String getActiveUploadJobConfigSummary() {
            return activeUploadJobConfigSummary;
        }

        protected int getNextJobId() {
            long nextJobId = 0;
            while(getUploadJobIds().contains(nextJobId)) {
                nextJobId += 1;
            }
            while(getDeletedUploadJobIds().contains(nextJobId)) {
                nextJobId += 1;
            }
            return (int) nextJobId;
        }

        public boolean isTrackingEvent(AutoUploadJobViewCompleteEvent event) {
            return trackableEventManager.isTrackingEvent(event);
        }

        public boolean hasUploadJobId(int jobId) {
            return getUploadJobIds().contains((long) jobId);
        }

        public void addJobCfg(AutoUploadJobConfig cfg) {
            currentItems.add(cfg);
            addedItems.add(cfg);
        }

        public void undeleteAll() {
            currentItems.addAll(deletedItems);
            deletedItems.clear();
        }

        public void setJobIds(ArrayList<Integer> uploadJobIdsFromValue) {
            for(Integer jobId : uploadJobIdsFromValue) {
                currentItems.add(new AutoUploadJobConfig(jobId));
            }
        }

    }

}
