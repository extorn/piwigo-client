package delit.piwigoclient.ui;

import android.os.Bundle;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.HashSet;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.events.BackgroundUploadStartedEvent;
import delit.piwigoclient.ui.events.BackgroundUploadStoppedEvent;
import delit.piwigoclient.ui.events.BackgroundUploadThreadCheckingForTasksEvent;
import delit.piwigoclient.ui.events.BackgroundUploadThreadStartedEvent;
import delit.piwigoclient.ui.events.BackgroundUploadThreadTerminatedEvent;
import delit.piwigoclient.ui.events.trackable.AutoUploadJobViewRequestedEvent;
import delit.piwigoclient.ui.events.ViewTagEvent;
import delit.piwigoclient.ui.events.trackable.TagSelectionNeededEvent;
import delit.piwigoclient.ui.preferences.AutoUploadJobPreferenceFragment;
import delit.piwigoclient.ui.tags.TagSelectFragment;
import delit.piwigoclient.ui.tags.TagsListFragment;
import delit.piwigoclient.ui.tags.ViewTagFragment;

/**
 * Created by gareth on 07/04/18.
 */

public class MainActivity extends AbstractMainActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    protected void showTags() {
        BaseRecyclerViewAdapterPreferences prefs = new BaseRecyclerViewAdapterPreferences();
        prefs.setEnabled(true);
        TagsListFragment fragment = TagsListFragment.newInstance(prefs);
        showFragmentNow(fragment);
    }

    private void showTagSelectionFragment(int actionId, BaseRecyclerViewAdapterPreferences prefs, HashSet<Long> initialSelection) {
        TagSelectFragment fragment = TagSelectFragment.newInstance(prefs, actionId, initialSelection);
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(TagSelectionNeededEvent event) {
        BaseRecyclerViewAdapterPreferences prefs = new BaseRecyclerViewAdapterPreferences().selectable(event.isAllowMultiSelect(), event.isInitialSelectionLocked());
        prefs.setAllowItemAddition(true);
        if(!event.isAllowEditing()) {
            prefs.readonly();
        }
        showTagSelectionFragment(event.getActionId(), prefs , event.getInitialSelection());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ViewTagEvent event) {
        ViewTagFragment fragment = ViewTagFragment.newInstance(event.getTag());
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AutoUploadJobViewRequestedEvent event) {
        AutoUploadJobPreferenceFragment fragment = AutoUploadJobPreferenceFragment.newInstance(event.getActionId(), event.getJobId());
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(BackgroundUploadThreadTerminatedEvent event) {
        getUiHelper().showToast(getString(R.string.alert_auto_upload_service_stopped));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(BackgroundUploadThreadStartedEvent event) {
        getUiHelper().showToast(R.string.alert_auto_upload_service_started);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(BackgroundUploadStartedEvent event) {
        String uploadingToServer = event.getUploadJob().getConnectionPrefs().getPiwigoServerAddress(prefs, getApplicationContext());
        if(event.isJobBeingRerun()) {
            getUiHelper().showToast(getString(R.string.alert_auto_upload_service_job_restarted, uploadingToServer, event.getUploadJob().getFilesForUpload().size()));
        } else {
            getUiHelper().showToast(getString(R.string.alert_auto_upload_service_job_started, uploadingToServer, event.getUploadJob().getFilesForUpload().size()));
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(BackgroundUploadStoppedEvent event) {
        String uploadingToServer = event.getUploadJob().getConnectionPrefs().getPiwigoServerAddress(prefs, getApplicationContext());
        if(event.getUploadJob().isFinished()) {
            getUiHelper().showToast(getString(R.string.alert_auto_upload_service_job_finished_success, uploadingToServer));
        } else {
            if(event.getUploadJob().getFilesNotYetUploaded().size() < event.getUploadJob().getFilesForUpload().size()) {
                getUiHelper().showLongToast(getString(R.string.alert_auto_upload_service_job_finished_partial_success, uploadingToServer));
            } else {
                getUiHelper().showLongToast(getString(R.string.alert_auto_upload_service_job_finished_failure, uploadingToServer));
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(BackgroundUploadThreadCheckingForTasksEvent event) {
        getUiHelper().showToast(getString(R.string.alert_auto_upload_service_checking_for_tasks));
    }
}
