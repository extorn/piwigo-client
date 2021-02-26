package delit.piwigoclient.piwigoApi.upload.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.security.SecureRandom;

import delit.libs.http.RequestParams;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.PiwigoGalleryDetails;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoWsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumCreateResponseHandler;

public class UploadAlbumCreateResponseHandler extends AlbumCreateResponseHandler {

    private static final String TAG = "UplCreateAlbRspHdlr";
    private static final SecureRandom random = new SecureRandom();

    public UploadAlbumCreateResponseHandler(String parentAlbumName, long parentAlbumId) {
        super(buildGalleryDetails(parentAlbumName, parentAlbumId), false, TAG);
    }

    private static final PiwigoGalleryDetails buildGalleryDetails(String parentAlbumName, long parentAlbumId) {
        CategoryItemStub parent = new CategoryItemStub(parentAlbumName, parentAlbumId);
        return new PiwigoGalleryDetails(parent, null, "uploads-" + Math.abs(random.nextInt()), "PiwigoClient - uploads in progress", false, true);
    }
}