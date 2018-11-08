package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.preference.DialogPreference;
import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.ads.AdView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.button.AppCompatCheckboxTriState;
import delit.piwigoclient.ui.common.list.MultiSourceListAdapter;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.util.DisplayUtils;

public class ServerConnectionsListPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat implements DialogPreference.TargetFragment {

    private ListView listView;
    private String selectedValue;
    private String STATE_SELECTED_VALUE = "ServerConnectionsListPreference.SelectedValue";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        loadListValues(listView, selectedValue);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView.getAdapter().getCount() == 1) {
            // ensure the value gets set.
            ((ServerConnectionProfilesListAdapter) listView.getAdapter()).selectAllItemIds();
            onClick(getDialog(), DialogInterface.BUTTON_POSITIVE);
            onDismiss(getDialog());
        }
    }

    @Override
    protected View onCreateDialogView(Context context) {
        return buildDialogView();
    }

    private View buildDialogView() {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.layout_fullsize_list, null, false);

        AdView adView = view.findViewById(R.id.list_adView);
        if (AdsManager.getInstance().shouldShowAdverts()) {
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(View.GONE);
        }

        view.findViewById(R.id.list_action_cancel_button).setVisibility(View.GONE);
        view.findViewById(R.id.list_action_toggle_all_button).setVisibility(View.GONE);

        TextView heading = view.findViewById(R.id.heading);
        heading.setText(R.string.piwigo_connection_profile_heading);
        heading.setVisibility(View.VISIBLE);

        listView = view.findViewById(R.id.list);

        view.findViewById(R.id.list_action_cancel_button).setVisibility(View.GONE);
        view.findViewById(R.id.list_action_toggle_all_button).setVisibility(View.GONE);
        view.findViewById(R.id.list_action_add_item_button).setVisibility(View.GONE);
        view.findViewById(R.id.list_action_save_button).setVisibility(View.GONE);

        return view;
    }

    @Override
    public ServerConnectionsListPreference getPreference() {
        return (ServerConnectionsListPreference) super.getPreference();
    }

    private SharedPreferences getAppSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
    }

//    @Override
//    public void onDialogClosed(boolean positiveResult) {
//        EditableListPreference pref = getPreference();
//        if (positiveResult && userSelectedItem != null && pref.getEntryValues() != null) {
//            String value = userSelectedItem;
//            if (pref.callChangeListener(value)) {
//                pref.setValue(value);
//            }
//        }
//    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            ServerConnectionsListPreference.ServerConnection selectedItem = ((ServerConnectionProfilesListAdapter) listView.getAdapter()).getSelectedItems().iterator().next();

            ServerConnectionsListPreference pref = getPreference();
            String selectedItemStr = selectedItem == null ? null : selectedItem.getProfileName();
            if (pref.callChangeListener(selectedItemStr)) {
                pref.persistStringValue(selectedItemStr);
            }
        }
    }

    private void loadListValues(ListView listView, String currentSelection) {

        ArrayList<ServerConnectionsListPreference.ServerConnection> serverConnections = loadServerConnections(getAppSharedPreferences());
//        String activeProfile = ConnectionPreferences.getActiveConnectionProfile(getSharedPreferences(), getContext());
        HashSet<Long> selectedIdx = new HashSet<>(1);
        int idxToSelect = 0;
        for (ServerConnectionsListPreference.ServerConnection c : serverConnections) {
            if (c.getProfileName().equals(currentSelection)) {
                selectedIdx.add((long) idxToSelect);
                break;
            }
            idxToSelect++;
        }


        BaseRecyclerViewAdapterPreferences viewPrefs = new BaseRecyclerViewAdapterPreferences();
        viewPrefs.selectable(false, false);
        ServerConnectionProfilesListAdapter adapter = new ServerConnectionProfilesListAdapter(getContext(), serverConnections, viewPrefs);
        adapter.linkToListView(listView, selectedIdx, selectedIdx);
    }

    private ArrayList<ServerConnectionsListPreference.ServerConnection> loadServerConnections(SharedPreferences prefs) {
        Set<String> profiles = ConnectionPreferences.getConnectionProfileList(prefs, getContext());
        ArrayList<ServerConnectionsListPreference.ServerConnection> connections = new ArrayList<>();
        if (profiles.size() > 0) {
            for (String p : profiles) {
                ConnectionPreferences.ProfilePreferences profilePrefs = ConnectionPreferences.getPreferences(p);
                connections.add(new ServerConnectionsListPreference.ServerConnection(p,
                        profilePrefs.getPiwigoServerAddress(prefs, getContext()),
                        profilePrefs.getPiwigoUsername(prefs, getContext())));
            }
        } else {
            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
            connections.add(new ServerConnectionsListPreference.ServerConnection("",
                    connectionPrefs.getPiwigoServerAddress(prefs, getContext()),
                    connectionPrefs.getPiwigoUsername(prefs, getContext())));
        }
        return connections;
    }

    @Override
    public androidx.preference.Preference findPreference(CharSequence key) {
        return getPreference();
    }

    public static ServerConnectionsListPreferenceDialogFragmentCompat newInstance(String key) {
        final ServerConnectionsListPreferenceDialogFragmentCompat fragment =
                new ServerConnectionsListPreferenceDialogFragmentCompat();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    private class ServerConnectionProfilesListAdapter extends MultiSourceListAdapter<ServerConnectionsListPreference.ServerConnection, BaseRecyclerViewAdapterPreferences> {

        public ServerConnectionProfilesListAdapter(Context context, ArrayList<ServerConnectionsListPreference.ServerConnection> availableItems, BaseRecyclerViewAdapterPreferences adapterPrefs) {
            super(context, availableItems, adapterPrefs);
        }

        @Override
        public long getItemId(ServerConnectionsListPreference.ServerConnection item) {
            return getPosition(item);
        }

        @Override
        protected int getItemViewLayoutRes() {
            return R.layout.layout_simple_list_item_checkable;
        }

        @Override
        protected void setViewContentForItemDisplay(View itemView, ServerConnectionsListPreference.ServerConnection item, int levelInTreeOfItem) {
            TextView nameView = itemView.findViewById(R.id.name);
            TextView detailView = itemView.findViewById(R.id.details);

            nameView.setText(item.getProfileName());
            detailView.setText(item.getUsername() + '@' + item.getServerName());
        }

        @Override
        protected AppCompatCheckboxTriState getAppCompatCheckboxTriState(View view) {
            return view.findViewById(R.id.checked);
        }

    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        DisplayUtils.hideKeyboardFrom(getContext(), dialog);
        super.onClick(dialog, which);
    }

}
