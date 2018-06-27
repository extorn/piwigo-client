package delit.piwigoclient.ui.events.trackable;

public class AutoUploadJobViewRequestedEvent extends TrackableRequestEvent {
    private int jobId;

    public AutoUploadJobViewRequestedEvent(int jobId) {
        this.jobId = jobId;
    }

    public int getJobId() {
        return jobId;
    }
}
