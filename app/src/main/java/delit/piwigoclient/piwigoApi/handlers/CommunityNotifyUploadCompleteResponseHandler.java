package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.Set;

import delit.libs.http.RequestParams;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class CommunityNotifyUploadCompleteResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "CommUplCmplRspHdlr";
    private final long parentAlbumId;
    private final Set<Long> uploadedResourceIds;

    public CommunityNotifyUploadCompleteResponseHandler(Set<Long> uploadedResourceIds, long parentAlbumId) {
        super("community.images.uploadCompleted", TAG);
        this.parentAlbumId = parentAlbumId;
        this.uploadedResourceIds = uploadedResourceIds;
    }

    @Override
    public RequestParams buildRequestParameters() {

        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("category_id", String.valueOf(parentAlbumId));
        params.put("pwg_token", getPwgSessionToken());
        for (Long resId : uploadedResourceIds) {
            params.add("image_id[]", resId.toString());
        }
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        int pendingItems = 0;
        if (rsp.isJsonObject()) {
            JsonObject result = rsp.getAsJsonObject();
            if (result.has("pending")) {
                JsonArray pendingItemsArr = result.getAsJsonArray("pending");
                pendingItems = pendingItemsArr.size();
            }
        }
        CommunityNotifyUploadCompleteResponse r = new CommunityNotifyUploadCompleteResponse(getMessageId(), getPiwigoMethod(), isCached, pendingItems);
        storeResponse(r);
    }

    public boolean isUseHttpGet() {
        return true;
    }

    public static class CommunityNotifyUploadCompleteResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {

        private final int pendingItems;

        public CommunityNotifyUploadCompleteResponse(long messageId, String piwigoMethod, boolean isCached, int pendingItems) {
            super(messageId, piwigoMethod, isCached);
            this.pendingItems = pendingItems;
        }


        public int getPendingItems() {
            return pendingItems;
        }
    }
}
