package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;
import com.loopj.android.http.AsyncHttpResponseHandler;

import org.json.JSONException;

import delit.libs.http.RequestParams;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.CachingAsyncHttpClient;
import delit.piwigoclient.piwigoApi.http.RequestHandle;

public class LogoutResponseHandler extends AbstractPiwigoWsResponseHandler {

    public static final String METHOD = "pwg.session.logout";
    private static final String TAG = "LogoutRspHdlr";

    public LogoutResponseHandler() {
        super(METHOD, TAG);
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        return params;
    }

    @Override
    public RequestHandle runCall(CachingAsyncHttpClient client, AsyncHttpResponseHandler handler) {
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(getConnectionPrefs());
        if (sessionDetails == null || !sessionDetails.isLoggedIn()) {
            onLogout(sessionDetails, false);
            return null;
        } else {
            return super.runCall(client, handler);
        }
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        onLogout(PiwigoSessionDetails.getInstance(getConnectionPrefs()), isCached);
    }

    private void onLogout(PiwigoSessionDetails sessionDetails, boolean isCached) {
        if (sessionDetails != null) {
            PiwigoSessionDetails.logout(getConnectionPrefs(), getContext());
        }
        PiwigoOnLogoutResponse r = new PiwigoOnLogoutResponse(getMessageId(), getPiwigoMethod(), isCached);
        storeResponse(r);
    }

    public static class PiwigoOnLogoutResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        public PiwigoOnLogoutResponse(long messageId, String piwigoMethod, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
        }
    }

    public boolean isUseHttpGet() {
        return true;
    }
}
