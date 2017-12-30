package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.loopj.android.http.AsyncHttpResponseHandler;

import org.json.JSONException;

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.CachingAsyncHttpClient;
import delit.piwigoclient.piwigoApi.http.RequestHandle;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class GetSessionStatusResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "LoginRspHdlr";

    public GetSessionStatusResponseHandler() {
        super("pwg.session.getStatus", TAG);
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        return params;
    }

    @Override
    public RequestHandle runCall(CachingAsyncHttpClient client, AsyncHttpResponseHandler handler) {
        if(PiwigoSessionDetails.getInstance() != null) {
            onPiwigoSessionRetrieved();
            return null;
        } else {
            return super.runCall(client, handler);
        }
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        PiwigoSessionDetails.setInstance(parseSessionDetails(rsp));

        PiwigoResponseBufferingHandler.PiwigoSessionStatusRetrievedResponse r = new PiwigoResponseBufferingHandler.PiwigoSessionStatusRetrievedResponse(getMessageId(), getPiwigoMethod());
        onPiwigoSessionRetrieved();
        storeResponse(r);
    }

    private void onPiwigoSessionRetrieved() {
        if(PiwigoSessionDetails.getInstance() != null) {
            //TODO forcing true will allow thumbnails to be made available (with extra call) for albums hidden to admin users.
            CommunitySessionStatusResponseHandler communitySessionLoadHandler = new CommunitySessionStatusResponseHandler(false);
            runAndWaitForHandlerToFinish(communitySessionLoadHandler);
            if(!PiwigoSessionDetails.isLoggedInWithSessionDetails()) {
                reportNestedFailure(communitySessionLoadHandler);
            }
        }
    }

    private PiwigoSessionDetails parseSessionDetails(JsonElement rsp) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();
        String user = result.get("username").getAsString();
        String userStatus = result.get("status").getAsString();
        String token = result.get("pwg_token").getAsString();
        String piwigoVersion = result.get("version").getAsString();
        Set<String> availableSizes = new HashSet<>();
        JsonArray availableSizesArr = result.get("available_sizes").getAsJsonArray();
        for (int i = 0; i < availableSizesArr.size(); i++) {
            availableSizes.add(availableSizesArr.get(i).getAsString());
        }

        PiwigoSessionDetails sessionDetails;
        String serverUrl = getPiwigoServerUrl();
        long userGuid = serverUrl.hashCode() + user.hashCode() + userStatus.hashCode();

        if (userStatus.equals("admin") || userStatus.equals("webmaster")) {
            Long uploadChunkSize = result.get("upload_form_chunk_size").getAsLong();
            String uploadFileTypes = result.get("upload_file_types").getAsString();
            StringTokenizer st = new StringTokenizer(uploadFileTypes, ",");
            Set<String> uploadFileTypesSet = new HashSet<>(st.countTokens());
            while (st.hasMoreTokens()) {
                uploadFileTypesSet.add(st.nextToken());
            }
            sessionDetails = new PiwigoSessionDetails(userGuid, user, userStatus, piwigoVersion, availableSizes, uploadFileTypesSet, uploadChunkSize, token);
        } else {
            sessionDetails = new PiwigoSessionDetails(userGuid, user, userStatus, piwigoVersion, availableSizes, token);
        }
        return sessionDetails;
    }

}