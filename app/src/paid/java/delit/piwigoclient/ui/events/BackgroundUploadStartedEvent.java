package delit.piwigoclient.ui.events;

import delit.libs.ui.events.SingleUseEvent;
import delit.piwigoclient.piwigoApi.upload.UploadJob;

public class BackgroundUploadStartedEvent extends SingleUseEvent {
    private UploadJob uploadJob;
    private boolean jobBeingRerun;

    public BackgroundUploadStartedEvent(UploadJob uploadJob, boolean jobBeingRerun) {
        this.uploadJob = uploadJob;
        this.jobBeingRerun = jobBeingRerun;
    }

    public UploadJob getUploadJob() {
        return uploadJob;
    }

    public boolean isEventValid() {
        return !uploadJob.isStatusFinished();
    }

    public boolean isJobBeingRerun() {
        return jobBeingRerun;
    }
}
