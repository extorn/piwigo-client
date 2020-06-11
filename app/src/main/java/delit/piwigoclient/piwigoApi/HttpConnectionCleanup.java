package delit.piwigoclient.piwigoApi;

import android.content.Context;

import delit.libs.ui.SafeAsyncTask;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler;

/**
 * Created by gareth on 4/19/18.
 */

public class HttpConnectionCleanup extends SafeAsyncTask {


    private final long messageId;
    private final ConnectionPreferences.ProfilePreferences connectionPrefs;
    private final boolean fullClientShutdown;

    public HttpConnectionCleanup(ConnectionPreferences.ProfilePreferences connectionPrefs, Context context) {
        this(connectionPrefs, context, false);
    }

    public HttpConnectionCleanup(ConnectionPreferences.ProfilePreferences connectionPrefs, Context context, boolean fullClientShutdown) {
        withContext(context);
        this.connectionPrefs = connectionPrefs;
        messageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        this.fullClientShutdown = fullClientShutdown;
    }

    public long getMessageId() {
        return messageId;
    }

    @Override
    protected Object doInBackgroundSafely(Object[] objects) {
        if (fullClientShutdown) {
            HttpClientFactory.getInstance(getContext()).clearCachedClients(connectionPrefs);
        } else {
            HttpClientFactory.getInstance(getContext()).cancelAllRunningHttpRequests(connectionPrefs);
        }
        PiwigoSessionDetails.logout(connectionPrefs, getContext());
        PiwigoResponseBufferingHandler.getDefault().processResponse(new HttpClientsShutdownResponse(messageId));
        return true;
    }

    public long start() {
        execute();
        return getMessageId();
    }

    public static class HttpClientsShutdownResponse extends PiwigoResponseBufferingHandler.BaseResponse {

        public HttpClientsShutdownResponse(long messageId) {
            super(messageId, true);
        }
    }
}
