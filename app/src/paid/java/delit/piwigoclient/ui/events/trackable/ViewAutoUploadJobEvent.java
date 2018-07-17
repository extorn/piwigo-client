package delit.piwigoclient.ui.events;

import delit.piwigoclient.ui.events.trackable.TrackableRequestEvent;

public class ViewAutoUploadJobEvent extends TrackableRequestEvent {
    private int jobId;

    public ViewAutoUploadJobEvent(int jobId) {
        this.jobId = jobId;
    }

    public int getJobId() {
        return jobId;
    }
}
