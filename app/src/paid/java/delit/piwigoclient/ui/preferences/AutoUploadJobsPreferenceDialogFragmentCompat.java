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
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.DialogPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDialogFragmentCompat;

import com.google.android.gms.ads.AdView;
import com.google.android.gms.common.util.Strings;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import delit.libs.ui.view.button.AppCompatCheckboxTriState;
import delit.libs.ui.view.button.CustomImageButton;
import delit.libs.ui.view.list.MultiSourceListAdapter;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;
import delit.libs.util.CollectionUtils;
import delit.libs.util.ObjectUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.events.trackable.AutoUploadJobViewCompleteEvent;
import delit.piwigoclient.ui.events.trackable.AutoUploadJobViewRequestedEvent;
import delit.piwigoclient.ui.events.trackable.TrackableEventManager;

public class AutoUploadJobsPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat implements DialogPreference.TargetFragment {
    private final static String STATE_DELETED_JOBS = "AutoUploadJobsPreference.deletedJobs";
    private final static String STATE_TRACKABLE_EVENT_MANAGER = "AutoUploadJobsPreference.trackableEventManagerState";
    private final static String STATE_JOB_IDS = "AutoUploadJobsPreference.JobIds";
    private final static String STATE_ACTIVE_JOB_CONFIG_SUMMARY = "AutoUploadJobsPreference.activeUploadJobConfigSummary";
    private final static String STATE_JOBS_HAVE_CHANGED = "AutoUploadJobsPreference.jobsHaveChanged";
    private ListView itemListView;
    private AutoUploadJobsListAdapter adapter;
    private ArrayList<AutoUploadJobConfig> deletedItems = new ArrayList<>();
    private TrackableEventManager trackableEventManager = new TrackableEventManager();
    private ArrayList<Integer> uploadJobIds;
    private String activeUploadJobConfigSummary;
    private boolean jobsHaveChanged;


    @Override
    public Preference findPreference(CharSequence key) {
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

    @Override
    public void onResume() {
        super.onResume();
        if(!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        EventBus.getDefault().unregister(this);
        super.onDismiss(dialog);
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        AutoUploadJobsPreference pref = getPreference();
        if(positiveResult) {

            String val = CollectionUtils.toCsvList(uploadJobIds);

            if (pref.callChangeListener(val)) {
                pref.setValue(val, jobsHaveChanged);
                for(AutoUploadJobConfig deletedJob : deletedItems) {
                    deletedJob.deletePreferences(getContext());
                }
                deletedItems.clear();
            }
        }
    }

    private void onDeleteUploadJob(AutoUploadJobConfig item) {

        deletedItems.add(item);
        uploadJobIds.remove((Integer) item.getJobId());
        loadListValues(uploadJobIds);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog d =  super.onCreateDialog(savedInstanceState);
        d.setCancelable(!jobsHaveChanged); // force explicit cancel if the jobs list has altered
        return d;
    }

    @Override
    protected View onCreateDialogView(Context context) {
        return buildListView();
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        loadListValues(uploadJobIds);
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
        view.findViewById(R.id.list_action_save_button).setVisibility(View.GONE);

        TextView heading = view.findViewById(R.id.heading);
        heading.setText(R.string.preference_data_upload_automatic_upload_jobs_title);
        heading.setVisibility(View.VISIBLE);

        itemListView = view.findViewById(R.id.list);

        View addListItemButton = view.findViewById(R.id.list_action_add_item_button);
        addListItemButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDisplayUploadJob(null);
            }
        });

        return view;
    }

    private void onDisplayUploadJob(AutoUploadJobConfig uploadJobConfig) {
        activeUploadJobConfigSummary = uploadJobConfig==null?null:uploadJobConfig.getSummary(getPreference().getSharedPreferences(), getContext());
        int jobId;
        if(uploadJobConfig == null) {
            jobId = getNextJobId();
        } else {
            jobId = uploadJobConfig.getJobId();
        }
        trackableEventManager.postTrackedEvent(new AutoUploadJobViewRequestedEvent(jobId));
        Dialog d = getDialog();
        if(d != null) {
            d.dismiss();
        }
    }

    private int getNextJobId() {
        if(itemListView.getCount() == 0) {
            return 0;
        }
        ArrayList<Long> jobIds = adapter.getItemIds();
        long nextJobId = 0;
        while(jobIds.contains(nextJobId)) {
            nextJobId += 1;
        }
        return (int) nextJobId;
    }

    private void loadListValues(List<Integer> uploadJobIds) {

        ArrayList<AutoUploadJobConfig> uploadJobConfigs = new ArrayList<>(uploadJobIds.size());
        for(Integer jobId : uploadJobIds) {
            uploadJobConfigs.add(new AutoUploadJobConfig(jobId));
        }

        BaseRecyclerViewAdapterPreferences viewPrefs = new BaseRecyclerViewAdapterPreferences();
        adapter = new AutoUploadJobsListAdapter(getContext(), uploadJobConfigs, viewPrefs);

        if(itemListView != null) {
            adapter.linkToListView(itemListView, null, null);
        }
    }

    private class AutoUploadJobsListAdapter extends MultiSourceListAdapter<AutoUploadJobConfig, BaseRecyclerViewAdapterPreferences> {

        public AutoUploadJobsListAdapter(Context context, ArrayList<AutoUploadJobConfig> availableItems, BaseRecyclerViewAdapterPreferences adapterPrefs) {
            super(context, availableItems, adapterPrefs);
        }

        @Override
        public long getItemId(AutoUploadJobConfig item) {
            return item.getJobId();
        }

        @Override
        protected int getItemViewLayoutRes() {
            return R.layout.upload_jobs_list_item_checkable_layout;
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
                user = getContext().getString(R.string.server_connection_preference_user_guest);
            }
            return user + '@' + serverName + '(' + uploadFolder + ')';
        }

        @Override
        protected void setViewContentForItemDisplay(Context context, View itemView, final AutoUploadJobConfig item, int levelInTreeOfItem) {
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onDisplayUploadJob(item);
                }
            });
            TextView nameView = itemView.findViewById(R.id.list_item_name);
            TextView detailView = itemView.findViewById(R.id.list_item_details);
            AppCompatCheckboxTriState deleteUploadedFiles = itemView.findViewById(R.id.delete_uploaded);
            AppCompatCheckboxTriState jobEnabledView = itemView.findViewById(R.id.enabled);
            CustomImageButton deleteButton = itemView.findViewById(R.id.list_item_delete_button);
            deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onDeleteUploadJob(item);
                }
            });

            nameView.setText(getUploadFromSummary(context, item));
            detailView.setText(getUploadToSummary(context, item));
            deleteUploadedFiles.setChecked(item.isJobEnabled(getContext()) && item.isDeleteFilesAfterUpload(getContext()));
            jobEnabledView.setChecked(item.isJobEnabled(getContext()) && item.isJobValid(getContext()));
        }

        @Override
        protected AppCompatCheckboxTriState getAppCompatCheckboxTriState(View view) {
            return view.findViewById(R.id.list_item_checked);
        }
    }

    private void addAutoUploadJobConfigToListIfNew(AutoUploadJobConfig cfg) {
        if(cfg.exists(getContext()) && adapter.getPosition(Long.valueOf(cfg.getJobId())) < 0) {
            uploadJobIds.add(cfg.getJobId());
            loadListValues(uploadJobIds);
        }
    }

    private boolean hasChanged(AutoUploadJobConfig cfg) {
        String newSummary = cfg.getSummary(getPreference().getSharedPreferences(), getContext());
        return !ObjectUtils.areEqual(activeUploadJobConfigSummary, newSummary);
    }

    @Subscribe(sticky = true)
    public void onEvent(AutoUploadJobViewCompleteEvent event) {
        if(!trackableEventManager.wasTrackingEvent(event)) {
            return;
        }
        AutoUploadJobConfig cfg = new AutoUploadJobConfig(event.getJobId());
        if(hasChanged(cfg)) {
            jobsHaveChanged = true;
        }
        addAutoUploadJobConfigToListIfNew(cfg);
        // reload the list anyway (content of job may have altered)
        loadListValues(uploadJobIds);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList(STATE_DELETED_JOBS, deletedItems);
        outState.putBundle(STATE_TRACKABLE_EVENT_MANAGER, trackableEventManager.onSaveInstanceState());
        outState.putIntegerArrayList(STATE_JOB_IDS, uploadJobIds);
        outState.putString(STATE_ACTIVE_JOB_CONFIG_SUMMARY, activeUploadJobConfigSummary);
        outState.putBoolean(STATE_JOBS_HAVE_CHANGED, jobsHaveChanged);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState != null) {
            deletedItems = savedInstanceState.getParcelableArrayList(STATE_DELETED_JOBS);
            trackableEventManager.onRestoreInstanceState(savedInstanceState.getBundle(STATE_TRACKABLE_EVENT_MANAGER));
            uploadJobIds = savedInstanceState.getIntegerArrayList(STATE_JOB_IDS);
            activeUploadJobConfigSummary = savedInstanceState.getString(STATE_ACTIVE_JOB_CONFIG_SUMMARY);
            jobsHaveChanged = savedInstanceState.getBoolean(STATE_JOBS_HAVE_CHANGED);
        } else {
            uploadJobIds = getPreference().getUploadJobIdsFromValue();
        }
    }
}
