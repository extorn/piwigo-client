package delit.piwigoclient.piwigoApi.upload.messages;

import android.net.Uri;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class PiwigoUploadProgressUpdateResponse extends PiwigoResponseBufferingHandler.BaseResponse {

    private final int progress;
    private final Uri fileForUpload;

    public PiwigoUploadProgressUpdateResponse(long jobId, Uri fileForUpload, int progress) {
        super(jobId, false);
        this.progress = progress;
        this.fileForUpload = fileForUpload;
    }

    public Uri getFileForUpload() {
        return fileForUpload;
    }

    public int getProgress() {
        return progress;
    }

    public long getJobId() {
        return getMessageId();
    }
}
