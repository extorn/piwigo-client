package delit.piwigoclient.piwigoApi.handlers;

import org.json.JSONException;
import org.json.JSONObject;

import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class AlbumDeleteResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "DeleteGalleryRspHdlr";
    private final long galleryId;

    public AlbumDeleteResponseHandler(long galleryId) {
        super("pwg.categories.delete", TAG);
        this.galleryId = galleryId;
    }

    @Override
    public RequestParams buildRequestParameters() {
        String sessionToken = "";
        if(PiwigoSessionDetails.isLoggedInWithSessionDetails()) {
            sessionToken = PiwigoSessionDetails.getInstance().getSessionToken();
        }
        //TODO this will give an unusual error if the user is not logged in.... better way?

        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("category_id", String.valueOf(galleryId));
        params.put("photo_deletion_mode", "delete_orphans");
        params.put("pwg_token", sessionToken);
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {
        PiwigoResponseBufferingHandler.PiwigoAlbumDeletedResponse r = new PiwigoResponseBufferingHandler.PiwigoAlbumDeletedResponse(getMessageId(), getPiwigoMethod(), galleryId);
        storeResponse(r);
    }

}