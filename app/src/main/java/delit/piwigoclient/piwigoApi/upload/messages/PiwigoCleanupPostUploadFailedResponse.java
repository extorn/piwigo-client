package delit.piwigoclient.piwigoApi.upload.messages;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class PiwigoCleanupPostUploadFailedResponse extends PiwigoResponseBufferingHandler.BaseResponse {
    private final PiwigoResponseBufferingHandler.Response error;

    public PiwigoCleanupPostUploadFailedResponse(long jobId, PiwigoResponseBufferingHandler.Response error) {
        super(jobId, false);
        this.error = error;
    }

    public PiwigoResponseBufferingHandler.Response getError() {
        return error;
    }

    public long getJobId() {
        return getMessageId();
    }
}
