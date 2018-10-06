package delit.piwigoclient.ui.events.trackable;

public class ViewAutoUploadJobEvent extends TrackableRequestEvent {
    private int jobId;

    public ViewAutoUploadJobEvent(int jobId) {
        this.jobId = jobId;
    }

    public int getJobId() {
        return jobId;
    }
}
