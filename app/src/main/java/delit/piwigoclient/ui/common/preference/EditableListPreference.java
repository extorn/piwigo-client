package delit.piwigoclient.ui.common.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.preference.DialogPreference;
import androidx.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.AttributeSet;

import com.crashlytics.android.Crashlytics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.util.ParcelUtils;

/**
 * Created by gareth on 23/01/18.
 */

public class EditableListPreference extends DialogPreference {

    // State persistent values
    private String currentValue;
    private HashSet<String> entries;
    // Non state persistent values (because they are never altered)
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

            notifyChanged();
            if (listener != null) {
                listener.onItemSelectionChange(oldValue, value, entries.contains(oldValue));
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
        final String entry = getValue();
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

    public boolean isAllowItemEdit() {
        return allowItemEdit;
    }

    public void updateEntryValues(ArrayList<EditableListPreferenceDialogFragmentCompat.ListAction> actions) {
        for(EditableListPreferenceDialogFragmentCompat.ListAction action : actions) {
            if(action instanceof EditableListPreferenceDialogFragmentCompat.Removal) {
                if (listener != null) {
                    listener.onItemRemoved(action.entryValue);
                }
                entries.remove(action.entryValue);
            } else if(action instanceof EditableListPreferenceDialogFragmentCompat.Addition) {
                if (listener != null) {
                    listener.onItemAdded(action.entryValue);
                }
                entries.add(action.entryValue);
            } else if(action instanceof EditableListPreferenceDialogFragmentCompat.Replacement) {
                if (listener != null) {
                    listener.onItemAltered(this, action.entryValue, ((EditableListPreferenceDialogFragmentCompat.Replacement) action).newEntryValue);
                }
                entries.remove(action.entryValue);
                entries.add(((EditableListPreferenceDialogFragmentCompat.Replacement) action).newEntryValue);
            }
        }
        persistEntries(entries);
    }

    public void addAndSelectItem(String item) {
        loadEntries();
        EditableListPreferenceDialogFragmentCompat.Addition action = new EditableListPreferenceDialogFragmentCompat.Addition(item);
        ArrayList<EditableListPreferenceDialogFragmentCompat.ListAction> actions = new ArrayList<>(1);
        actions.add(action);
        updateEntryValues(actions);
        setValue(item);
    }

    public interface EditableListPreferenceChangeListener extends Serializable {
        void onItemAdded(String newItem);

        void onItemRemoved(String newItem);

        void onItemSelectionChange(String oldSelection, String newSelection, boolean oldSelectionExists);

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
            entries = ParcelUtils.readStringSet(source, null);
            entriesAltered = ParcelUtils.readValue(source,null, boolean.class);
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(value);
            ParcelUtils.writeStringSet(dest, entries);
            dest.writeValue(entriesAltered);
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
        public void onItemAltered(EditableListPreference preference, String oldValue, String newValue) {
        }
    }


}
