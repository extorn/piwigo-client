package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.ParcelUtils;
import delit.libs.ui.util.PreferenceUtils;
import delit.libs.ui.view.preference.MyDialogPreference;
import delit.libs.util.CollectionUtils;
import delit.libs.util.SetUtils;
import delit.piwigoclient.R;

/**
 * Created by gareth on 23/01/18.
 */

public class EditableListPreference extends MyDialogPreference {

    private static final String TAG = "EditableListPreference";
    // State persistent values
    private TreeSet<String> currentValues = new TreeSet<>();
    private TreeSet<String> entries;
    // Non state persistent values (because they are never altered)
    private int entriesPref;
    private String summary;
    private EditableListPreferenceChangeListener listener;
    private boolean allowItemEdit;
    private boolean allowMultiSelect;
    private boolean alwaysSelectAll;

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

        allowItemEdit = a.getBoolean(R.styleable.EditableListPreference_allowItemEdit, false);

        allowMultiSelect = a.getBoolean(R.styleable.EditableListPreference_allowMultiSelect, false);

        a.recycle();

        summary = String.valueOf(super.getSummary());

        if (entriesPref < 0) {
            throw new IllegalArgumentException("entriesPref is a mandatory field");
        }
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
        String entriesPrefKey = getContext().getString(entriesPref);
        if (getKey().equals(entriesPrefKey)) {
            alwaysSelectAll = true;
        }
        TreeSet<String> defVal = new TreeSet<>();
        entries = new TreeSet<>(getSharedPreferences().getStringSet(entriesPrefKey, defVal));
    }

    /**
     * Returns the value of the key. This should be one of the entriesList in
     * {@link #getEntryValues()}.
     *
     * @return The value of the key.
     */
    public String getValue() {
        if (!allowMultiSelect) {
            return currentValues.size() > 0 ? currentValues.iterator().next() : null;
        } else {
            throw new IllegalStateException("only valid in single select mode");
        }
    }

    public void setValue(String value) {
        if (!allowMultiSelect) {
            HashSet<String> newVal = new HashSet<>();
            if (value != null) {
                newVal.add(value);
            }
            boolean changed = !CollectionUtils.equals(currentValues, newVal);
            if (changed) {
                TreeSet<String> oldValues = new TreeSet<>(currentValues);
                currentValues.clear();
                currentValues.addAll(newVal);

                persistString(value);
                notifyChanged();
                if (listener != null) {
                    boolean oldSelectionNotDeleted = SetUtils.difference(oldValues, entries).size() == 0;
                    listener.onItemSelectionChange(oldValues, currentValues, oldSelectionNotDeleted);
                }
            }
        } else {
            throw new IllegalStateException("only valid in single select mode");
        }
    }

    public Set<String> getValues() {
        if (allowMultiSelect) {
            return getValuesInternal();
        } else {
            throw new IllegalStateException("only valid in multi select mode");
        }
    }

    public void setValues(Set<String> values) {
        if (allowMultiSelect) {
            setValuesInternal(values);
        } else {
            throw new IllegalStateException("only valid in multi select mode");
        }
    }

    protected Set<String> getValuesInternal() {
        return currentValues;
    }

    protected void setValuesInternal(Set<String> values) {

        boolean changed = !CollectionUtils.equals(values, currentValues);
        if (changed) {
            HashSet<String> oldValues = new HashSet<>(currentValues);
            currentValues.clear();
            currentValues.addAll(values);

            persistStringSet(currentValues);
            notifyChanged();
            if (listener != null) {
                boolean oldSelectionNotDeleted = SetUtils.difference(oldValues, entries).size() == 0;
                listener.onItemSelectionChange(oldValues, currentValues, oldSelectionNotDeleted);
            }
        }
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        try {
            boolean canRestoreValue = shouldPersist() && getSharedPreferences().contains(getKey());
            if (isMultiSelectMode()) {
                setValues(canRestoreValue ? getPersistedStringSet(currentValues) : (Set<String>) defaultValue);
            } else {
                String currentValue = currentValues.size() > 0 ? currentValues.iterator().next() : null;
                setValue(canRestoreValue ? getPersistedString(currentValue) : (String) defaultValue);
            }
        } catch (ClassCastException e) {
            Logging.recordException(e);
            String msg = "Pref " + this.getKey() + " in " + (isMultiSelectMode() ? "multi" : "single") + "select mode initialised with value of wrong type (" + defaultValue + ")";
            Logging.log(Log.ERROR, TAG, msg);
            throw e;
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

    protected boolean isMultiSelectMode() {
        return allowMultiSelect;
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
            Logging.recordException(e);
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
        final String strValue;
        if (isMultiSelectMode()) {
            strValue = CollectionUtils.toCsvList(getValuesInternal());
        } else {
            strValue = getValue();
        }
        if (summary == null) {
            return super.getSummary();
        } else {
            return String.format(summary, strValue == null ? "" : strValue);
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return PreferenceUtils.getMultiTypeDefaultValue(this, a, index);
    }

    public boolean isAllowItemEdit() {
        return allowItemEdit;
    }

    public boolean isAlwaysSelectAll() {
        return alwaysSelectAll;
    }

    public String filterUserInput(String value) throws IllegalArgumentException {
        return listener.filterUserInput(value);
    }

    public void updateEntryValues(ArrayList<EditableListPreferenceDialogFragmentCompat.ListAction> actions) {

        for (EditableListPreferenceDialogFragmentCompat.ListAction action : actions) {
            if (action instanceof EditableListPreferenceDialogFragmentCompat.Removal) {
                if (listener != null) {
                    listener.onItemRemoved(action.entryValue);
                }
                entries.remove(action.entryValue);
            } else if (action instanceof EditableListPreferenceDialogFragmentCompat.Addition) {
                if (listener != null) {
                    listener.onItemAdded(action.entryValue);
                }
                entries.add(action.entryValue);
            } else if (action instanceof EditableListPreferenceDialogFragmentCompat.Replacement) {
                if (listener != null) {
                    listener.onItemAltered(this, action.entryValue, ((EditableListPreferenceDialogFragmentCompat.Replacement) action).newEntryValue);
                }
                entries.remove(action.entryValue);
                entries.add(((EditableListPreferenceDialogFragmentCompat.Replacement) action).newEntryValue);
            }
        }
        persistEntries(entries);
    }

    public void addAndSelectItems(Set<String> items) {
        loadEntries();
        ArrayList<EditableListPreferenceDialogFragmentCompat.ListAction> actions = new ArrayList<>(items.size());
        for (String item : items) {
            actions.add(new EditableListPreferenceDialogFragmentCompat.Addition(item));
        }
        updateEntryValues(actions);
        setValues(items); // use external version so it sanity checks the multi-select setting
    }

    public void addAndSelectItem(String item) {
        loadEntries();
        EditableListPreferenceDialogFragmentCompat.Addition action = new EditableListPreferenceDialogFragmentCompat.Addition(item);
        ArrayList<EditableListPreferenceDialogFragmentCompat.ListAction> actions = new ArrayList<>(1);
        actions.add(action);
        updateEntryValues(actions);
        setValue(item);
    }

    public Set<String> filterNewUserSelection(Set<String> userSelectedItems) {
        return listener.filterNewUserSelection(userSelectedItems);
    }

    public interface EditableListPreferenceChangeListener {
        void onItemAdded(String newItem);

        void onItemRemoved(String newItem);

        void onItemSelectionChange(Set<String> oldSelection, Set<String> newSelection, boolean oldSelectionExists);

//        void onItemSelectionChange(String oldSelection, String newSelection, boolean oldSelectionExists);

        void onItemAltered(EditableListPreference preference, String oldValue, String newValue);

        String filterUserInput(String value) throws IllegalArgumentException;

        Set<String> filterNewUserSelection(Set<String> userSelectedItems);
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
        private TreeSet<String> entries;
        private boolean entriesAltered;

        public SavedState(Parcel source) {
            super(source);
            value = source.readString();
            entries = ParcelUtils.readStringSet(source, new TreeSet<>());
            entriesAltered = ParcelUtils.readBool(source);
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(value);
            ParcelUtils.writeStringSet(dest, entries);
            ParcelUtils.writeBool(dest, entriesAltered);
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
        public void onItemSelectionChange(Set<String> oldSelection, Set<String> newSelection, boolean oldSelectionExists) {

        }

        @Override
        public void onItemAltered(EditableListPreference preference, String oldValue, String newValue) {
        }

        @Override
        public String filterUserInput(String value) throws IllegalArgumentException {
            return value;
        }

        @Override
        public Set<String> filterNewUserSelection(Set<String> userSelectedItems) {
            return userSelectedItems;
        }
    }


}
