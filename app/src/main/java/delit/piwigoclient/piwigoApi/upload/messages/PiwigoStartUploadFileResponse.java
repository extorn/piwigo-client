package delit.piwigoclient.piwigoApi.upload.messages;

import android.net.Uri;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class PiwigoStartUploadFileResponse extends PiwigoResponseBufferingHandler.BaseResponse {

    private final Uri fileForUpload;

    public PiwigoStartUploadFileResponse(long jobId, Uri fileForUpload) {
        super(jobId, false);
        this.fileForUpload = fileForUpload;
    }

    public Uri getFileForUpload() {
        return fileForUpload;
    }

    public long getJobId() {
        return getMessageId();
    }
}
