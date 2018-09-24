package delit.piwigoclient.ui.preferences;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.DialogPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceDialogFragmentCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.ads.AdView;
import com.google.android.gms.common.util.Strings;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.button.AppCompatCheckboxTriState;
import delit.piwigoclient.ui.common.button.CustomImageButton;
import delit.piwigoclient.ui.common.list.MultiSourceListAdapter;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.events.trackable.AutoUploadJobViewCompleteEvent;
import delit.piwigoclient.ui.events.trackable.AutoUploadJobViewRequestedEvent;
import delit.piwigoclient.ui.events.trackable.TrackableEventManager;
import delit.piwigoclient.util.CollectionUtils;

public class AutoUploadJobsPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat implements DialogPreference.TargetFragment {
    private ListView itemListView;
    private AutoUploadJobsListAdapter adapter;
    private ArrayList<AutoUploadJobConfig> deletedItems = new ArrayList<>();
    private TrackableEventManager trackableEventManager = new TrackableEventManager();
    private String STATE_DELETED_JOBS = "AutoUploadJobsPreference.deletedJobs";
    private String STATE_TRACKABLE_EVENT_MANAGER = "AutoUploadJobsPreference.trackableEventManagerState";
    private ArrayList<Integer> uploadJobIds;
    private String STATE_JOB_IDS = "AutoUploadJobsPreference.JobIds";


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
        EventBus.getDefault().register(this);
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
                pref.setValue(val);
                for(AutoUploadJobConfig deletedJob : deletedItems) {
                    deletedJob.deletePreferences();
                }
                deletedItems.clear();
            }
        }
    }

    private void onDeleteUploadJob(AutoUploadJobConfig item) {

        deletedItems.add(item);
        uploadJobIds.remove(item.getJobId());
        loadListValues(uploadJobIds);
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

        TextView heading = view.findViewById(R.id.heading);
        heading.setText(R.string.preference_data_upload_automatic_upload_jobs_title);
        heading.setVisibility(View.VISIBLE);

        itemListView = view.findViewById(R.id.list);

//        view.findViewById(R.id.list_action_cancel_button).setVisibility(View.GONE);
        view.findViewById(R.id.list_action_toggle_all_button).setVisibility(View.GONE);
        View addListItemButton = view.findViewById(R.id.list_action_add_item_button);
        addListItemButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDisplayUploadJob(null);
            }
        });
        view.findViewById(R.id.list_action_save_button).setVisibility(View.GONE);

        return view;
    }

    private void onDisplayUploadJob(AutoUploadJobConfig uploadJobConfig) {
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
            uploadJobConfigs.add(new AutoUploadJobConfig(getContext(), jobId));
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

        public String getUploadFromSummary(AutoUploadJobConfig item) {
            File localFolder = item.getLocalFolderToMonitor(getContext());
            if(localFolder == null) {
                return "???";
            }
            return localFolder.getAbsolutePath();
        }

        public String getUploadToSummary(AutoUploadJobConfig item) {

            ConnectionPreferences.ProfilePreferences connPrefs = item.getConnectionPrefs(getContext());
            String serverName = connPrefs.getPiwigoServerAddress(getPreference().getSharedPreferences(), getContext());
            String username = connPrefs.getPiwigoUsername(getPreference().getSharedPreferences(), getContext());
            String uploadFolder = item.getUploadToAlbumName(getContext());
            if(uploadFolder == null) {
                uploadFolder = "???";
            }

            String user = Strings.emptyToNull(username);
            if(user == null) {
                user = getContext().getString(R.string.server_connection_preference_user_guest);
            }
            return user + '@' + serverName + '(' + uploadFolder + ')';
        }

        @Override
        protected void setViewContentForItemDisplay(View itemView, final AutoUploadJobConfig item, int levelInTreeOfItem) {
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onDisplayUploadJob(item);
                }
            });
            TextView nameView = itemView.findViewById(R.id.name);
            TextView detailView = itemView.findViewById(R.id.details);
            AppCompatCheckboxTriState jobEnabledView = itemView.findViewById(R.id.enabled);
            CustomImageButton deleteButton = itemView.findViewById(R.id.list_item_delete_button);
            deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onDeleteUploadJob(item);
                }
            });

            nameView.setText(getUploadFromSummary(item));
            detailView.setText(getUploadToSummary(item));
            jobEnabledView.setChecked(item.isJobEnabled(getContext()) && item.isJobValid(getContext()));
        }

        @Override
        protected AppCompatCheckboxTriState getAppCompatCheckboxTriState(View view) {
            return view.findViewById(R.id.checked);
        }
    }

    private void addAutoUploadJobConfigToListIfNew(int jobId) {
        AutoUploadJobConfig cfg = new AutoUploadJobConfig(getContext(), jobId);
        if(cfg.exists(getContext()) && adapter.getPosition(Long.valueOf(cfg.getJobId())) < 0) {
            uploadJobIds.add(cfg.getJobId());
            loadListValues(uploadJobIds);
        }
    }

    @Subscribe(sticky = true)
    public void onEvent(AutoUploadJobViewCompleteEvent event) {
        if(!trackableEventManager.wasTrackingEvent(event)) {
            return;
        }
        addAutoUploadJobConfigToListIfNew(event.getJobId());
        // reload the list anyway (content of job may have altered)
        loadListValues(uploadJobIds);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_DELETED_JOBS, deletedItems);
        outState.putBundle(STATE_TRACKABLE_EVENT_MANAGER, trackableEventManager.onSaveInstanceState());
        outState.putIntegerArrayList(STATE_JOB_IDS, uploadJobIds);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState != null) {
            deletedItems = (ArrayList<AutoUploadJobConfig>) savedInstanceState.getSerializable(STATE_DELETED_JOBS);
            trackableEventManager.onRestoreInstanceState(savedInstanceState.getBundle(STATE_TRACKABLE_EVENT_MANAGER));
            uploadJobIds = savedInstanceState.getIntegerArrayList(STATE_JOB_IDS);
        } else {
            uploadJobIds = getPreference().getUploadJobIdsFromValue();
        }
    }
}
