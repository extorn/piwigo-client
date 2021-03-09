package delit.piwigoclient.piwigoApi.upload.messages;

import android.net.Uri;

import com.drew.lang.annotations.Nullable;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class PiwigoUploadProgressUpdateResponse extends PiwigoResponseBufferingHandler.BaseResponse {

    private final int progress;
    private final Uri fileForUpload;

    public PiwigoUploadProgressUpdateResponse(long jobId, @Nullable Uri fileForUpload, int progress) {
        super(jobId, false);
        this.progress = progress;
        this.fileForUpload = fileForUpload;
    }

    public @Nullable Uri getFileForUpload() {
        return fileForUpload;
    }

    public int getProgress() {
        return progress;
    }

    public long getJobId() {
        return getMessageId();
    }
}
