package delit.piwigoclient.piwigoApi.handlers;

import com.loopj.android.http.AsyncHttpResponseHandler;

import org.json.JSONException;
import org.json.JSONObject;

import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.CachingAsyncHttpClient;
import delit.piwigoclient.piwigoApi.http.RequestHandle;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class LogoutResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "LogoutRspHdlr";
    public static final String METHOD = "pwg.session.logout";

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
        if (!PiwigoSessionDetails.isLoggedIn()) {
            onLogout();
            return null;
        } else {
            return super.runCall(client, handler);
        }
    }

    @Override
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {
        onLogout();
    }

    private void onLogout() {
        PiwigoSessionDetails.logout();
        PiwigoResponseBufferingHandler.PiwigoOnLogoutResponse r = new PiwigoResponseBufferingHandler.PiwigoOnLogoutResponse(getMessageId(), getPiwigoMethod());
        storeResponse(r);
    }
}
