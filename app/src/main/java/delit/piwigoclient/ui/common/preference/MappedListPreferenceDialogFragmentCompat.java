package delit.piwigoclient.ui.common.preference;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.DialogPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceDialogFragmentCompat;
import android.view.inputmethod.InputMethodManager;

public class MappedListPreferenceDialogFragmentCompat<T> extends PreferenceDialogFragmentCompat implements DialogPreference.TargetFragment {

    private int mClickedDialogEntryIndex;
    private String SAVE_STATE_INDEX = "MappedListPreferenceDialogFragment.index";

    @Override
    public Preference findPreference(CharSequence key) {
        return getPreference();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState != null) {
            mClickedDialogEntryIndex = savedInstanceState.getInt(SAVE_STATE_INDEX, 0);
        } else {
            MappedListPreference<T> pref = (MappedListPreference<T>) getPreference();
            mClickedDialogEntryIndex = pref.getValueIndex();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SAVE_STATE_INDEX, mClickedDialogEntryIndex);
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        MappedListPreference<T> pref = (MappedListPreference<T>) getPreference();
        if (pref.getEntries() == null || pref.getEntryValues() == null) {
            throw new IllegalStateException(
                    "ListPreference requires an entries array and an entryValues array.");
        }

        builder.setSingleChoiceItems(pref.getEntries(), pref.getValueIndex(),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        mClickedDialogEntryIndex = which;
                        /*
                         * Clicking on an item simulates the positive button
                         * click, and dismisses the dialog.
                         */
                        onClick(dialog, DialogInterface.BUTTON_POSITIVE);
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
        if (positiveResult && mClickedDialogEntryIndex >= 0 && pref.getEntryValues() != null) {
            T value = pref.getEntryValues()[mClickedDialogEntryIndex];
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
        hideKeyboardFrom(getContext(), ((AlertDialog)dialog).getCurrentFocus().getWindowToken());
        super.onClick(dialog, which);
    }

    public void hideKeyboardFrom(Context context, IBinder windowToken) {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(windowToken, 0);
    }
}
