package delit.piwigoclient.ui.events;

import delit.piwigoclient.piwigoApi.upload.UploadJob;

public class ViewJobStatusDetailsEvent {
    private UploadJob job;

    public ViewJobStatusDetailsEvent(UploadJob job) {
        this.job = job;
    }

    public UploadJob getJob() {
        return job;
    }
}
