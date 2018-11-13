package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.HashSet;
import java.util.Iterator;

import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class ImageUpdateTagsResponseHandler<T extends ResourceItem> extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "UpdateResTagsRspHdlr";
    public static final String ERROR_IMAGE_NOT_FOUND = "Image not found";
    public static final String ERROR_NO_DELETE_PERMISSION = "You are not allowed to delete tags";
    private final T piwigoResource;

    public ImageUpdateTagsResponseHandler(T piwigoResource) {
        super("user_tags.tags.update", TAG);
        this.piwigoResource = piwigoResource;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("image_id", String.valueOf(piwigoResource.getId()));
        params.put("tags", toCsvList(piwigoResource.getTags()));
        return params;
    }

    private String toCsvList(HashSet<Tag> tags) {
        if(tags.size() == 0) {
            return "";
        }
        Iterator<Tag> iter = tags.iterator();
        StringBuilder sb = new StringBuilder();
        sb.append(iter.next().getName());
        while(iter.hasNext()) {
            sb.append(',');
            sb.append(iter.next().getName());
        }
        return sb.toString();
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        JsonObject rspObj = rsp.getAsJsonObject();
        PiwigoResponseBufferingHandler.BaseResponse responseForUser;
        if(rspObj.has("error")) {
            // actually failed.
            JsonObject errObj = rspObj.getAsJsonObject("error");
            String errorReason = errObj.get("item").getAsString();
            responseForUser = new PiwigoResponseBufferingHandler.PiwigoServerErrorResponse(this, 0, errorReason, isCached);
        }
        String resultMsg = null;
        if(rspObj.has("info")) {
            resultMsg = rspObj.get("info").getAsString();
        }
        if("Tags updated".equals(resultMsg)) {
            responseForUser = new BaseImageUpdateInfoResponseHandler.PiwigoUpdateResourceInfoResponse<>(getMessageId(), getPiwigoMethod(), piwigoResource, isCached);
        } else {
            responseForUser = new PiwigoResponseBufferingHandler.PiwigoServerErrorResponse(this, 0, ERROR_IMAGE_NOT_FOUND, isCached);
        }
        storeResponse(responseForUser);
    }
}