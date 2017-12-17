package delit.piwigoclient.piwigoApi.handlers;

import android.util.JsonReader;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;

import cz.msebera.android.httpclient.Header;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.model.piwigo.PiwigoGalleryDetails;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class CommunitySessionStatusResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "CommunitySessStat";
    private final boolean forceUsingCommunityPlugin;

    public CommunitySessionStatusResponseHandler(boolean forceUsingCommunityPluginIfAvailable) {
        super("community.session.getStatus", TAG);
        this.forceUsingCommunityPlugin = forceUsingCommunityPluginIfAvailable;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        return params;
    }

    @Override
    public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error, boolean triedToGetNewSession) {

        try {
            String response = responseBody == null ? null : new String(responseBody, getCharset());
            JSONObject rsp = new JSONObject(response);
            onPiwigoFailure(rsp);

        } catch (UnsupportedEncodingException e) {
            // response body not available - treat as a standard http error
            super.onFailure(statusCode, headers, responseBody, error, triedToGetNewSession);
        } catch (JSONException e) {
            // response body not available - treat as a standard http error
            super.onFailure(statusCode, headers, responseBody, error, triedToGetNewSession);
        }
    }

    @Override
    protected void onPiwigoFailure(JSONObject rsp) throws JSONException {
        int errorCode = rsp.getInt("err");
        String errorMessage = rsp.getString("message");
        if(errorCode == 501 && "Method name is not valid".equals(errorMessage)) {
            PiwigoSessionDetails.getInstance().setUseCommunityPlugin(false);
            PiwigoResponseBufferingHandler.PiwigoCommunitySessionStatusResponse r = new PiwigoResponseBufferingHandler.PiwigoCommunitySessionStatusResponse(getMessageId(), getPiwigoMethod(), null);
            storeResponse(r);
        } else {
            super.onPiwigoFailure(rsp);
        }
    }

    @Override
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {

        JSONObject response = rsp.getJSONObject("result");
        String uploadToAlbumsList = response.getString("upload_categories_getList_method");
        String realUserStatus = response.getString("real_user_status");

        if(uploadToAlbumsList.equals("community.categories.getList")
                || (uploadToAlbumsList.equals("pwg.categories.getAdminList") && forceUsingCommunityPlugin)) {
            PiwigoSessionDetails.getInstance().setUseCommunityPlugin(true);
        } else {
            PiwigoSessionDetails.getInstance().setUseCommunityPlugin(false);
        }
        PiwigoSessionDetails.getInstance().updateUserType(realUserStatus);
        PiwigoResponseBufferingHandler.PiwigoCommunitySessionStatusResponse r = new PiwigoResponseBufferingHandler.PiwigoCommunitySessionStatusResponse(getMessageId(), getPiwigoMethod(), uploadToAlbumsList);
        storeResponse(r);
    }
}