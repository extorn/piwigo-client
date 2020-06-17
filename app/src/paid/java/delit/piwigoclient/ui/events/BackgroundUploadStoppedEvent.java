package delit.piwigoclient.ui.events;

import delit.libs.ui.events.SingleUseEvent;
import delit.piwigoclient.piwigoApi.upload.UploadJob;

public class BackgroundUploadStoppedEvent extends SingleUseEvent {
    private UploadJob uploadJob;
    public BackgroundUploadStoppedEvent(UploadJob uploadJob) {
        this.uploadJob = uploadJob;
    }

    public UploadJob getUploadJob() {
        return uploadJob;
    }
}
