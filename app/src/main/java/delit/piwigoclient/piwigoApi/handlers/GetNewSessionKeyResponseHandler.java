package delit.piwigoclient.piwigoApi.handlers;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.gson.JsonElement;

import org.json.JSONException;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class GetNewSessionKeyResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "GetSessKeyRspHdlr";
    private final String password;

    public GetNewSessionKeyResponseHandler(String password, Context context) {
        super("pwg.session.login", TAG);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        this.password = password;
    }

    @Override
    public RequestParams buildRequestParameters() {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        String username = getConnectionPrefs().getPiwigoUsername(prefs, getContext());
        String pass = this.password;
        if(pass == null) {
            pass = getConnectionPrefs().getPiwigoPassword(prefs, getContext());
        }

        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("username", username);
        params.put("password", pass);
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        PiwigoNewSessionKeyResponse r = new PiwigoNewSessionKeyResponse(getMessageId(), getPiwigoMethod());
        storeResponse(r);
    }

    public static class PiwigoNewSessionKeyResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        public PiwigoNewSessionKeyResponse(long messageId, String piwigoMethod) {
            super(messageId, piwigoMethod);
        }
    }
}
