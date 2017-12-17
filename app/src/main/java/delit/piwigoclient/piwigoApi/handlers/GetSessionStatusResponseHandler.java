package delit.piwigoclient.piwigoApi.handlers;

import com.loopj.android.http.AsyncHttpResponseHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.CachingAsyncHttpClient;
import delit.piwigoclient.piwigoApi.http.RequestHandle;
import delit.piwigoclient.piwigoApi.http.RequestParams;
import delit.piwigoclient.ui.AdsManager;

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
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {
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

    private PiwigoSessionDetails parseSessionDetails(JSONObject rsp) throws JSONException {
        JSONObject result = rsp.getJSONObject("result");
        String user = result.getString("username");
        String userStatus = result.getString("status");
        String token = result.getString("pwg_token");
        String piwigoVersion = result.getString("version");
        Set<String> availableSizes = new HashSet<>();
        JSONArray availableSizesArr = result.getJSONArray("available_sizes");
        for (int i = 0; i < availableSizesArr.length(); i++) {
            availableSizes.add(availableSizesArr.getString(i));
        }

        PiwigoSessionDetails sessionDetails;
        String serverUrl = getPiwigoServerUrl();
        long userGuid = serverUrl.hashCode() + user.hashCode() + userStatus.hashCode();

        if (userStatus.equals("admin") || userStatus.equals("webmaster")) {
            Long uploadChunkSize = result.getLong("upload_form_chunk_size");
            String uploadFileTypes = result.getString("upload_file_types");
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