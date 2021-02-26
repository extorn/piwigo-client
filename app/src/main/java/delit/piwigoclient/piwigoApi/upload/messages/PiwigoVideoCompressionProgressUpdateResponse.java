package delit.piwigoclient.piwigoApi.upload.messages;

import android.net.Uri;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class PiwigoVideoCompressionProgressUpdateResponse extends PiwigoResponseBufferingHandler.BaseResponse {

    private final int progress;
    private final Uri fileForUpload;
    private final Uri compressedFileUpload;

    public PiwigoVideoCompressionProgressUpdateResponse(long jobId, Uri fileForUpload, Uri compressedFileUpload, int progress) {
        super(jobId, false);
        this.progress = progress;
        this.fileForUpload = fileForUpload;
        this.compressedFileUpload = compressedFileUpload;
    }

    public Uri getFileForUpload() {
        return fileForUpload;
    }

    public Uri getCompressedFileUpload() {
        return compressedFileUpload;
    }

    public int getProgress() {
        return progress;
    }

    public long getJobId() {
        return getMessageId();
    }
}
