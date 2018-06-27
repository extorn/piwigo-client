package delit.piwigoclient.ui.events;

import delit.piwigoclient.piwigoApi.upload.UploadJob;

public class BackgroundUploadStartedEvent {
    private UploadJob uploadJob;
    public BackgroundUploadStartedEvent(UploadJob uploadJob) {
        this.uploadJob = uploadJob;
    }

    public UploadJob getUploadJob() {
        return uploadJob;
    }

    public boolean isEventValid() {
        return !uploadJob.isFinished();
    }
}
