package delit.piwigoclient.ui.preferences;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.DialogPreference;
import androidx.preference.PreferenceDialogFragmentCompat;

import com.google.android.gms.ads.AdView;
import com.google.android.gms.common.util.Strings;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.Objects;
import java.util.SortedSet;

import delit.libs.ui.view.button.MaterialCheckboxTriState;
import delit.libs.ui.view.list.MultiSourceListAdapter;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.events.trackable.AutoUploadJobViewRequestedEvent;
import delit.piwigoclient.ui.permissions.users.UserRecyclerViewAdapter;

public class AutoUploadJobsPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat implements DialogPreference.TargetFragment {
    private ListView itemListView;
    private AutoUploadJobsListAdapter adapter;
    private AutoUploadJobsListAdapterPreferences viewPrefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewPrefs = new AutoUploadJobsListAdapterPreferences(savedInstanceState);
    }

    @Override
    public AutoUploadJobsPreference findPreference(@NonNull CharSequence key) {
        return getPreference();
    }

    @Override
    public AutoUploadJobsPreference getPreference() {
        return (AutoUploadJobsPreference) super.getPreference();
    }

    public static AutoUploadJobsPreferenceDialogFragmentCompat newInstance(String key) {
        final AutoUploadJobsPreferenceDialogFragmentCompat fragment =
                new AutoUploadJobsPreferenceDialogFragmentCompat();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    private void onDialogNegativeClick() {
        getPreference().clearActiveState();
        // Clear changes from the active state.
    }

    private void onDialogPositiveClick() {
        if(getPreference().getActiveState().isJobConfigShowing()) {
            return;
        }
        getPreference().persistActiveState();
    }


    @Override
    public void onClick(DialogInterface dialog, int whichButton) {
        if(whichButton == DialogInterface.BUTTON_POSITIVE) {
            onDialogPositiveClick();
        } else if(whichButton == DialogInterface.BUTTON_NEGATIVE) {
            onDialogNegativeClick();
        }
        super.onClick(dialog, whichButton);
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        // do nothing - positive just means not negative or neutral (doesn't mean positive button!).
    }

    private void onDeleteUploadJob(AutoUploadJobConfig item) {
        getPreference().getActiveState().recordItemForDelete(item);
        // refresh the list.
        loadListValues(getPreference().getActiveState().getUploadJobIds());
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        builder.setNegativeButton(null, null); // remove the cancel button
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog d =  super.onCreateDialog(savedInstanceState);
        d.setCancelable(!getPreference().getActiveState().isJobsHaveChanged()); // force explicit cancel if the jobs list has altered
        return d;
    }

    @Override
    protected View onCreateDialogView(Context context) {
        return buildListView(context);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        loadListValues(getPreference().getActiveState().getUploadJobIds());
    }

    private View buildListView(Context context) {
        View view = LayoutInflater.from(context).inflate(R.layout.layout_fullsize_list, null, false);

        AdView adView = view.findViewById(R.id.list_adView);
        if(AdsManager.getInstance(getContext()).shouldShowAdverts()) {
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(View.GONE);
        }

        view.findViewById(R.id.list_action_cancel_button).setVisibility(View.GONE);
        view.findViewById(R.id.list_action_toggle_all_button).setVisibility(View.GONE);
        view.findViewById(R.id.list_action_save_button).setVisibility(View.GONE);

        TextView heading = view.findViewById(R.id.heading);
        heading.setText(R.string.preference_data_upload_automatic_upload_jobs_title);
        heading.setVisibility(View.VISIBLE);

        itemListView = view.findViewById(R.id.list);

        ExtendedFloatingActionButton addListItemButton = view.findViewById(R.id.list_action_add_item_button);
        addListItemButton.setOnClickListener(v -> onDisplayUploadJob(null));

        return view;
    }

    private void onDisplayUploadJob(AutoUploadJobConfig uploadJobConfig) {
        String activeJobText = null;
        if(uploadJobConfig !=null) {
            activeJobText = uploadJobConfig.getSummary(getPreference().getSharedPreferences(), getContext());
            if(activeJobText == null) {
                activeJobText = "";
            }
        }
        getPreference().getActiveState().setActiveUploadJobConfigSummaryText(activeJobText);
        int jobId;
        if(uploadJobConfig == null) {
            jobId = getPreference().getActiveState().getNextJobId();
        } else {
            jobId = uploadJobConfig.getJobId();
        }
        getPreference().getActiveState().postTrackableEvent(new AutoUploadJobViewRequestedEvent(jobId));
        Objects.requireNonNull(getDialog()).dismiss();
    }

    private void showDialog() {
        Dialog d = getDialog();
        if(d != null) {
            d.show();
        }
    }


    private void loadListValues(SortedSet<Long> uploadJobIds) {

        ArrayList<AutoUploadJobConfig> uploadJobConfigs = new ArrayList<>(uploadJobIds.size());
        for(Long jobId : uploadJobIds) {
            uploadJobConfigs.add(new AutoUploadJobConfig((int) Math.rint(jobId)));
        }

        adapter = new AutoUploadJobsListAdapter(uploadJobConfigs, viewPrefs);

        if(itemListView != null) {
            adapter.linkToListView(itemListView, null, null);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if(adapter != null) {
            adapter.getAdapterPrefs().storeToBundle(outState);
        }
    }

    protected static class AutoUploadJobsListAdapterPreferences extends BaseRecyclerViewAdapterPreferences<AutoUploadJobsListAdapterPreferences>{

        public AutoUploadJobsListAdapterPreferences(){}

        public AutoUploadJobsListAdapterPreferences(Bundle bundle) {
            loadFromBundle(bundle);
        }

        @Override
        protected String getBundleName() {
            return "AutoUploadJobsListAdapterPreferences";
        }
    }

    private class AutoUploadJobsListAdapter extends MultiSourceListAdapter<AutoUploadJobConfig, AutoUploadJobsListAdapterPreferences> {

        public AutoUploadJobsListAdapter(ArrayList<AutoUploadJobConfig> availableItems, AutoUploadJobsListAdapterPreferences adapterPrefs) {
            super(availableItems, adapterPrefs);
        }

        @Override
        public long getItemId(AutoUploadJobConfig item) {
            return item.getJobId();
        }

        @Override
        protected int getItemViewLayoutRes() {
            return R.layout.layout_list_item_upload_jobs_job_summary;
        }

        public String getUploadFromSummary(@NonNull Context context, @NonNull AutoUploadJobConfig item) {
            return item.getUploadFromFolderName(context);
        }

        public String getUploadToSummary(@NonNull Context context, AutoUploadJobConfig item) {

            ConnectionPreferences.ProfilePreferences connPrefs = item.getConnectionPrefs(context, getPreference().getSharedPreferences());
            String serverName = connPrefs.getPiwigoServerAddress(getPreference().getSharedPreferences(), context);
            String username = connPrefs.getPiwigoUsername(getPreference().getSharedPreferences(), context);
            String uploadFolder = item.getUploadToAlbumName(context);

            String user = Strings.emptyToNull(username);
            if(user == null) {
                user = requireContext().getString(R.string.server_connection_preference_user_guest);
            }
            return user + '@' + serverName + '(' + uploadFolder + ')';
        }

        @Override
        protected void setViewContentForItemDisplay(Context context, View itemView, final AutoUploadJobConfig item, int levelInTreeOfItem) {
            itemView.setOnClickListener(v -> onDisplayUploadJob(item));
            TextView nameView = itemView.findViewById(R.id.list_item_name);
            TextView detailView = itemView.findViewById(R.id.list_item_details);
            MaterialCheckboxTriState deleteUploadedFiles = itemView.findViewById(R.id.delete_uploaded);
            MaterialCheckboxTriState jobEnabledView = itemView.findViewById(R.id.enabled);
            MaterialButton deleteButton = itemView.findViewById(R.id.list_item_delete_button);
            deleteButton.setOnClickListener(v -> onDeleteUploadJob(item));

            nameView.setText(getUploadFromSummary(context, item));
            detailView.setText(getUploadToSummary(context, item));
            deleteUploadedFiles.setChecked(item.isJobEnabled(getContext()) && item.isDeleteFilesAfterUpload(getContext()));
            jobEnabledView.setChecked(item.isJobEnabled(getContext()) && item.isJobValid(getContext()));
        }

        @Override
        protected MaterialCheckboxTriState getAppCompatCheckboxTriState(View view) {
            return view.findViewById(R.id.list_item_checked);
        }
    }

    @Nullable
    @Override
    public Context getContext() {
        Context c = super.getContext();
        if(c == null) {
            c = getPreference().getContext();
        }
        return c;
    }
}
