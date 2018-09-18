package delit.piwigoclient.ui.common.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v7.preference.DialogPreference;
import android.support.v7.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.AttributeSet;

import com.crashlytics.android.Crashlytics;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import delit.piwigoclient.R;

/**
 * Created by gareth on 23/01/18.
 */

public class EditableListPreference extends DialogPreference {

    // State persistant values
    private String currentValue;
    private HashSet<String> entries;
    private boolean entriesAltered;
    // Non state persistant values (because they are never altered)
    private int entriesPref;
    private String summary;
    private EditableListPreferenceChangeListener listener;
    private boolean allowItemEdit;

    public EditableListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initPreference(context, attrs, defStyleRes);
    }

    public EditableListPreference(Context context, AttributeSet attrs, int defStyleRes) {
        super(context, attrs, defStyleRes);
        initPreference(context, attrs, defStyleRes);
    }

    public EditableListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPreference(context, attrs, 0);
    }

    public EditableListPreference(Context context) {
        super(context, null);
        initPreference(context, null, 0);
    }

    private void initPreference(Context context, AttributeSet attrs, int defStyleRes) {
        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.EditableListPreference, 0, defStyleRes);
        entriesPref = a.getResourceId(R.styleable.EditableListPreference_entriesPref, -1);
        if (entriesPref < 0) {
            throw new IllegalArgumentException("entriesPref is a mandatory field");
        }

        allowItemEdit = a.getBoolean(R.styleable.EditableListPreference_allowItemEdit, false);

        a.recycle();

        summary = String.valueOf(super.getSummary());
    }

    public void setListener(EditableListPreferenceChangeListener listener) {
        this.listener = listener;
    }

    public EditableListPreferenceChangeListener getListener() {
        return listener;
    }

    /**
     * Override to load list values from a different location than shared preferences.
     *
     */
    public void loadEntries() {
        entriesAltered = false;
        entries = new HashSet<>(getPreferenceManager().getSharedPreferences().getStringSet(getContext().getString(entriesPref), new HashSet<String>(1)));
    }

    /**
     * Returns the value of the key. This should be one of the entriesList in
     * {@link #getEntryValues()}.
     *
     * @return The value of the key.
     */
    public String getValue() {
        return currentValue;
    }

    /**
     * Sets the value of the key. This should be one of the entriesList in
     * {@link #getEntryValues()}.
     *
     * @param value The value to set for the key.
     */
    public void setValue(String value) {
        // Always persist/notify the first time.
        final boolean changed = !TextUtils.equals(currentValue, value);
        if (changed) {
            String oldValue = currentValue;
            currentValue = value;
            persistString(value);
            persistEntries(entries);

            if (changed) {
                notifyChanged();
                if (listener != null) {
                    listener.onItemSelectionChanged(this, oldValue, value, entries.contains(oldValue));
                }
            }
        }
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setValue(restoreValue ? getPersistedString(currentValue) : (String) defaultValue);
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
        myState.entries = entries;
        myState.entriesAltered = entriesAltered;
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
        entries = myState.entries;
        entriesAltered = myState.entriesAltered;
    }

    /**
     * Use identity mapping (i.e. value identical to displayed text).
     *
     * @return
     */
    protected Set<String> getEntryValues() {
        return entries;
    }

    /**
     * Attempts to persist a set of Strings if this Preference is persistent.
     *
     * @param values The values to persist.
     * @return True if this Preference is persistent. (This is not whether the
     * value was persisted, since we may not necessarily commit if there
     * will be a batch commit later.)
     * @see #getPersistedStringSet(Set)
     */
    protected boolean persistEntries(Set<String> values) {
        if (!shouldPersist()) {
            return false;
        }
        if(!entriesAltered) {
            return false;
        }

        PreferenceManager prefMan = getPreferenceManager();
        SharedPreferences.Editor editor = prefMan.getSharedPreferences().edit();
        editor.putStringSet(getContext().getString(entriesPref), values);
        try {
            editor.apply();
        } catch (AbstractMethodError e) {
            Crashlytics.logException(e);
            // The app injected its own pre-Gingerbread
            // SharedPreferences.Editor implementation without
            // an apply method.
            editor.commit();
        }
        return true;
    }

    protected String getEntry() {
        return getValue();
    }

    /**
     * Returns the summary of this ListPreference. If the summary
     * has a {@linkplain java.lang.String#format String formatting}
     * marker in it (i.e. "%s" or "%1$s"), then the current entry
     * value will be substituted in its place.
     *
     * @return the summary with appropriate string substitution
     */
    @Override
    public CharSequence getSummary() {
        final CharSequence entry = getEntry();
        if (summary == null) {
            return super.getSummary();
        } else {
            return String.format(summary, entry == null ? "" : entry);
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    /**
     * Implement to allow deletion of items from the list (called on delete button click)
     *
     * @param position
     * @param entry
     * @param entryValue
     */
    public void deleteItemFromList(int position, String entry, String entryValue) {
        // clone the entries set so we don't inadvertently change the cached property value
        entriesAltered = true;
        // delete the item from the list.
        entries.remove(entry);
        if (listener != null) {
            listener.onItemRemoved(entryValue);
        }
    }

    public void replaceValue(String oldValue, String newValue) {
        entriesAltered = true;
        entries.remove(oldValue);
        entries.add(newValue);
    }

    public void addNewValue(String newItem) {
        entriesAltered = true;
        entries.add(newItem);
    }

    public boolean isAllowItemEdit() {
        return allowItemEdit;
    }

    public boolean isEntriesAltered() {
        return entriesAltered;
    }

    public interface EditableListPreferenceChangeListener extends Serializable {
        void onItemAdded(String newItem);

        void onItemRemoved(String newItem);

        void onItemSelectionChange(String oldSelection, String newSelection, boolean oldSelectionExists);

        void onItemSelectionChanged(EditableListPreference preference, String oldSelection, String newSelection, boolean oldSelectionExists);

        void onItemAltered(EditableListPreference preference, String oldValue, String newValue);
    }

    public static class SavedState extends BaseSavedState {
        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
        private String value;
        private HashSet<String> entries;
        private boolean entriesAltered;

        public SavedState(Parcel source) {
            super(source);
            value = source.readString();
            entries = (HashSet<String>) source.readSerializable();
            entriesAltered = source.readInt() != 0;
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(value);
            dest.writeSerializable(entries);
            dest.writeInt(entriesAltered ? 1 : 0);
        }
    }

    public static class EditableListPreferenceChangeAdapter implements EditableListPreferenceChangeListener {

        @Override
        public void onItemAdded(String newItem) {
        }

        @Override
        public void onItemRemoved(String newItem) {
        }

        @Override
        public void onItemSelectionChange(String oldSelection, String newSelection, boolean oldSelectionExists) {
        }

        @Override
        public void onItemSelectionChanged(EditableListPreference preference, String oldSelection, String newSelection, boolean oldSelectionExists) {
        }

        @Override
        public void onItemAltered(EditableListPreference preference, String oldValue, String newValue) {
        }
    }


}
