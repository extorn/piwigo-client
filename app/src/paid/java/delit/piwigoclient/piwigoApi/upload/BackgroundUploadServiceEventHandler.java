package delit.piwigoclient.piwigoApi.upload;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.BackgroundUploadStartedEvent;
import delit.piwigoclient.ui.events.BackgroundUploadStoppedEvent;
import delit.piwigoclient.ui.events.BackgroundUploadThreadCheckingForTasksEvent;
import delit.piwigoclient.ui.events.BackgroundUploadThreadStartedEvent;
import delit.piwigoclient.ui.events.BackgroundUploadThreadTerminatedEvent;

public class BackgroundUploadServiceEventHandler {

    private UIHelper uihelper;

    public void register(UIHelper uihelper) {
        this.uihelper = uihelper;
        EventBus eventBus = EventBus.getDefault();
        if(!eventBus.isRegistered(this)) {
            eventBus.register(this);
        }
    }
    
    public void unregister() {
        uihelper = null;
        EventBus eventBus = EventBus.getDefault();
        if(eventBus.isRegistered(this)) {
            eventBus.unregister(this);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(BackgroundUploadThreadTerminatedEvent event) {
        if (event.isHandled()) {
            return;
        }
        uihelper.showDetailedShortMsg(R.string.alert_information, uihelper.getString(R.string.alert_auto_upload_service_stopped));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(BackgroundUploadThreadStartedEvent event) {
        if (event.isHandled()) {
            return;
        }
        uihelper.showShortMsg(R.string.alert_auto_upload_service_started);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(BackgroundUploadStartedEvent event) {
        if (event.isHandled()) {
            return;
        }
        String uploadingToServer = event.getUploadJob().getConnectionPrefs().getPiwigoServerAddress(uihelper.getPrefs(), uihelper.getAppContext());
        if(event.isJobBeingRerun()) {
            uihelper.showDetailedShortMsg(R.string.alert_information, uihelper.getString(R.string.alert_auto_upload_service_job_restarted, uploadingToServer, event.getUploadJob().getFilesForUpload().size()));
        } else {
            uihelper.showDetailedShortMsg(R.string.alert_information, uihelper.getString(R.string.alert_auto_upload_service_job_started, uploadingToServer, event.getUploadJob().getFilesForUpload().size()));
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(BackgroundUploadStoppedEvent event) {
        if (event.isHandled()) {
            return;
        }
        String uploadingToServer = event.getUploadJob().getConnectionPrefs().getPiwigoServerAddress(uihelper.getPrefs(), uihelper.getAppContext());
        if(event.getUploadJob().isFinished()) {
            uihelper.showDetailedShortMsg(R.string.alert_information, uihelper.getString(R.string.alert_auto_upload_service_job_finished_success, uploadingToServer));
        } else {
            if(event.getUploadJob().getFilesNotYetUploaded(uihelper.getAppContext()).size() < event.getUploadJob().getFilesForUpload().size()) {
                uihelper.showDetailedMsg(R.string.alert_information, uihelper.getString(R.string.alert_auto_upload_service_job_finished_partial_success, uploadingToServer));
            } else {
                uihelper.showDetailedMsg(R.string.alert_information, uihelper.getString(R.string.alert_auto_upload_service_job_finished_failure, uploadingToServer));
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(BackgroundUploadThreadCheckingForTasksEvent event) {
        if (event.isHandled()) {
            return;
        }
        uihelper.showDetailedShortMsg(R.string.alert_information, uihelper.getString(R.string.alert_auto_upload_service_checking_for_tasks));
    }
}
