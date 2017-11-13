package delit.piwigoclient.piwigoApi.handlers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
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
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {
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

        if (userStatus.equals("admin") || userStatus.equals("webmaster")) {
            Long uploadChunkSize = result.getLong("upload_form_chunk_size");
            String uploadFileTypes = result.getString("upload_file_types");
            StringTokenizer st = new StringTokenizer(uploadFileTypes, ",");
            Set<String> uploadFileTypesSet = new HashSet<>(st.countTokens());
            while (st.hasMoreTokens()) {
                uploadFileTypesSet.add(st.nextToken());
            }
            sessionDetails = new PiwigoSessionDetails(user, userStatus, piwigoVersion, availableSizes, uploadFileTypesSet, uploadChunkSize, token);
        } else {
            sessionDetails = new PiwigoSessionDetails(user, userStatus, piwigoVersion, availableSizes, token);
        }

        PiwigoSessionDetails.setInstance(sessionDetails);

        PiwigoResponseBufferingHandler.PiwigoSessionStatusRetrievedResponse r = new PiwigoResponseBufferingHandler.PiwigoSessionStatusRetrievedResponse(getMessageId(), getPiwigoMethod());
        storeResponse(r);
    }

}