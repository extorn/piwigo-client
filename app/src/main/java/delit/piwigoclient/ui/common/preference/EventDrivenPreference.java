package delit.piwigoclient.ui.common.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.AttributeSet;

import org.greenrobot.eventbus.EventBus;

import androidx.preference.Preference;
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

    public void persistStringValue(String value)
    {
        final boolean changed = !TextUtils.equals(getPersistedString(null), value);
        if (changed) {
            currentValue = value;
            persistString(value);
            notifyChanged();
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

    protected String getValue() {
        currentValue = getPersistedString(this.currentValue);
        return currentValue;
    }

    protected void setValue(String newValue) {
        this.currentValue = newValue;
        super.persistString(currentValue);
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
        this.currentValue = myState.value;
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

    public String getCurrentValue() {
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