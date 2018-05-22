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
import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.common.util.Strings;

import java.util.ArrayList;
import java.util.Set;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.CustomImageButton;
import delit.piwigoclient.util.ObjectUtils;

import static android.content.Context.MODE_PRIVATE;

/**
 * Created by gareth on 15/07/17.
 */

public class ServerConnectionsListPreference extends DialogPreference {

    private boolean mValueSet;
    private RecyclerView itemListView;
    private CustomImageButton addListItemButton;
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
        String activeProfile = ConnectionPreferences.getActiveConnectionProfile(prefs, getContext());
        ConnectionPreferences.ProfilePreferences selectedPref = ConnectionPreferences.getPreferences(activeProfile);
        ServerConnection activeConnection = new ServerConnection(activeProfile,
                selectedPref.getPiwigoServerAddress(prefs, getContext()),
                selectedPref.getPiwigoUsername(prefs, getContext()));
        return activeConnection.getSummary(getContext());
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        mValue = super.getPersistedString("");
        View view = buildListView();
        builder.setView(view);
    }

    private View buildListView() {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.layout_fullsize_recycler_list, null, false);

        AdView adView = view.findViewById(R.id.list_adView);
        if(AdsManager.getInstance().shouldShowAdverts()) {
            adView.loadAd(new AdRequest.Builder().build());
            adView.setVisibility(View.VISIBLE);
        } else {
            adView.setVisibility(View.GONE);
        }

        view.findViewById(R.id.list_action_cancel_button).setVisibility(View.GONE);
        view.findViewById(R.id.list_action_toggle_all_button).setVisibility(View.GONE);

        TextView heading = view.findViewById(R.id.heading);
        heading.setText(R.string.certificates_heading);
        heading.setVisibility(View.VISIBLE);

        itemListView = view.findViewById(R.id.list);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getContext());
        itemListView.setLayoutManager(mLayoutManager);
        ListAdapter adapter = new ListAdapter();
        SharedPreferences prefs = getAppSharedPreferences();
        adapter.setData(loadServerConnections(prefs));
        String activeProfile = ConnectionPreferences.getActiveConnectionProfile(prefs, getContext());
        adapter.setSelection(adapter.getItemPosition(activeProfile));
        itemListView.setAdapter(adapter);

        addListItemButton = view.findViewById(R.id.list_action_add_item_button);
        addListItemButton.setVisibility(View.VISIBLE);
        addListItemButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSelectItemsToAddToList();
            }
        });

        Button saveChangesButton = view.findViewById(R.id.list_action_save_button);
        saveChangesButton.setVisibility(View.GONE);

        return view;
    }

    private SharedPreferences getAppSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
    }

    private ArrayList<ServerConnection> loadServerConnections(SharedPreferences prefs) {
        Set<String> profiles = ConnectionPreferences.getConnectionProfileList(prefs, getContext());
        ArrayList<ServerConnection> connections = new ArrayList<>();
        if(profiles != null) {
            for (String p : profiles) {
                ConnectionPreferences.ProfilePreferences profilePrefs = ConnectionPreferences.getPreferences(p);
                connections.add(new ServerConnection(p,
                        profilePrefs.getPiwigoServerAddress(prefs, getContext()),
                        profilePrefs.getPiwigoUsername(prefs, getContext())));
            }
        } else {
            connections.add(new ServerConnection("",
                    ConnectionPreferences.getPiwigoServerAddress(prefs, getContext()),
                    ConnectionPreferences.getPiwigoUsername(prefs, getContext())));
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
        if(itemListView.getAdapter().getItemCount() == 1) {
            // ensure the value gets set.
            onClick(getDialog(), DialogInterface.BUTTON_POSITIVE);
            getDialog().dismiss();
            return;
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if(positiveResult) {
            mValueSet = false; // force the value to be saved.
            ServerConnection selectedItem = ((ListAdapter)itemListView.getAdapter()).getSelectedItem();

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
            String user = username;
            if(user == null) {
                user = c.getString(R.string.server_connection_preference_user_guest);
            }
            return user + '@' + serverName;
        }
    }

    private class ServerConnectionViewHolder extends RecyclerView.ViewHolder {

        private final TextView detailView;
        private final TextView nameView;

        public ServerConnectionViewHolder(View itemView) {
            super(itemView);
            nameView = itemView.findViewById(R.id.name);
            detailView = itemView.findViewById(R.id.details);
        }

        public void populateFromItem(ServerConnection connection, boolean isSelected) {
            nameView.setText(connection.getProfileName());
            nameView.setSelected(isSelected);
            detailView.setText(connection.getUsername() + '@' + connection.getServerName());
            detailView.setSelected(isSelected);
        }

        protected class ViewClickListener implements View.OnClickListener {
            private final ServerConnectionViewHolder vh;

            public ViewClickListener(ServerConnectionViewHolder vh) {
                this.vh = vh;
            }

            @Override
            public void onClick(View v) {
                int adapterPos = getAdapterPosition();

                getDialog().dismiss();
            }
        }
    }

    private class ListAdapter extends RecyclerView.Adapter<ServerConnectionViewHolder> {

        private int selectedItemPosition;
        private ArrayList<ServerConnection> data;

        public ListAdapter() {
        }

        public ArrayList<ServerConnection> getBackingObjectStore() {
            return data;
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        public void setData(ArrayList<ServerConnection> data) {
            this.data = data;
            notifyDataSetChanged();
        }

        public void setSelection(int selectedItemPosition) {
            this.selectedItemPosition = selectedItemPosition;
        }

        @NonNull
        @Override
        public ServerConnectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.simple_list_item_layout, parent, false);
            return new ServerConnectionViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ServerConnectionViewHolder holder, int position) {
            holder.populateFromItem(getItem(position), selectedItemPosition == position);
        }

        public ServerConnection getItem(int position) {
            return data.get(position);
        }
//
//        @Override
//        public long getItemId(int position) {
//            return getItem(position).getProfileName().hashCode();
//        }

        public int getItemPosition(String activeProfile) {
            for (int i = 0; i < data.size(); i++) {
                if(data.get(0).getProfileName().equals(activeProfile)) {
                    return i;
                }
            }
            return -1;
        }

        public ServerConnection getSelectedItem() {
            if(selectedItemPosition < 0) {
                return null;
            }
            return data.get(selectedItemPosition);
        }
    }

}