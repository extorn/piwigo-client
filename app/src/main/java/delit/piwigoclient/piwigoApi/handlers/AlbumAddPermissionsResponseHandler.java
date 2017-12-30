package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;

import org.json.JSONException;

import java.util.HashSet;

import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.PiwigoGalleryDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

/**
 * Created by gareth on 02/10/17.
 */

public class AlbumAddPermissionsResponseHandler extends AlbumAlterPermissionsResponseHandler {

    private static final String TAG = "AddAlbmPermRspHdlr";
    private static final String PIWIGO_METHOD = "pwg.permissions.add";

    public AlbumAddPermissionsResponseHandler(CategoryItem gallery, HashSet<Long> groupIds, HashSet<Long> userIds, boolean recursive) {
        super(gallery, groupIds, userIds, PIWIGO_METHOD, TAG, recursive);
    }

    public AlbumAddPermissionsResponseHandler(PiwigoGalleryDetails gallery, HashSet<Long> groupIds, HashSet<Long> userIds, boolean recursive) {
        super(gallery, groupIds, userIds, PIWIGO_METHOD, TAG, recursive);
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        PiwigoResponseBufferingHandler.PiwigoAddAlbumPermissionsResponse r = new PiwigoResponseBufferingHandler.PiwigoAddAlbumPermissionsResponse(getMessageId(), getPiwigoMethod(), getNewAllowedGroups(), getNewAllowedUsers());
        storeResponse(r);
    }


}
