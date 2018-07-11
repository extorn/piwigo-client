package delit.piwigoclient.ui.preferences;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.ads.AdView;
import com.google.android.gms.common.util.Strings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.button.AppCompatCheckboxTriState;
import delit.piwigoclient.ui.common.button.CustomImageButton;
import delit.piwigoclient.ui.common.list.MultiSourceListAdapter;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.util.ObjectUtils;

/**
 * Created by gareth on 15/07/17.
 */

public class ServerConnectionsListPreference extends DialogPreference {

    private boolean mValueSet;
    private ListView itemListView;
    private String mValue;

    public ServerConnectionsListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ServerConnectionsListPreference(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.dialogPreferenceStyle);
    }
    
    /**
     * Returns the value of the key.
     *
     * @return The value of the key.
     */
    public String getValue() {
        return mValue;
    }

    /**
     * Sets the value of the key.
     *
     * @param value The value to set for the key.
     */
    public void setValue(String value) {
        // Always persist/notify the first time.
        boolean changed = !ObjectUtils.areEqual(this.mValue, value);
        if (!mValueSet || changed) {
            this.mValue = value;
            mValueSet = true;
            persistString(mValue);
            if (changed) {
                notifyChanged();
            }
        }
    }

    @Override
    public CharSequence getSummary() {
        SharedPreferences prefs = getAppSharedPreferences();
//        String activeProfile = ConnectionPreferences.getActiveConnectionProfile(prefs, getContext());
        ServerConnection activeConnection;
        if(mValue != null) {
            ConnectionPreferences.ProfilePreferences selectedPref = ConnectionPreferences.getPreferences(mValue);
            activeConnection = new ServerConnection(mValue,
                    selectedPref.getPiwigoServerAddress(prefs, getContext()),
                    selectedPref.getPiwigoUsername(prefs, getContext()));
        } else {
            activeConnection = new ServerConnection(mValue, null,null);
        }
        return activeConnection.getSummary(getContext());
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        mValue = super.getPersistedString(null);
        View view = buildListView();
        builder.setView(view);
    }

    private View buildListView() {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.layout_fullsize_list, null, false);

        AdView adView = view.findViewById(R.id.list_adView);
        if(AdsManager.getInstance().shouldShowAdverts()) {
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(View.GONE);
        }

        view.findViewById(R.id.list_action_cancel_button).setVisibility(View.GONE);
        view.findViewById(R.id.list_action_toggle_all_button).setVisibility(View.GONE);

        TextView heading = view.findViewById(R.id.heading);
        heading.setText(R.string.piwigo_connection_profile_heading);
        heading.setVisibility(View.VISIBLE);

        itemListView = view.findViewById(R.id.list);

        view.findViewById(R.id.list_action_cancel_button).setVisibility(View.GONE);
        view.findViewById(R.id.list_action_toggle_all_button).setVisibility(View.GONE);
        view.findViewById(R.id.list_action_add_item_button).setVisibility(View.GONE);
        view.findViewById(R.id.list_action_save_button).setVisibility(View.GONE);

        return view;
    }

    private SharedPreferences getAppSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
    }

    private ArrayList<ServerConnection> loadServerConnections(SharedPreferences prefs) {
        Set<String> profiles = ConnectionPreferences.getConnectionProfileList(prefs, getContext());
        ArrayList<ServerConnection> connections = new ArrayList<>();
        if(profiles.size() > 0) {
            for (String p : profiles) {
                ConnectionPreferences.ProfilePreferences profilePrefs = ConnectionPreferences.getPreferences(p);
                connections.add(new ServerConnection(p,
                        profilePrefs.getPiwigoServerAddress(prefs, getContext()),
                        profilePrefs.getPiwigoUsername(prefs, getContext())));
            }
        } else {
            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
            connections.add(new ServerConnection("",
                    connectionPrefs.getPiwigoServerAddress(prefs, getContext()),
                    connectionPrefs.getPiwigoUsername(prefs, getContext())));
        }
        return connections;
    }

    /**
     * Override to handle the add new item action
     */
    protected void onSelectItemsToAddToList() {
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        loadListValues();
        if(itemListView.getAdapter().getCount() == 1) {
            // ensure the value gets set.
            ((ServerConnectionProfilesListAdapter)itemListView.getAdapter()).selectAllItemIds();
            onClick(getDialog(), DialogInterface.BUTTON_POSITIVE);
            getDialog().dismiss();
            return;
        }
    }

    private void loadListValues() {

        ArrayList<ServerConnection> serverConnections = loadServerConnections(getAppSharedPreferences());
//        String activeProfile = ConnectionPreferences.getActiveConnectionProfile(getSharedPreferences(), getContext());
        HashSet<Long> selectedIdx = new HashSet<>(1);
        int idxToSelect = 0;
        for(ServerConnection c : serverConnections) {
            if(c.getProfileName().equals(mValue)) {
                selectedIdx.add(Long.valueOf(idxToSelect));
                break;
            }
            idxToSelect++;
        }


        BaseRecyclerViewAdapterPreferences viewPrefs = new BaseRecyclerViewAdapterPreferences();
        viewPrefs.selectable(false, false);
        ServerConnectionProfilesListAdapter adapter = new ServerConnectionProfilesListAdapter(getContext(), serverConnections, viewPrefs);
        adapter.linkToListView(itemListView, selectedIdx, selectedIdx);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if(positiveResult) {
            mValueSet = false; // force the value to be saved.
            ServerConnection selectedItem = ((ServerConnectionProfilesListAdapter)itemListView.getAdapter()).getSelectedItems().iterator().next();

            if (callChangeListener(selectedItem==null?null: selectedItem.profileName)) {
                setValue(selectedItem==null?null:selectedItem.profileName);
            }
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setValue(restoreValue ? getPersistedString("") : (String) defaultValue);
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
    }

    public static class SavedState extends BaseSavedState {

        public static final Creator<SavedState> CREATOR =
                new Creator<ServerConnectionsListPreference.SavedState>() {
                    public ServerConnectionsListPreference.SavedState createFromParcel(Parcel in) {
                        return new ServerConnectionsListPreference.SavedState(in);
                    }

                    public ServerConnectionsListPreference.SavedState[] newArray(int size) {
                        return new ServerConnectionsListPreference.SavedState[size];
                    }
                };

        private String value;

        public SavedState(Parcel source) {
            super(source);
            value = source.readString();
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(value);
        }
    }

    private class ServerConnection {

        private String serverName;
        private String username;
        private String profileName;

        public ServerConnection(String profileName, String piwigoServerAddress, String piwigoUsername) {
            this.profileName = profileName;
            this.serverName = piwigoServerAddress;
            this.username = piwigoUsername;
        }

        public String  getServerName() {
            return serverName;
        }

        public String  getUsername() {
            return username;
        }

        public String getProfileName() {
            return profileName;
        }

        public String getSummary(Context c) {
            if(serverName == null) {
                return c.getString(R.string.server_connection_preference_summary_default);
            }
            String user = Strings.emptyToNull(username);
            if(user == null) {
                user = c.getString(R.string.server_connection_preference_user_guest);
            }
            return user + '@' + serverName;
        }
    }

    private class ServerConnectionProfilesListAdapter extends MultiSourceListAdapter<ServerConnection, BaseRecyclerViewAdapterPreferences> {

        public ServerConnectionProfilesListAdapter(Context context, ArrayList<ServerConnection> availableItems, BaseRecyclerViewAdapterPreferences adapterPrefs) {
            super(context, availableItems, adapterPrefs);
        }

        @Override
        public long getItemId(ServerConnection item) {
            return getPosition(item);
        }

        @Override
        protected int getItemViewLayoutRes() {
            return R.layout.simple_list_item_checkable_layout;
        }

        @Override
        protected void setViewContentForItemDisplay(View itemView, ServerConnection item, int levelInTreeOfItem) {
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

}