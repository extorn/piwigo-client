package delit.piwigoclient.piwigoApi.upload.handlers;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.SecureRandom;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoWsResponseHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class UploadAlbumCreateResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "CreateGalleryRspHdlr";
    private long parentAlbumId;
    private static final SecureRandom random = new SecureRandom();

    public UploadAlbumCreateResponseHandler(long parentAlbumId) {
        super("pwg.categories.add", TAG);
        this.parentAlbumId = parentAlbumId;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("parent", parentAlbumId);
        params.put("name", "uploads-"+Math.abs(random.nextInt()));
        params.put("comment", "PiwigoClient - uploads in progress");
        params.put("visible", "false");
        params.put("status", "private");
        params.put("commentable", "false");
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {


        JSONObject galleryCreateResponse = rsp.getJSONObject("result");
        long newAlbumnId = galleryCreateResponse.getLong("id");

        PiwigoResponseBufferingHandler.PiwigoAlbumCreatedResponse r = new PiwigoResponseBufferingHandler.PiwigoAlbumCreatedResponse(getMessageId(), getPiwigoMethod(), newAlbumnId);
        storeResponse(r);
    }
}