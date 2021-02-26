package delit.piwigoclient.piwigoApi.upload.messages;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.upload.UploadJob;

public class PiwigoUploadFileJobCompleteResponse extends PiwigoResponseBufferingHandler.BaseResponse {

    private final UploadJob job;

    public PiwigoUploadFileJobCompleteResponse(long messageId, UploadJob job) {

        super(messageId, true);
        this.job = job;
    }

    public long getJobId() {
        return getMessageId();
    }

    public UploadJob getJob() {
        return job;
    }
}
