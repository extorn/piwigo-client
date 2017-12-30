package delit.piwigoclient.piwigoApi.upload.handlers;

import org.json.JSONException;import com.google.gson.JsonElement;import com.google.gson.JsonObject;import com.google.gson.JsonArray;

import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoWsResponseHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class ImageCheckFilesResponseHandler<T extends ResourceItem> extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "CheckResourceRspHdlr";
    private T resourceItem;
    private Boolean fileMatches;

    public ImageCheckFilesResponseHandler(T piwigoResource) {
        super("pwg.images.checkFiles", TAG);
        this.resourceItem = piwigoResource;
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
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();
        String testResult = result.get("file").getAsString();
        Boolean same = null;
        if("equals".equals(testResult)) {
            same = true;
        } else if("differs".equals(testResult)) {
            same = false;
        }
        fileMatches = same;

        if(!getUseSynchronousMode()) {
            PiwigoResponseBufferingHandler.PiwigoResourceCheckRetrievedResponse<T> r = new PiwigoResponseBufferingHandler.PiwigoResourceCheckRetrievedResponse<>(getMessageId(), getPiwigoMethod(), resourceItem, same);
            storeResponse(r);
        }
    }

    public boolean isFileMatch() {
        return fileMatches != null && fileMatches;
    }

}