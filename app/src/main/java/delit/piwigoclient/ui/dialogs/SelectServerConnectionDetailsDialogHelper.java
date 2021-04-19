package delit.piwigoclient.ui.dialogs;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.google.android.gms.ads.AdView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.ui.view.button.MaterialCheckboxTriState;
import delit.libs.ui.view.list.MultiSourceListAdapter;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.preference.ServerConnectionsListPreference;

public class SelectServerConnectionDetailsDialogHelper {

    private static final String TAG = "SelectServerConnectionDetailsDialog";
    private final ServerConnectionProfilesListAdapter.ServerConnectionProfilesListAdapterPreferences viewPrefs;
    private ListView listView;
    private ServerConnectionProfilesListAdapter adapter;
    private @StringRes int headingDescMessageId;

    public SelectServerConnectionDetailsDialogHelper(@Nullable Bundle savedInstanceState) {
        viewPrefs = new ServerConnectionProfilesListAdapter.ServerConnectionProfilesListAdapterPreferences(savedInstanceState);
    }

    public void selectAll() {
        ((ServerConnectionProfilesListAdapter) listView.getAdapter()).selectAllItemIds();
    }

    public HashSet<ServerConnectionsListPreference.ServerConnection> getSelectedItems() {
        return ((ServerConnectionProfilesListAdapter) listView.getAdapter()).getSelectedItems();
    }

    public void onSaveInstanceState(Bundle outState) {
        if(adapter != null) {
            adapter.getAdapterPrefs().storeToBundle(outState);
        }
    }

    public int getItemCount() {
        return listView.getAdapter().getCount();
    }

    public void withMessage(@StringRes int message) {
        headingDescMessageId = message;
    }

    public interface DialogListener {
        void onSuccess(ServerConnectionsListPreference.ServerConnection selectedItem);
        void onCancel();
    }

    public AlertDialog buildDialog(@NonNull Context context, @NonNull DialogListener listener, @Nullable String selectedItem) {
        MaterialAlertDialogBuilder builder1 = new MaterialAlertDialogBuilder(new android.view.ContextThemeWrapper(context, R.style.Theme_App_EditPages));
        builder1.setTitle(R.string.alert_question_title);
        builder1.setView(createDialogView(builder1.getContext(), null));
        builder1.setNegativeButton(R.string.button_cancel,  (dialog, which) -> listener.onCancel());
        builder1.setPositiveButton(R.string.button_ok, (dialog, which) -> onClickOkayButton(listener));
        builder1.setCancelable(true);
        AlertDialog dialog = builder1.create();
        loadListValues(builder1.getContext(), selectedItem);
        return dialog;
    }

    public void onClickOkayButton(@NonNull DialogListener listener) {
        HashSet<ServerConnectionsListPreference.ServerConnection> selectedItems = getSelectedItems();
        if(selectedItems == null || selectedItems.isEmpty()) {
            Logging.log(Log.WARN,TAG, "OK selected, but no item selected");
            return;
        }
        ServerConnectionsListPreference.ServerConnection selectedItem = selectedItems.iterator().next();
        listener.onSuccess(selectedItem);
    }

    public void loadListValues(@NonNull Context context, String currentSelection) {

        ArrayList<ServerConnectionsListPreference.ServerConnection> serverConnections = loadServerConnections(context);
        HashSet<Long> selectedIdx = new HashSet<>(1);
        int idxToSelect = 0;
        for (ServerConnectionsListPreference.ServerConnection c : serverConnections) {
            if (c.getProfileName().equals(currentSelection)) {
                selectedIdx.add((long) idxToSelect);
                break;
            }
            idxToSelect++;
        }

        adapter = new ServerConnectionProfilesListAdapter(serverConnections, viewPrefs);
        adapter.linkToListView(listView, selectedIdx, selectedIdx);
    }

    private ArrayList<ServerConnectionsListPreference.ServerConnection> loadServerConnections(@NonNull Context context) {
        SharedPreferences prefs = getAppSharedPreferences(context);
        Set<String> profiles = ConnectionPreferences.getConnectionProfileList(prefs, context);
        ArrayList<ServerConnectionsListPreference.ServerConnection> connections = new ArrayList<>();
        if (profiles.size() > 0) {
            for (String p : profiles) {
                if(profiles.size() == 1) {
                    ConnectionPreferences.clonePreferences(prefs, context, null, p);
                }
                ConnectionPreferences.ProfilePreferences profilePrefs = ConnectionPreferences.getPreferences(p, prefs, context);
                connections.add(new ServerConnectionsListPreference.ServerConnection(p,
                        profilePrefs.getPiwigoServerAddress(prefs, context),
                        profilePrefs.getPiwigoUsername(prefs, context)));
            }
        } else {
            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
            connections.add(new ServerConnectionsListPreference.ServerConnection("",
                    connectionPrefs.getPiwigoServerAddress(prefs, context),
                    connectionPrefs.getPiwigoUsername(prefs, context)));
        }
        return connections;
    }

    public View createDialogView(@NonNull Context context, @Nullable ViewGroup rootView) {
        View view = LayoutInflater.from(context).inflate(R.layout.layout_fullsize_list, rootView, false);
        AdView adView = view.findViewById(R.id.list_adView);
        if (AdsManager.getInstance(context).shouldShowAdverts()) {
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(View.GONE);
        }

        TextView heading = view.findViewById(R.id.heading);
        heading.setText(R.string.piwigo_connection_profile_heading);
        heading.setVisibility(View.VISIBLE);

        if(headingDescMessageId != 0) {
            TextView headingDescription = view.findViewById(R.id.heading_description);
            headingDescription.setText(headingDescMessageId);
            headingDescription.setVisibility(View.VISIBLE);
        }

        listView = view.findViewById(R.id.list);

        view.findViewById(R.id.list_action_cancel_button).setVisibility(View.GONE);
        view.findViewById(R.id.list_action_toggle_all_button).setVisibility(View.GONE);
        view.findViewById(R.id.list_action_add_item_button).setVisibility(View.GONE);
        view.findViewById(R.id.list_action_save_button).setVisibility(View.GONE);

        return view;
    }

    private SharedPreferences getAppSharedPreferences(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    private static class ServerConnectionProfilesListAdapter extends MultiSourceListAdapter<ServerConnectionsListPreference.ServerConnection, ServerConnectionProfilesListAdapter.ServerConnectionProfilesListAdapterPreferences> {

        public static class ServerConnectionProfilesListAdapterPreferences extends BaseRecyclerViewAdapterPreferences<ServerConnectionProfilesListAdapterPreferences> {
            public ServerConnectionProfilesListAdapterPreferences(){
                selectable(false, false);
            }

            public ServerConnectionProfilesListAdapterPreferences(Bundle bundle) {
                if(bundle != null) {
                    loadFromBundle(bundle);
                } else {
                    selectable(false, false);
                }
            }

            @Override
            protected String getBundleName() {
                return "ServerConnectionProfilesListAdapterPreferences";
            }
        }

        public ServerConnectionProfilesListAdapter(ArrayList<ServerConnectionsListPreference.ServerConnection> availableItems, ServerConnectionProfilesListAdapterPreferences adapterPrefs) {
            super(availableItems, adapterPrefs);
        }

        @NonNull
        @Override
        public String toString() {
            return "ServerConProfilesAdapter:"+super.toString();
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
}
