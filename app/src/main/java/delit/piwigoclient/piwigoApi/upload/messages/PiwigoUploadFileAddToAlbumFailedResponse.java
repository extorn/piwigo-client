package delit.piwigoclient.piwigoApi.upload.messages;

import android.net.Uri;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class PiwigoUploadFileAddToAlbumFailedResponse extends PiwigoResponseBufferingHandler.BaseResponse {

    private final PiwigoResponseBufferingHandler.Response error;
    private final Uri fileForUpload;
    private final boolean itemUploadCancelled;

    public PiwigoUploadFileAddToAlbumFailedResponse(long jobId, Uri fileForUpload, boolean itemUploadCancelled, PiwigoResponseBufferingHandler.Response error) {
        super(jobId, false);
        this.itemUploadCancelled = itemUploadCancelled;
        this.error = error;
        this.fileForUpload = fileForUpload;
    }

    public boolean isItemUploadCancelled() {
        return itemUploadCancelled;
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
