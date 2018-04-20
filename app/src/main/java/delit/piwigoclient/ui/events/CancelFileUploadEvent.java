package delit.piwigoclient.ui.events;

import java.io.File;

/**
 * Created by gareth on 12/06/17.
 */

public class CancelFileUploadEvent {

    private final long jobId;
    private final File cancelledFile;

    public CancelFileUploadEvent(long jobId, File cancelledFile) {
        this.jobId = jobId;
        this.cancelledFile = cancelledFile;
    }

    public long getJobId() {
        return jobId;
    }

    public File getCancelledFile() {
        return cancelledFile;
    }
}
