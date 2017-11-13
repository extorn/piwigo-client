package delit.piwigoclient.piwigoApi.handlers;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;

import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.PiwigoGalleryDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

/**
 * Created by gareth on 02/10/17.
 */

public class AlbumRemovePermissionsResponseHandler extends AlbumAlterPermissionsResponseHandler {

    private static final String TAG = "DelAlbmPermRspHdlr";
    private static final String PIWIGO_METHOD = "pwg.permissions.remove";

    public AlbumRemovePermissionsResponseHandler(CategoryItem gallery, HashSet<Long> groupIds, HashSet<Long> userIds) {
        // remove is always recursive so doesn't support the recursive flag (false means it won't be added to the ws call)
        super(gallery, groupIds, userIds, PIWIGO_METHOD, TAG, false);
    }

    public AlbumRemovePermissionsResponseHandler(PiwigoGalleryDetails gallery, HashSet<Long> groupIds, HashSet<Long> userIds) {
        // remove is always recursive so doesn't support the recursive flag (false means it won't be added to the ws call)
        super(gallery, groupIds, userIds, PIWIGO_METHOD, TAG, false);
    }

    @Override
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {
        PiwigoResponseBufferingHandler.PiwigoRemoveAlbumPermissionsResponse r = new PiwigoResponseBufferingHandler.PiwigoRemoveAlbumPermissionsResponse(getMessageId(), getPiwigoMethod(), getNewAllowedGroups(), getNewAllowedUsers());
        storeResponse(r);
    }
}
