package delit.piwigoclient.piwigoApi.handlers;

import org.json.JSONException;
import org.json.JSONObject;

import delit.piwigoclient.model.piwigo.PiwigoGalleryDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class AlbumCreateResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "CreateGalleryRspHdlr";
    private PiwigoGalleryDetails newAlbum;

    public AlbumCreateResponseHandler(PiwigoGalleryDetails newAlbum) {
        super("pwg.categories.add", TAG);
        this.newAlbum = newAlbum;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("parent", String.valueOf(newAlbum.getParentGalleryId()));
        params.put("name", newAlbum.getGalleryName());
        params.put("comment", newAlbum.getGalleryDescription());
        params.put("visible", String.valueOf(true));
        params.put("status", newAlbum.isPrivate() ? "private" : "public");
        params.put("commentable", String.valueOf(newAlbum.isUserCommentsAllowed()));
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {


        JSONObject galleryCreateResponse = rsp.getJSONObject("result");
        long newAlbumnId = galleryCreateResponse.getLong("id");
        newAlbum.setGalleryId(newAlbumnId);

        PiwigoResponseBufferingHandler.PiwigoAlbumCreatedResponse r = new PiwigoResponseBufferingHandler.PiwigoAlbumCreatedResponse(getMessageId(), getPiwigoMethod(), newAlbum);
        storeResponse(r);
    }
}