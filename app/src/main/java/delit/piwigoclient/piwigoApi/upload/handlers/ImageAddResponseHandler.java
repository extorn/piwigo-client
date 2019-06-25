package delit.piwigoclient.piwigoApi.upload.handlers;

import com.google.gson.JsonElement;

import org.json.JSONException;

import delit.libs.http.RequestParams;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoWsResponseHandler;

public class ImageAddResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "AddImageRspHdlr";
    private final String name;
    private final String checksum;
    private final Long albumId;
    private final int privacyLevel;

    public ImageAddResponseHandler(String name, String checksum, Long albumId, int privacyLevel) {
        super("pwg.images.add", TAG);
        this.name = name;
        this.checksum = checksum;
        this.albumId = albumId;
        this.privacyLevel = privacyLevel;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("name", name);
        params.put("original_sum", checksum);
        params.put("categories", String.valueOf(albumId));
        params.put("level", String.valueOf(privacyLevel));
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        PiwigoAddImageResponse r = new PiwigoAddImageResponse(getMessageId(), getPiwigoMethod(), isCached);
        storeResponse(r);
    }

    public static class PiwigoAddImageResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {

        public PiwigoAddImageResponse(long messageId, String piwigoMethod, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
        }
    }
}