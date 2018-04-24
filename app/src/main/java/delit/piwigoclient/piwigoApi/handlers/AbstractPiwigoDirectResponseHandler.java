package delit.piwigoclient.piwigoApi.handlers;

import android.content.Context;

import java.util.concurrent.atomic.AtomicLong;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.Worker;

/**
 * Created by gareth on 27/07/17.
 */

public abstract class AbstractPiwigoDirectResponseHandler extends AbstractBasicPiwigoResponseHandler {
    private long messageId;
    private PiwigoResponseBufferingHandler.BaseResponse response;
    private static final AtomicLong nextMessageId = new AtomicLong();
    private boolean publishResponses = true;

    public static synchronized long getNextMessageId() {
        long id;
        id = nextMessageId.incrementAndGet();
        if(id < 0) {
            nextMessageId.set(0);
            id = 0;
        }
        return id;
    }

    public AbstractPiwigoDirectResponseHandler(String tag) {
        super(tag);
        messageId = getNextMessageId();
    }

    public long getMessageId() {
        return messageId;
    }

    public void setMessageId(long messageId) {
        if(this.messageId < 0 || this.messageId == messageId) {
            this.messageId = messageId;
        } else {
            throw new IllegalArgumentException("Message ID can only be set once for a handler");
        }
    }

    public void setPublishResponses(boolean publishResponses) {
        this.publishResponses = publishResponses;
    }

    @Override
    public final void preRunCall() {
        /*if(messageId < 0) {
            messageId = getNextMessageId();
        }*/
    }

    protected void storeResponse(PiwigoResponseBufferingHandler.BaseResponse response) {

        if(!getUseSynchronousMode() && publishResponses) {
            PiwigoResponseBufferingHandler.getDefault().processResponse(response);
        } else {
            this.response = response;
        }
    }

    @Override
    public boolean isSuccess() {
        return super.isSuccess() && !isResponseError();
    }

    public PiwigoResponseBufferingHandler.BaseResponse getResponse() {
        return response;
    }

    public boolean isResponseError() {
        return response instanceof PiwigoResponseBufferingHandler.ErrorResponse;
    }

    public long invokeAsync(Context context) {
        return new Worker( this, context).start(messageId);
    }

    public long invokeAsyncAgain() {
        return new Worker( this, this.getContext()).start(messageId);
    }
}
