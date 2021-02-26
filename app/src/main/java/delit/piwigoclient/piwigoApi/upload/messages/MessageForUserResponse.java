package delit.piwigoclient.piwigoApi.upload.messages;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class MessageForUserResponse extends PiwigoResponseBufferingHandler.BaseResponse {
    private final String message;

    public MessageForUserResponse(long jobId, String message) {
        super(jobId, false);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
