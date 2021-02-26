package delit.piwigoclient.piwigoApi.upload.messages;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class PiwigoUploadUnexpectedLocalErrorResponse extends PiwigoResponseBufferingHandler.BaseResponse implements PiwigoResponseBufferingHandler.ErrorResponse {

    private final Exception error;

    public PiwigoUploadUnexpectedLocalErrorResponse(long jobId, Exception error) {
        super(jobId, false);
        this.error = error;
    }

    public Exception getError() {
        return error;
    }

    public long getJobId() {
        return getMessageId();
    }
}
