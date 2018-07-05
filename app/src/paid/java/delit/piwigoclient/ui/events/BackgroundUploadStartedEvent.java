package delit.piwigoclient.ui.events;

import delit.piwigoclient.piwigoApi.upload.UploadJob;

public class BackgroundUploadStartedEvent {
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
        return !uploadJob.isFinished();
    }

    public boolean isJobBeingRerun() {
        return jobBeingRerun;
    }
}
