package delit.piwigoclient.piwigoApi.upload.handlers;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;

import delit.piwigoclient.model.UploadFileChunk;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoWsResponseHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class NewImageUploadFileChunkResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "UploadFileChunkRspHdlr";

    private UploadFileChunk fileChunk;

    public NewImageUploadFileChunkResponseHandler(UploadFileChunk fileChunk) {
        super("pwg.images.upload", TAG);
        this.fileChunk = fileChunk;
    }

    @Override
    public RequestParams buildRequestParameters() {
        String sessionToken = "";
        if(PiwigoSessionDetails.isLoggedInWithSessionDetails()) {
            sessionToken = PiwigoSessionDetails.getInstance().getSessionToken();
        }
        //TODO this will give an unusual error if the user is not logged in.... better way?

        RequestParams params = new RequestParams();
        params.setAutoCloseInputStreams(true);
        params.setContentEncoding("form-data");
        params.setForceMultipartEntityContentType(true);
        params.put("method", getPiwigoMethod());
        params.put("chunk", String.valueOf(fileChunk.getChunkId()));
        params.put("chunks", String.valueOf(fileChunk.getChunkCount()));
        params.put("category", fileChunk.getUploadToAlbumId());
        params.put("file", fileChunk.getChunkData(), null, fileChunk.getMimeType(), true);
        params.put("name", fileChunk.getFilenameOnServer());
        params.put("pwg_token", sessionToken);
        return params;
    }

    @Override
    protected void postCall(boolean success) {
        super.postCall(success);
        if(success) {
            fileChunk.incrementUploadAttempts();
        }
    }

    @Override
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {
        ResourceItem uploadedResource = null;
        JSONObject result = rsp.optJSONObject("result");
        if(result != null) {
            long imageId = result.getLong("image_id");
            String imageName = result.getString("name");
            String thumbnailUrl = result.getString("src");
            JSONObject categeoryObj = result.getJSONObject("category");
            long albumId = categeoryObj.getLong("id");
            uploadedResource = new ResourceItem(imageId, imageName, null, null, thumbnailUrl);
            HashSet<Long> linkedAlbums = new HashSet<>(1);
            linkedAlbums.add(albumId);
            uploadedResource.setLinkedAlbums(linkedAlbums);
        }
        PiwigoResponseBufferingHandler.PiwigoUploadFileChunkResponse r = new PiwigoResponseBufferingHandler.PiwigoUploadFileChunkResponse(getMessageId(), getPiwigoMethod(), uploadedResource);
        storeResponse(r);
    }
}