package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import org.json.JSONException;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;

import cz.msebera.android.httpclient.Header;
import delit.libs.http.RequestParams;
import delit.piwigoclient.model.piwigo.PiwigoJsonResponse;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

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
    public boolean onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error, boolean triedToGetNewSession, boolean isCached) {
        String response = null;
        try {
            GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
            Gson gson = gsonBuilder.create();
            PiwigoJsonResponse piwigoResponse = gson.fromJson(new InputStreamReader(new ByteArrayInputStream(responseBody)), PiwigoJsonResponse.class);
            onPiwigoFailure(piwigoResponse, isCached);
        } catch (JsonSyntaxException e) {
            super.onFailure(statusCode, headers, responseBody, error, triedToGetNewSession, isCached);
        } catch (JsonIOException e) {
            super.onFailure(statusCode, headers, responseBody, error, triedToGetNewSession, isCached);
        } catch (JSONException e) {
            super.onFailure(statusCode, headers, responseBody, error, triedToGetNewSession, isCached);
        }
        return triedToGetNewSession;
    }
/*
    @Override
    protected void onPiwigoFailure(PiwigoJsonResponse rsp) throws JSONException {
        int errorCode = rsp.getErr();
        String errorMessage = rsp.getMessage();
        if (errorCode == 501 && "Method name is not valid".equals(errorMessage)) {
            resetFailureAsASuccess();
            PiwigoSessionDetails.getInstance(getConnectionPrefs()).setUseCommunityPlugin(false);
            PiwigoCommunitySessionStatusResponse r = new PiwigoCommunitySessionStatusResponse(getMessageId(), getPiwigoMethod(), null);
            storeResponse(r);
        } else {
            super.onPiwigoFailure(rsp);
        }
    }*/

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {

        JsonObject result = rsp.getAsJsonObject();
        String uploadToAlbumsList = result.get("upload_categories_getList_method").getAsString();
        String realUserStatus = result.get("real_user_status").getAsString();

        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(getConnectionPrefs());
        if (uploadToAlbumsList.equals("community.categories.getList")
                || (uploadToAlbumsList.equals("pwg.categories.getAdminList") && forceUsingCommunityPlugin)) {
            sessionDetails.setUseCommunityPlugin(true);
        } else {
            sessionDetails.setUseCommunityPlugin(false);
        }
        sessionDetails.updateUserType(realUserStatus);
        PiwigoCommunitySessionStatusResponse r = new PiwigoCommunitySessionStatusResponse(getMessageId(), getPiwigoMethod(), uploadToAlbumsList, isCached);
        storeResponse(r);
    }

    public static class PiwigoCommunitySessionStatusResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {

        private final String categoryListMethod;

        public PiwigoCommunitySessionStatusResponse(long messageId, String piwigoMethod, String categoryListMethod, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.categoryListMethod = categoryListMethod;
        }

        public String getCategoryListMethod() {
            return categoryListMethod;
        }

        public boolean isAvailable() {
            return categoryListMethod != null;
        }
    }

    public boolean isUseHttpGet() {
        return true;
    }
}