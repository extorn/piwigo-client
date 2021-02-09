package delit.piwigoclient.ui.events.trackable;

import android.os.Parcel;

import java.util.HashSet;

import delit.libs.ui.util.ParcelUtils;

/**
 * Created by gareth on 19/09/17.
 */

public class LongSetSelectionNeededEvent extends TrackableRequestEvent {
    private final boolean allowMultiSelect;
    private final boolean allowEditing;
    private final boolean initialSelectionLocked;
    private final HashSet<Long> initialSelection;

    public LongSetSelectionNeededEvent(boolean allowMultiSelect, boolean allowEditing, HashSet<Long> currentSelection) {
        this(allowMultiSelect, allowEditing, false, currentSelection);
    }

    public LongSetSelectionNeededEvent(boolean allowMultiSelect, boolean allowEditing, boolean initialSelectionLocked, HashSet<Long> initialSelection) {
        this.allowMultiSelect = allowMultiSelect;
        this.initialSelection = initialSelection;
        this.allowEditing = allowEditing;
        this.initialSelectionLocked = initialSelectionLocked;
    }

    public LongSetSelectionNeededEvent(Parcel in) {
        super(in);
        allowMultiSelect = ParcelUtils.readBool(in);
        allowEditing = ParcelUtils.readBool(in);
        initialSelectionLocked = ParcelUtils.readBool(in);
        initialSelection = ParcelUtils.readLongSet(in);
    }

    public HashSet<Long> getInitialSelection() {
        return initialSelection;
    }

    public boolean isInitialSelectionLocked() {
        return initialSelectionLocked;
    }

    public boolean isAllowMultiSelect() {
        return allowMultiSelect;
    }

    public boolean isAllowEditing() {
        return allowEditing;
    }

    public static final Creator<LongSetSelectionNeededEvent> CREATOR = new Creator<LongSetSelectionNeededEvent>() {
        @Override
        public LongSetSelectionNeededEvent createFromParcel(Parcel in) {
            return new LongSetSelectionNeededEvent(in);
        }

        @Override
        public LongSetSelectionNeededEvent[] newArray(int size) {
            return new LongSetSelectionNeededEvent[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        ParcelUtils.writeBool(dest, allowMultiSelect);
        ParcelUtils.writeBool(dest, allowEditing);
        ParcelUtils.writeBool(dest, initialSelectionLocked);
        ParcelUtils.writeLongSet(dest, initialSelection);
    }
}
