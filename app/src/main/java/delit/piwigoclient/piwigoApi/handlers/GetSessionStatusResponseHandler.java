package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import delit.libs.http.RequestParams;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

import static delit.piwigoclient.model.piwigo.PiwigoSessionDetails.UNKNOWN_VERSION;

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
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        PiwigoSessionDetails oldCredentials = getPiwigoSessionDetails();
        PiwigoSessionDetails newCredentials = parseSessionDetails(rsp);
        if (isCached) {
            // try and ensure it gets a new one before doing anything vital!
            newCredentials.setCached(isCached);
            newCredentials.setSessionMayHaveExpired();
        }
        PiwigoSessionDetails.setInstance(getConnectionPrefs(), newCredentials);

        PiwigoSessionStatusRetrievedResponse r = new PiwigoSessionStatusRetrievedResponse(getMessageId(), getPiwigoMethod(), oldCredentials, newCredentials, isCached);
        storeResponse(r);
    }

    private PiwigoSessionDetails parseSessionDetails(JsonElement rsp) {
        JsonObject result = rsp.getAsJsonObject();
        String user = result.get("username").getAsString();
        String userStatus = result.get("status").getAsString();
        String token = result.get("pwg_token").getAsString();
        JsonElement versionElem = result.get("version");
        String piwigoVersion = versionElem != null ? versionElem.getAsString() : UNKNOWN_VERSION;
        Set<String> availableSizes = new HashSet<>();
        JsonElement availableSizesJsonElem = result.get("available_sizes");
        if (availableSizesJsonElem == null || availableSizesJsonElem.isJsonNull()) {
            // the sizes will be empty and thus warnings will be presented to the user.
        } else {
            JsonArray availableSizesArr = availableSizesJsonElem.getAsJsonArray();
            for (int i = 0; i < availableSizesArr.size(); i++) {
                availableSizes.add(availableSizesArr.get(i).getAsString());
            }
        }

        PiwigoSessionDetails sessionDetails;
        String serverUrl = getPiwigoServerUrl();
        long userGuid = serverUrl.hashCode() + user.hashCode() + userStatus.hashCode();

        if (userStatus.equals("admin") || userStatus.equals("webmaster")) {
            JsonElement uploadChunkSizeElem = result.get("upload_form_chunk_size");
            long uploadChunkSizeKb = -1L;
            if (uploadChunkSizeElem != null && !uploadChunkSizeElem.isJsonNull()) {
                uploadChunkSizeKb = uploadChunkSizeElem.getAsLong();
            }
            JsonElement fileTypesUploadAllowedJsonElem = result.get("upload_file_types");
            Set<String> uploadFileTypesSet;
            if (fileTypesUploadAllowedJsonElem != null && !fileTypesUploadAllowedJsonElem.isJsonNull()) {
                String uploadFileTypes = fileTypesUploadAllowedJsonElem.getAsString();
                StringTokenizer st = new StringTokenizer(uploadFileTypes, ",");
                uploadFileTypesSet = new HashSet<>(st.countTokens());
                while (st.hasMoreTokens()) {
                    uploadFileTypesSet.add(st.nextToken());
                }
            } else {
                uploadFileTypesSet = new HashSet<>(0);
            }
            sessionDetails = new PiwigoSessionDetails(getConnectionPrefs(), serverUrl, userGuid, user, userStatus, piwigoVersion, availableSizes, uploadFileTypesSet, uploadChunkSizeKb, token);
        } else {
            sessionDetails = new PiwigoSessionDetails(getConnectionPrefs(), serverUrl, userGuid, user, userStatus, piwigoVersion, availableSizes, token);
        }
        return sessionDetails;
    }

    public static class PiwigoSessionStatusRetrievedResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {

        private final PiwigoSessionDetails oldCredentials, newCredentials;

        public PiwigoSessionStatusRetrievedResponse(long messageId, String piwigoMethod, PiwigoSessionDetails oldCredentials, PiwigoSessionDetails newCredentials, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.oldCredentials = oldCredentials;
            this.newCredentials = newCredentials;
        }

        public PiwigoSessionDetails getOldCredentials() {
            return oldCredentials;
        }

        public PiwigoSessionDetails getNewCredentials() {
            return newCredentials;
        }
    }

    public boolean isUseHttpGet() {
        return true;
    }
}