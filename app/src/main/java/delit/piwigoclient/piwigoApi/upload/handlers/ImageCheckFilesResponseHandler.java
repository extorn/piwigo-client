package delit.piwigoclient.piwigoApi.upload.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import delit.libs.http.RequestParams;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoWsResponseHandler;

public class ImageCheckFilesResponseHandler<T extends ResourceItem> extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "CheckResourceRspHdlr";
    private final T resourceItem;
    private Boolean fileMatches;

    public ImageCheckFilesResponseHandler(T piwigoResource) {
        super("pwg.images.checkFiles", TAG);
        this.resourceItem = piwigoResource;
        if(resourceItem.getFileChecksum() == null) {
            throw new IllegalArgumentException("Unable to check image on server without a local file checksum");
        }
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("image_id", String.valueOf(resourceItem.getId()));
        params.put("file_sum", resourceItem.getFileChecksum());
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();
        String testResult = result.get("file").getAsString();
        Boolean same = null;
        if ("equals".equals(testResult)) {
            same = true;
        } else if ("differs".equals(testResult)) {
            same = false;
        }
        fileMatches = same;

        if (!getUseSynchronousMode()) {
            PiwigoResourceCheckRetrievedResponse<T> r = new PiwigoResourceCheckRetrievedResponse<>(getMessageId(), getPiwigoMethod(), resourceItem, same, isCached);
            storeResponse(r);
        }
    }

    public boolean isFileMatch() {
        return fileMatches != null && fileMatches;
    }

    public static class PiwigoResourceCheckRetrievedResponse<T extends ResourceItem> extends PiwigoResponseBufferingHandler.BasePiwigoResponse {

        private final T resource;
        private final Boolean fileMatches;

        public PiwigoResourceCheckRetrievedResponse(long messageId, String piwigoMethod, T resource, Boolean fileMatches, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.resource = resource;
            this.fileMatches = fileMatches;
        }

        public Boolean getFileMatches() {
            return fileMatches;
        }

        public boolean isFileMatch() {
            return fileMatches != null && fileMatches;
        }

        public T getResource() {
            return resource;
        }
    }
}