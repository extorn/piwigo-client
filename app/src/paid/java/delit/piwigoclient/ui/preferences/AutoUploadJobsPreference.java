package delit.piwigoclient.ui.preferences;

import android.app.AlertDialog;
import android.content.Context;
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

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

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
import delit.piwigoclient.util.ObjectUtils;

public class AutoUploadJobsPreference extends DialogPreference {

    private boolean mValueSet;
    private ListView itemListView;
    private CustomImageButton addListItemButton;
    private String mValue;
    private ArrayList<AutoUploadJobConfig> deletedItems = new ArrayList<>();
    private AutoUploadJobsListAdapter adapter;
    private boolean showDialogOnCreate;
    private TrackableEventManager trackableEventManager = new TrackableEventManager();

    public AutoUploadJobsPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        loadListValues(getPersistedValue());
    }

    public View getView(View convertView, ViewGroup parent) {
        parent.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                if(showDialogOnCreate) {
                    showDialog(null);
                }
            }

            @Override
            public void onViewDetachedFromWindow(View v) {

            }
        });
        return super.getView(convertView, parent);
    }

    public AutoUploadJobsPreference(Context context, AttributeSet attrs) {
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
        int count = 0;
        if(adapter != null) {
            count = adapter.getCount();
        }
        return getContext().getString(R.string.preference_data_upload_automatic_upload_jobs_summary, count);
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
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
        heading.setText(R.string.preference_data_upload_automatic_upload_jobs_title);
        heading.setVisibility(View.VISIBLE);

        itemListView = view.findViewById(R.id.list);

//        view.findViewById(R.id.list_action_cancel_button).setVisibility(View.GONE);
        view.findViewById(R.id.list_action_toggle_all_button).setVisibility(View.GONE);
        addListItemButton = view.findViewById(R.id.list_action_add_item_button);
        addListItemButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDisplayUploadJob(null);
            }
        });
        view.findViewById(R.id.list_action_save_button).setVisibility(View.GONE);

        adapter.linkToListView(itemListView, null, null);

        return view;
    }

    private void onDeleteUploadJob(AutoUploadJobConfig item) {

        deletedItems.add(item);
        ArrayList<Integer> uploadJobIds = getUploadJobIds();
        uploadJobIds.remove(item.getJobId());
        loadListValues(uploadJobIds);

    }

    private void onDisplayUploadJob(AutoUploadJobConfig uploadJobConfig) {
        int jobId;
        if(uploadJobConfig == null) {
            jobId = getNextJobId();
        } else {
            jobId = uploadJobConfig.getJobId();
        }
        showDialogOnCreate = true;
        trackableEventManager.postTrackedEvent(new AutoUploadJobViewRequestedEvent(jobId));
        getDialog().dismiss();
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

    private SharedPreferences getAppSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
    }

    @Override
    protected void onAttachedToHierarchy(PreferenceManager preferenceManager) {
        EventBus.getDefault().register(this);
        super.onAttachedToHierarchy(preferenceManager);
    }

    @Override
    protected void onPrepareForRemoval() {
        EventBus.getDefault().unregister(this);
        super.onPrepareForRemoval();
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

    private ArrayList<Integer> getUploadJobIds() {
        ArrayList<Long> jobIds = adapter.getItemIds();
        ArrayList<Integer> uploadJobIds = new ArrayList<>(jobIds.size());
        for(Long jobId : jobIds) {
            uploadJobIds.add(jobId.intValue());
        }
        return uploadJobIds;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if(positiveResult) {
            mValueSet = false; // force the value to be saved.

            String val = CollectionUtils.toCsvList(getUploadJobIds());

            if (callChangeListener(val)) {
                setValue(val);
                for(AutoUploadJobConfig deletedJob : deletedItems) {
                    deletedJob.deletePreferences();
                }
                deletedItems.clear();
            }
        } else {
            //reset the view.
            loadListValues(getPersistedValue());
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        mValue = restoreValue ? getPersistedString("") : (String) defaultValue;
        setValue(mValue);
        loadListValues(getPersistedValue());
    }

    private List<Integer> getPersistedValue() {
        return CollectionUtils.integersFromCsvList(mValue);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            // No need to save instance state since it's persistent
            return superState;
        }

        final AutoUploadJobsPreference.SavedState myState = new AutoUploadJobsPreference.SavedState(superState);
        myState.value = getValue();
        myState.deletedJobs = deletedItems;
        myState.showDialogOnCreate = showDialogOnCreate;
        myState.trackableEventManagerState = trackableEventManager.onSaveInstanceState();
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(AutoUploadJobsPreference.SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        AutoUploadJobsPreference.SavedState myState = (AutoUploadJobsPreference.SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        setValue(myState.value);
        deletedItems = myState.deletedJobs;
        trackableEventManager.onRestoreInstanceState(myState.trackableEventManagerState);

        if(myState.showDialogOnCreate && !getDialog().isShowing()) {
            showDialog(null);
        }
    }

    private void addAutoUploadJobConfigToListIfNew(int jobId) {
        AutoUploadJobConfig cfg = new AutoUploadJobConfig(getContext(), jobId);
        if(cfg.exists(getContext()) && adapter.getPosition(Long.valueOf(cfg.getJobId())) < 0) {
            ArrayList<Integer> uploadJobIds = getUploadJobIds();
            uploadJobIds.add(cfg.getJobId());
            loadListValues(uploadJobIds);
        }
    }

    public static class SavedState extends BaseSavedState {

        public static final Creator<AutoUploadJobsPreference.SavedState> CREATOR =
                new Creator<AutoUploadJobsPreference.SavedState>() {
                    public AutoUploadJobsPreference.SavedState createFromParcel(Parcel in) {
                        return new AutoUploadJobsPreference.SavedState(in);
                    }

                    public AutoUploadJobsPreference.SavedState[] newArray(int size) {
                        return new AutoUploadJobsPreference.SavedState[size];
                    }
                };

        private Bundle trackableEventManagerState;
        private String value;
        private ArrayList<AutoUploadJobConfig> deletedJobs;
        private boolean showDialogOnCreate;

        public SavedState(Parcel source) {
            super(source);
            value = source.readString();
            showDialogOnCreate = Boolean.valueOf(source.readString());
            deletedJobs = (ArrayList<AutoUploadJobConfig>) source.readSerializable();
            trackableEventManagerState = source.readBundle();
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(value);
            dest.writeString(Boolean.toString(showDialogOnCreate));
            dest.writeSerializable(deletedJobs);
            dest.writeBundle(trackableEventManagerState);
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
            return item.getLocalFolderToMonitor(getContext()).getAbsolutePath();
        }

        public String getUploadToSummary(AutoUploadJobConfig item) {

            ConnectionPreferences.ProfilePreferences connPrefs = item.getConnectionPrefs(getContext());
            String serverName = connPrefs.getPiwigoServerAddress(getSharedPreferences(), getContext());
            String username = connPrefs.getPiwigoUsername(getSharedPreferences(), getContext());
            String uploadFolder = item.getUploadToAlbumName(getContext());

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
            jobEnabledView.setButtonDrawable(R.drawable.always_clear_checkbox);
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

    @Subscribe(sticky = true)
    public void onEvent(AutoUploadJobViewCompleteEvent event) {
        if(!trackableEventManager.wasTrackingEvent(event)) {
            return;
        }
        addAutoUploadJobConfigToListIfNew(event.getJobId());
        // reload the list anyway (content of job may have altered)
        notifyChanged();
    }

}
