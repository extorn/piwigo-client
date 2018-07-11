package delit.piwigoclient.ui.events;

import delit.piwigoclient.piwigoApi.upload.UploadJob;

public class BackgroundUploadStoppedEvent {
    private UploadJob uploadJob;
    public BackgroundUploadStoppedEvent(UploadJob uploadJob) {
        this.uploadJob = uploadJob;
    }

    public UploadJob getUploadJob() {
        return uploadJob;
    }
}
