package delit.piwigoclient.ui.events;

import delit.libs.ui.events.SingleUseEvent;
import delit.piwigoclient.piwigoApi.upload.UploadJob;

public class ViewJobStatusDetailsEvent extends SingleUseEvent {
    private UploadJob job;

    public ViewJobStatusDetailsEvent(UploadJob job) {
        this.job = job;
    }

    public UploadJob getJob() {
        return job;
    }
}
