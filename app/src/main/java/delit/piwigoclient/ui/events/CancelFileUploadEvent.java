package delit.piwigoclient.ui.events;

import android.net.Uri;

/**
 * Created by gareth on 12/06/17.
 */

public class CancelFileUploadEvent {

    private final long jobId;
    private final Uri cancelledFile;

    public CancelFileUploadEvent(long jobId, Uri cancelledFile) {
        this.jobId = jobId;
        this.cancelledFile = cancelledFile;
    }

    public long getJobId() {
        return jobId;
    }

    public Uri getCancelledFile() {
        return cancelledFile;
    }
}
