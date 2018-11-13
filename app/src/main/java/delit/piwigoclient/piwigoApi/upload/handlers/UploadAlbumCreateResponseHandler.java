package delit.piwigoclient.piwigoApi.upload.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.security.SecureRandom;

import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoWsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumCreateResponseHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class UploadAlbumCreateResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "CreateGalleryRspHdlr";
    private static final SecureRandom random = new SecureRandom();
    private final long parentAlbumId;

    public UploadAlbumCreateResponseHandler(long parentAlbumId) {
        super("pwg.categories.add", TAG);
        this.parentAlbumId = parentAlbumId;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("parent", parentAlbumId);
        params.put("name", "uploads-" + Math.abs(random.nextInt()));
        params.put("comment", "PiwigoClient - uploads in progress");
        params.put("visible", "false");
        params.put("status", "private");
        params.put("commentable", "false");
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {

        JsonObject result = rsp.getAsJsonObject();
        long newAlbumnId = result.get("id").getAsLong();
        AlbumCreateResponseHandler.PiwigoAlbumCreatedResponse r = new AlbumCreateResponseHandler.PiwigoAlbumCreatedResponse(getMessageId(), getPiwigoMethod(), newAlbumnId, isCached);
        storeResponse(r);
    }
}