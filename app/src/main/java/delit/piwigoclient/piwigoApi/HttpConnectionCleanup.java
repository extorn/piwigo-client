package delit.piwigoclient.piwigoApi;

import android.content.Context;

import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler;

/**
 * Created by gareth on 4/19/18.
 */

public class HttpConnectionCleanup extends Worker {


    private final long messageId;

    public HttpConnectionCleanup(Context context) {
        super(null, context);
        messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
    }

    @Override
    protected boolean executeCall(long messageId) {
        HttpClientFactory.getInstance(getContext()).clearCachedClients();
        PiwigoResponseBufferingHandler.getDefault().processResponse(new PiwigoResponseBufferingHandler.HttpClientsShutdownResponse(messageId));
        return true;
    }

    public long start() {
        return start(messageId);
    }
}
