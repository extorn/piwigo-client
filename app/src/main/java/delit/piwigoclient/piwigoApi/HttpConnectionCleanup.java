package delit.piwigoclient.piwigoApi;

import android.content.Context;

import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler;

/**
 * Created by gareth on 4/19/18.
 */

public class HttpConnectionCleanup extends Worker {


    private final long messageId;
    private final ConnectionPreferences.ProfilePreferences connectionPrefs;

    public HttpConnectionCleanup(ConnectionPreferences.ProfilePreferences connectionPrefs, Context context) {
        super(null, context);
        this.connectionPrefs = connectionPrefs;
        messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
    }

    @Override
    protected boolean executeCall(long messageId) {
        HttpClientFactory.getInstance(getContext()).clearCachedClients(connectionPrefs);
        PiwigoResponseBufferingHandler.getDefault().processResponse(new HttpClientsShutdownResponse(messageId));
        return true;
    }

    public long start() {
        return start(messageId);
    }

    public static class HttpClientsShutdownResponse extends PiwigoResponseBufferingHandler.BaseResponse {

        public HttpClientsShutdownResponse(long messageId) {
            super(messageId, true);
        }
    }
}
