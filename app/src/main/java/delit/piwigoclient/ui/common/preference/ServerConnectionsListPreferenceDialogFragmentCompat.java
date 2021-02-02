package delit.piwigoclient.ui.common.preference;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.DialogPreference;
import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.preference.PreferenceManager;

import com.google.android.gms.ads.AdView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.button.MaterialCheckboxTriState;
import delit.libs.ui.view.list.MultiSourceListAdapter;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.ui.AdsManager;

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
        View view = LayoutInflater.from(context).inflate(R.layout.layout_fullsize_list, null, false);
        AdView adView = view.findViewById(R.id.list_adView);
        if (AdsManager.getInstance(getContext()).shouldShowAdverts()) {
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(View.GONE);
        }

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
        return PreferenceManager.getDefaultSharedPreferences(requireContext().getApplicationContext());
    }

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
        HashSet<Long> selectedIdx = new HashSet<>(1);
        int idxToSelect = 0;
        for (ServerConnectionsListPreference.ServerConnection c : serverConnections) {
            if (c.getProfileName().equals(currentSelection)) {
                selectedIdx.add((long) idxToSelect);
                break;
            }
            idxToSelect++;
        }


        ServerConnectionProfilesListAdapter.ServerConnectionProfilesListAdapterPreferences viewPrefs = new ServerConnectionProfilesListAdapter.ServerConnectionProfilesListAdapterPreferences();
        ServerConnectionProfilesListAdapter adapter = new ServerConnectionProfilesListAdapter(serverConnections, viewPrefs);
        adapter.linkToListView(listView, selectedIdx, selectedIdx);
    }

    private ArrayList<ServerConnectionsListPreference.ServerConnection> loadServerConnections(SharedPreferences prefs) {
        Set<String> profiles = ConnectionPreferences.getConnectionProfileList(prefs, requireContext());
        ArrayList<ServerConnectionsListPreference.ServerConnection> connections = new ArrayList<>();
        if (profiles.size() > 0) {
            for (String p : profiles) {
                if(profiles.size() == 1) {
                    ConnectionPreferences.clonePreferences(prefs, getContext(), null, p);
                }
                ConnectionPreferences.ProfilePreferences profilePrefs = ConnectionPreferences.getPreferences(p, prefs, requireContext());
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
    public androidx.preference.Preference findPreference(@NonNull CharSequence key) {
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

    private static class ServerConnectionProfilesListAdapter extends MultiSourceListAdapter<ServerConnectionsListPreference.ServerConnection, ServerConnectionProfilesListAdapter.ServerConnectionProfilesListAdapterPreferences> {

        public static class ServerConnectionProfilesListAdapterPreferences extends BaseRecyclerViewAdapterPreferences<ServerConnectionProfilesListAdapterPreferences> {
            public ServerConnectionProfilesListAdapterPreferences(){
                selectable(false, false);
            }

            public ServerConnectionProfilesListAdapterPreferences(Bundle bundle) {
                loadFromBundle(bundle);
            }

            @Override
            protected String getBundleName() {
                return "ServerConnectionProfilesListAdapterPreferences";
            }
        }

        public ServerConnectionProfilesListAdapter(ArrayList<ServerConnectionsListPreference.ServerConnection> availableItems, ServerConnectionProfilesListAdapterPreferences adapterPrefs) {
            super(availableItems, adapterPrefs);
        }

        @Override
        public long getItemId(ServerConnectionsListPreference.ServerConnection item) {
            return getPosition(item);
        }

        @Override
        protected int getItemViewLayoutRes() {
            return R.layout.layout_list_item_simple_checkable;
        }

        @Override
        protected void setViewContentForItemDisplay(Context context, View itemView, ServerConnectionsListPreference.ServerConnection item, int levelInTreeOfItem) {
            TextView nameView = itemView.findViewById(R.id.list_item_name);
            TextView detailView = itemView.findViewById(R.id.list_item_details);

            nameView.setText(item.getProfileName());
            detailView.setText(context.getString(R.string.server_connection_id_pattern, item.getUsername(), item.getServerName()));
        }

        @Override
        protected MaterialCheckboxTriState getAppCompatCheckboxTriState(View view) {
            return view.findViewById(R.id.list_item_checked);
        }

    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        DisplayUtils.hideKeyboardFrom(getContext(), dialog);
        super.onClick(dialog, which);
    }

}
