package delit.piwigoclient.piwigoApi.upload.handlers;

import org.json.JSONException;
import org.json.JSONObject;

import delit.piwigoclient.model.UploadFileFragment;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoWsResponseHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

@Deprecated
public class ImageUploadFileChunkResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "UploadFileChunkRspHdlr";

    private UploadFileFragment fileChunk;

    public ImageUploadFileChunkResponseHandler(UploadFileFragment fileChunk) {
        super("pwg.images.addChunk", TAG);
        this.fileChunk = fileChunk;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("data", fileChunk.getData());
        params.put("original_sum", fileChunk.getFileChecksum());
        params.put("position", String.valueOf(fileChunk.getChunkId()));
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {
        PiwigoResponseBufferingHandler.PiwigoUploadFileChunkResponse r = new PiwigoResponseBufferingHandler.PiwigoUploadFileChunkResponse(getMessageId(), getPiwigoMethod());
        storeResponse(r);
    }
}