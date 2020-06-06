package delit.piwigoclient.ui.common.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;

import androidx.preference.Preference;

import org.greenrobot.eventbus.EventBus;

import delit.piwigoclient.ui.events.trackable.TrackableRequestEvent;
import delit.piwigoclient.ui.events.trackable.TrackableResponseEvent;

/**
 * Created by gareth on 15/07/17.
 */

public abstract class EventDrivenPreference<T extends TrackableRequestEvent> extends Preference {

    private String currentValue;
    private int trackedEventId = -1;

    public EventDrivenPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initPreference(context, attrs);
    }

    public EventDrivenPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPreference(context, attrs);
    }

    public EventDrivenPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPreference(context, attrs);
    }

    public EventDrivenPreference(Context context) {
        super(context);
        initPreference(context, null);
    }

    @Override
    protected void onClick() {
        TrackableRequestEvent event = buildOpenSelectionEvent();
        trackedEventId = event.getActionId();
        EventBus.getDefault().post(event);
    }

    protected abstract T buildOpenSelectionEvent();

    protected void initPreference(Context context, AttributeSet attrs) {
    }

    @Override
    protected void onPrepareForRemoval() {
        EventBus.getDefault().unregister(this);
        super.onPrepareForRemoval();
    }

    @Override
    public void onAttached() {
        super.onAttached();
        if(!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        setValue(getPersistedString((String) defaultValue));
    }

    protected String getValue() {
        currentValue = getPersistedString(this.currentValue);
        return currentValue;
    }

    @Override
    protected boolean persistString(String newValue) {
        this.currentValue = newValue;
        return super.persistString(currentValue);
    }

    public void setValue(String newValue) {
        if(callChangeListener(newValue)) {
            final boolean wasBlocking = shouldDisableDependents();

            persistString(newValue);

            final boolean isBlocking = shouldDisableDependents();
            if (isBlocking != wasBlocking) {
                notifyDependencyChange(isBlocking);
            }

            notifyChanged(); // this will update the summary text.
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
        myState.value = this.currentValue;
        myState.trackedEventId = this.trackedEventId;
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
        this.currentValue = myState.value; //TODO I think this currentValue field is totally pointless but it is in EditTextPreference!
        this.trackedEventId = myState.trackedEventId;
    }

    public static class SavedState extends BaseSavedState {

        public static final Creator<SavedState> CREATOR =
                new Creator<EventDrivenPreference.SavedState>() {
                    public EventDrivenPreference.SavedState createFromParcel(Parcel in) {
                        return new EventDrivenPreference.SavedState(in);
                    }

                    public EventDrivenPreference.SavedState[] newArray(int size) {
                        return new EventDrivenPreference.SavedState[size];
                    }
                };

        private String value;
        private int trackedEventId;

        public SavedState(Parcel source) {
            super(source);
            value = source.readString();
            trackedEventId = source.readInt();
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(value);
            dest.writeInt(trackedEventId);
        }
    }

    protected String getCurrentValue() {
        return currentValue;
    }

    protected boolean isTrackingEvent(TrackableResponseEvent event) {
        if(trackedEventId == event.getActionId()) {
            trackedEventId = -1;
            return true;
        }
        return false;
    }
}