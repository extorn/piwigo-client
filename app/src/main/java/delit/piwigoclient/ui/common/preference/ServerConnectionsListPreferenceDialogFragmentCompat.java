package delit.piwigoclient.ui.common.preference;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.preference.DialogPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDialogFragmentCompat;

import delit.libs.ui.util.DisplayUtils;
import delit.piwigoclient.ui.dialogs.SelectServerConnectionDetailsDialogHelper;

public class ServerConnectionsListPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat implements DialogPreference.TargetFragment {

    private String selectedValue;
    private static final String STATE_SELECTED_VALUE = "ServerConnectionsListPreference.SelectedValue";
    private SelectServerConnectionDetailsDialogHelper dialogHelper;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dialogHelper = new SelectServerConnectionDetailsDialogHelper(savedInstanceState);
        if(savedInstanceState == null) {
            ServerConnectionsListPreference pref = getPreference();
            selectedValue = pref.getValue();
        } else {
            selectedValue = savedInstanceState.getString(STATE_SELECTED_VALUE);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_SELECTED_VALUE, selectedValue);
        dialogHelper.onSaveInstanceState(outState);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        dialogHelper.loadListValues(view.getContext(), selectedValue);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (dialogHelper.getItemCount() == 1) {
            // ensure the value gets set.
            dialogHelper.selectAll();
            onClick(getDialog(), DialogInterface.BUTTON_POSITIVE);
            onDismiss(getDialog());
        }
    }

    @Override
    protected View onCreateDialogView(Context context) {
        return dialogHelper.createDialogView(context, null);
    }

    @Override
    public ServerConnectionsListPreference getPreference() {
        return (ServerConnectionsListPreference) super.getPreference();
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            dialogHelper.onClickOkayButton(new MyDialogListener());
        }
    }

    private class MyDialogListener implements SelectServerConnectionDetailsDialogHelper.DialogListener {
        @Override
        public void onSuccess(ServerConnectionsListPreference.ServerConnection selectedItem) {
            ServerConnectionsListPreference pref = getPreference();
            String selectedItemStr = selectedItem == null ? null : selectedItem.getProfileName();
            if (pref.callChangeListener(selectedItemStr)) {
                pref.persistStringValue(selectedItemStr);
            }
        }

        @Override
        public void onCancel() {
        }
    }

    @Override
    public <T extends Preference> T findPreference(@NonNull CharSequence key) {
        return (T)getPreference();
    }

    public static ServerConnectionsListPreferenceDialogFragmentCompat newInstance(String key) {
        final ServerConnectionsListPreferenceDialogFragmentCompat fragment =
                new ServerConnectionsListPreferenceDialogFragmentCompat();
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
