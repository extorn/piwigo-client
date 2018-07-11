package delit.piwigoclient.ui.events.trackable;

public class AutoUploadJobViewCompleteEvent extends TrackableResponseEvent {
    private int jobId;

    public AutoUploadJobViewCompleteEvent(int actionId, int jobId) {
        super(actionId);
        this.jobId = jobId;
    }

    public int getJobId() {
        return jobId;
    }
}
