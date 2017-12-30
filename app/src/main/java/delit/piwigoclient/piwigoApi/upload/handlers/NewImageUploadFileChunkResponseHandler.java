package delit.piwigoclient.piwigoApi.upload.handlers;

import org.json.JSONException;import com.google.gson.JsonElement;import com.google.gson.JsonObject;import com.google.gson.JsonArray;

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
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        ResourceItem uploadedResource = null;
        if(rsp != null && !rsp.isJsonNull()) {
            JsonObject result = rsp.getAsJsonObject();
            long imageId = result.get("image_id").getAsLong();
            String imageName = result.get("name").getAsString();
            String thumbnailUrl = result.get("src").getAsString();
            JsonObject categeoryObj = result.get("category").getAsJsonObject();
            long albumId = categeoryObj.get("id").getAsLong();
            uploadedResource = new ResourceItem(imageId, imageName, null, null, thumbnailUrl);
            HashSet<Long> linkedAlbums = new HashSet<>(1);
            linkedAlbums.add(albumId);
            uploadedResource.setLinkedAlbums(linkedAlbums);
        }
        PiwigoResponseBufferingHandler.PiwigoUploadFileChunkResponse r = new PiwigoResponseBufferingHandler.PiwigoUploadFileChunkResponse(getMessageId(), getPiwigoMethod(), uploadedResource);
        storeResponse(r);
    }
}