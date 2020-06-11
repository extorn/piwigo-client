package delit.piwigoclient.ui;

import android.os.Bundle;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.HashSet;

import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;
import delit.libs.util.VersionUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.ui.events.BackgroundUploadStartedEvent;
import delit.piwigoclient.ui.events.BackgroundUploadStoppedEvent;
import delit.piwigoclient.ui.events.BackgroundUploadThreadCheckingForTasksEvent;
import delit.piwigoclient.ui.events.BackgroundUploadThreadStartedEvent;
import delit.piwigoclient.ui.events.BackgroundUploadThreadTerminatedEvent;
import delit.piwigoclient.ui.events.ViewJobStatusDetailsEvent;
import delit.piwigoclient.ui.events.ViewTagEvent;
import delit.piwigoclient.ui.events.trackable.AutoUploadJobViewRequestedEvent;
import delit.piwigoclient.ui.events.trackable.TagSelectionNeededEvent;
import delit.piwigoclient.ui.favorites.ViewFavoritesFragment;
import delit.piwigoclient.ui.preferences.AutoUploadJobPreferenceFragment;
import delit.piwigoclient.ui.tags.TagSelectFragment;
import delit.piwigoclient.ui.tags.TagsListFragment;
import delit.piwigoclient.ui.tags.ViewTagFragment;
import delit.piwigoclient.ui.upload.UploadJobStatusDetailsFragment;

/**
 * Created by gareth on 07/04/18.
 */

public class MainActivity extends AbstractMainActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    protected void showFavorites() {
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        int[] pluginVersion = VersionUtils.parseVersionString(sessionDetails.getPiwigoClientPluginVersion());
        boolean versionSupported = VersionUtils.versionExceeds(new int[]{1,0,8}, pluginVersion);
        if(versionSupported) {
            showFragmentNow(ViewFavoritesFragment.newInstance());
        } else {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_plugin_required_pattern, "PiwigoClientWsExt", "1.0.8"), R.string.button_close);
        }
    }

    @Override
    protected void showTags() {
        BaseRecyclerViewAdapterPreferences prefs = new BaseRecyclerViewAdapterPreferences();
        prefs.setEnabled(true);
        prefs.setAllowItemAddition(true);
        //TODO tags deletion is not working (unsupported in piwigo server at the moment)
//        if(PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) {
//            prefs.deletable();
//        }
        TagsListFragment fragment = TagsListFragment.newInstance(prefs);
        showFragmentNow(fragment);
    }

    private void showTagSelectionFragment(int actionId, BaseRecyclerViewAdapterPreferences prefs, HashSet<Long> initialSelection, HashSet<Tag> unsavedTags) {
        TagSelectFragment fragment = TagSelectFragment.newInstance(prefs, actionId, initialSelection, unsavedTags);
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(TagSelectionNeededEvent event) {
        BaseRecyclerViewAdapterPreferences prefs = new BaseRecyclerViewAdapterPreferences().selectable(event.isAllowMultiSelect(), event.isInitialSelectionLocked());
        prefs.setAllowItemAddition(true);
        if(!event.isAllowEditing()) {
            prefs.readonly();
        }
        showTagSelectionFragment(event.getActionId(), prefs , event.getInitialSelection(), event.getNewUnsavedTags());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ViewTagEvent event) {
        ViewTagFragment fragment = ViewTagFragment.newInstance(event.getTag());
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(final ViewJobStatusDetailsEvent event) {
        UploadJobStatusDetailsFragment fragment = UploadJobStatusDetailsFragment.newInstance(event.getJob());
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AutoUploadJobViewRequestedEvent event) {
        AutoUploadJobPreferenceFragment fragment = AutoUploadJobPreferenceFragment.newInstance(event.getActionId(), event.getJobId());
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(BackgroundUploadThreadTerminatedEvent event) {
        getUiHelper().showDetailedShortMsg(R.string.alert_information, getString(R.string.alert_auto_upload_service_stopped));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(BackgroundUploadThreadStartedEvent event) {
        getUiHelper().showShortMsg(R.string.alert_auto_upload_service_started);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(BackgroundUploadStartedEvent event) {
        String uploadingToServer = event.getUploadJob().getConnectionPrefs().getPiwigoServerAddress(prefs, getApplicationContext());
        if(event.isJobBeingRerun()) {
            getUiHelper().showDetailedShortMsg(R.string.alert_information, getString(R.string.alert_auto_upload_service_job_restarted, uploadingToServer, event.getUploadJob().getFilesForUpload().size()));
        } else {
            getUiHelper().showDetailedShortMsg(R.string.alert_information, getString(R.string.alert_auto_upload_service_job_started, uploadingToServer, event.getUploadJob().getFilesForUpload().size()));
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(BackgroundUploadStoppedEvent event) {
        String uploadingToServer = event.getUploadJob().getConnectionPrefs().getPiwigoServerAddress(prefs, getApplicationContext());
        if(event.getUploadJob().isFinished()) {
            getUiHelper().showDetailedShortMsg(R.string.alert_information, getString(R.string.alert_auto_upload_service_job_finished_success, uploadingToServer));
        } else {
            if(event.getUploadJob().getFilesNotYetUploaded().size() < event.getUploadJob().getFilesForUpload().size()) {
                getUiHelper().showDetailedMsg(R.string.alert_information, getString(R.string.alert_auto_upload_service_job_finished_partial_success, uploadingToServer));
            } else {
                getUiHelper().showDetailedMsg(R.string.alert_information, getString(R.string.alert_auto_upload_service_job_finished_failure, uploadingToServer));
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(BackgroundUploadThreadCheckingForTasksEvent event) {
        getUiHelper().showDetailedShortMsg(R.string.alert_information, getString(R.string.alert_auto_upload_service_checking_for_tasks));
    }
}
