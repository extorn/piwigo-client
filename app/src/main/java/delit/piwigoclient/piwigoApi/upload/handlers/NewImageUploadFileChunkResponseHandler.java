package delit.piwigoclient.piwigoApi.upload.handlers;

import com.crashlytics.android.Crashlytics;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.HashSet;

import delit.libs.http.RequestParams;
import delit.piwigoclient.model.UploadFileChunk;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoWsResponseHandler;

public class NewImageUploadFileChunkResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "UploadFileChunkRspHdlr";

    private final UploadFileChunk fileChunk;

    public NewImageUploadFileChunkResponseHandler(UploadFileChunk fileChunk) {
        super("pwg.images.upload", TAG);
        this.fileChunk = fileChunk;
    }

    @Override
    public RequestParams buildRequestParameters() {

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
        params.put("name", fileChunk.getFilenameOnServer().replace('/', '_'));
        params.put("pwg_token", getPwgSessionToken());
        return params;
    }

    @Override
    protected void postCall(boolean success) {
        super.postCall(success);
        if (success) {
            fileChunk.incrementUploadAttempts();
        }
    }

    @Override
    protected void logJsonSyntaxError(String responseBodyStr) {
        if(responseBodyStr != null && responseBodyStr.contains("forbidden file type")) {
            String fileType = fileChunk.getOriginalFile().isDirectory() ? "dir" : "file";
            String filePath = fileChunk.getOriginalFile().getAbsolutePath();
            Crashlytics.log(String.format("Json Syntax error while trying to upload %1$s : %2$s", fileType, filePath));
            super.logJsonSyntaxError(responseBodyStr + " (file: " + filePath + ")");
        } else {
            super.logJsonSyntaxError(responseBodyStr);
        }
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        ResourceItem uploadedResource = null;
        if (rsp != null && !rsp.isJsonNull()) {
            JsonObject result = rsp.getAsJsonObject();
            long imageId = result.get("image_id").getAsLong();
            String imageName = result.get("name").getAsString();
            String thumbnailUrl = result.get("src").getAsString();
            JsonObject categeoryObj = result.get("category").getAsJsonObject();
            long albumId = categeoryObj.get("id").getAsLong();
            uploadedResource = new ResourceItem(imageId, imageName, null, null, null, null);
            uploadedResource.setThumbnailUrl(thumbnailUrl);
            HashSet<Long> linkedAlbums = new HashSet<>(1);
            linkedAlbums.add(albumId);
            uploadedResource.setLinkedAlbums(linkedAlbums);
        }
        PiwigoUploadFileChunkResponse r = new PiwigoUploadFileChunkResponse(getMessageId(), getPiwigoMethod(), uploadedResource, isCached);
        storeResponse(r);
    }

    public static class PiwigoUploadFileChunkResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private final ResourceItem uploadedResource;

        public PiwigoUploadFileChunkResponse(long messageId, String piwigoMethod, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            uploadedResource = null;
        }

        public PiwigoUploadFileChunkResponse(long messageId, String piwigoMethod, ResourceItem uploadedResource, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.uploadedResource = uploadedResource;
        }

        public ResourceItem getUploadedResource() {
            return uploadedResource;
        }
    }
}