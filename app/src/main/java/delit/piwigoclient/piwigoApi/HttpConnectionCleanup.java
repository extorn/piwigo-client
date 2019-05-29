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
    private final boolean fullClientShutdown;

    public HttpConnectionCleanup(ConnectionPreferences.ProfilePreferences connectionPrefs, Context context) {
        this(connectionPrefs, context, false);
    }

    public HttpConnectionCleanup(ConnectionPreferences.ProfilePreferences connectionPrefs, Context context, boolean fullClientShutdown) {
        super(null, context);
        this.connectionPrefs = connectionPrefs;
        messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        this.fullClientShutdown = fullClientShutdown;
    }

    @Override
    protected boolean executeCall(long messageId) {
        if (fullClientShutdown) {
            HttpClientFactory.getInstance(getContext()).clearCachedClients(connectionPrefs);
        } else {
            HttpClientFactory.getInstance(getContext()).cancelAllRunningHttpRequests(connectionPrefs);
        }
        PiwigoResponseBufferingHandler.getDefault().processResponse(new HttpClientsShutdownResponse(messageId));
        return true;
    }

    public long start() {
        return start(messageId);
    }

    public long getMessageId() {
        return messageId;
    }

    public static class HttpClientsShutdownResponse extends PiwigoResponseBufferingHandler.BaseResponse {

        public HttpClientsShutdownResponse(long messageId) {
            super(messageId, true);
        }
    }
}
