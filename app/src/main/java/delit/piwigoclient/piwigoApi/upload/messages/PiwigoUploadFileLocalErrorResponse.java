package delit.piwigoclient.piwigoApi.upload.messages;

import android.net.Uri;

public class PiwigoUploadFileLocalErrorResponse extends PiwigoUploadUnexpectedLocalErrorResponse {

    private final Uri fileForUpload;
    private final boolean isItemUploadCancelled;

    public PiwigoUploadFileLocalErrorResponse(long jobId, Uri fileForUpload, boolean isItemUploadCancelled, Exception error) {
        super(jobId, error);
        this.fileForUpload = fileForUpload;
        this.isItemUploadCancelled = isItemUploadCancelled;
    }

    public Uri getFileForUpload() {
        return fileForUpload;
    }

    public long getJobId() {
        return getMessageId();
    }

    public boolean isItemUploadCancelled() {
        return isItemUploadCancelled;
    }
}
