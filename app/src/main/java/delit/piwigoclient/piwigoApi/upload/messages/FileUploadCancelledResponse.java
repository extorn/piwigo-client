package delit.piwigoclient.piwigoApi.upload.messages;

import android.net.Uri;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class FileUploadCancelledResponse extends PiwigoResponseBufferingHandler.BaseResponse {

    private final Uri cancelledFile;

    public FileUploadCancelledResponse(long messageId, Uri cancelledFile) {
        super(messageId, true);
        this.cancelledFile = cancelledFile;
    }

    public Uri getCancelledFile() {
        return cancelledFile;
    }
}
