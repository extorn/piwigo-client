package delit.piwigoclient.ui.common.preference;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.DialogPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceDialogFragmentCompat;

import java.io.Serializable;

import delit.piwigoclient.util.DisplayUtils;

public class MappedListPreferenceDialogFragmentCompat<T extends Serializable> extends PreferenceDialogFragmentCompat implements DialogPreference.TargetFragment {

    private static final String SAVE_STATE_INDEX = "MappedListPreference.index";
    private static final String STATE_ENTRIES = "MappedListPreference.entries";
    private static final String STATE_ENTRY_VALUES = "MappedListPreference.entryValues";
    private int checkedItem;
    private T[] entryValues;
    private CharSequence[] entries;


    @Override
    public Preference findPreference(CharSequence key) {
        return getPreference();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState != null) {
            checkedItem = savedInstanceState.getInt(SAVE_STATE_INDEX, 0);
            entries = savedInstanceState.getCharSequenceArray(STATE_ENTRIES);
            entryValues = (T[]) savedInstanceState.getSerializable(STATE_ENTRY_VALUES);
        } else {
            MappedListPreference<T> pref = (MappedListPreference<T>) getPreference();
            checkedItem = pref.getValueIndex();
            entryValues = pref.getEntryValues();
            entries = pref.getEntries();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SAVE_STATE_INDEX, checkedItem);
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
