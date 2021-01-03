package delit.libs.ui.view.preference;

import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.preference.DialogPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDialogFragmentCompat;

import java.io.Serializable;
import java.lang.reflect.Array;

import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.util.DisplayUtils;

public class MappedListPreferenceDialogFragmentCompat<T extends Serializable> extends PreferenceDialogFragmentCompat implements DialogPreference.TargetFragment {

    private static final String SAVE_STATE_INDEX = "MappedListPreference.index";
    private static final String STATE_ENTRIES = "MappedListPreference.entries";
    private static final String STATE_ENTRY_VALUES = "MappedListPreference.entryValues";
    private static final String STATE_ENTRY_VALUE_CLAZZ = "MappedListPreference.entryValueClazz";
    private int checkedItem;
    private T[] entryValues;
    private Class<T> entryValueClazz;
    private CharSequence[] entries;


    @Override
    public Preference findPreference(@NonNull CharSequence key) {
        return getPreference();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState != null) {
            checkedItem = savedInstanceState.getInt(SAVE_STATE_INDEX, 0);
            entryValueClazz = BundleUtils.getClass(savedInstanceState, STATE_ENTRY_VALUE_CLAZZ);
            entryValues = BundleUtils.getSerializable(savedInstanceState, STATE_ENTRY_VALUES, (Class<T[]>)Array.newInstance(entryValueClazz, 0).getClass());
            entries = savedInstanceState.getCharSequenceArray(STATE_ENTRIES);
        } else {
            MappedListPreference<T> pref = (MappedListPreference<T>) getPreference();
            checkedItem = pref.getValueIndex();
            entryValues = pref.getEntryValues();
            entries = pref.getEntries();
            entryValueClazz = pref.getEntriesClazz();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SAVE_STATE_INDEX, checkedItem);
        BundleUtils.putClass(outState, STATE_ENTRY_VALUE_CLAZZ, entryValueClazz);
        outState.putSerializable(STATE_ENTRY_VALUES, entryValues);
        outState.putCharSequenceArray(STATE_ENTRIES, entries);
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        if (entries == null ||  entryValues == null) {
            throw new IllegalStateException(
                    "ListPreference requires an entries array and an entryValues array.");
        }

        builder.setSingleChoiceItems(entries, checkedItem,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        checkedItem = which;
                        /*
                         * Clicking on an item simulates the positive button
                         * click, and dismisses the dialog.
                         */
                        MappedListPreferenceDialogFragmentCompat.this.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                        dialog.dismiss();
                    }
                });

        /*
         * The typical interaction for list-based dialogs is to have
         * click-on-an-item dismiss the dialog instead of the user having to
         * press 'Ok'.
         */
        builder.setPositiveButton(null, null);
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        MappedListPreference<T> pref = (MappedListPreference<T>) getPreference();
        if (positiveResult && checkedItem >= 0 && pref.getEntryValues() != null) {
            T value = pref.getEntryValues()[checkedItem];
            if (pref.callChangeListener(value)) {
                pref.setValue(value);
            }
        }
    }

    public static DialogFragment newInstance(String key) {
        final MappedListPreferenceDialogFragmentCompat fragment =
                new MappedListPreferenceDialogFragmentCompat();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        DisplayUtils.hideKeyboardFrom(getContext(), dialog);
        super.onClick(dialog, which);
    }
}
