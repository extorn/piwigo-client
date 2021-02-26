package delit.piwigoclient.piwigoApi.upload.messages;

import android.net.Uri;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class PiwigoUploadFileChunkFailedResponse extends PiwigoResponseBufferingHandler.BaseResponse {

    private final PiwigoResponseBufferingHandler.Response error;
    private final Uri fileForUpload;

    public PiwigoUploadFileChunkFailedResponse(long jobId, Uri fileForUpload, PiwigoResponseBufferingHandler.Response error) {
        super(jobId, false);
        this.error = error;
        this.fileForUpload = fileForUpload;
    }

    public Uri getFileForUpload() {
        return fileForUpload;
    }

    public PiwigoResponseBufferingHandler.Response getError() {
        return error;
    }

    public long getJobId() {
        return getMessageId();
    }
}
