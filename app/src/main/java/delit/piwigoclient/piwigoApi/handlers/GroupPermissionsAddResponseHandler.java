package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.ArrayList;

import delit.libs.http.RequestParams;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class GroupPermissionsAddResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "addGrpPermRspHdlr";
    private final ArrayList<Long> newAlbumsAllowedAccessTo;
    private final long groupId;

    public GroupPermissionsAddResponseHandler(long groupId, ArrayList<Long> newAlbumsAllowedAccessTo) {
        super("pwg.permissions.add", TAG);
        this.groupId = groupId;
        this.newAlbumsAllowedAccessTo = newAlbumsAllowedAccessTo;
        if (newAlbumsAllowedAccessTo == null || newAlbumsAllowedAccessTo.size() == 0) {
            throw new IllegalArgumentException("Group must be being given access to at least one album");
        }
    }

    @Override
    public RequestParams buildRequestParameters() {

        //TODO this will give an unusual error if the user is not logged in.... better way?

        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("group_id", String.valueOf(groupId));
        for (Long albumId : newAlbumsAllowedAccessTo) {
            params.add("cat_id[]", String.valueOf(albumId));
        }
        params.put("pwg_token", getPwgSessionToken());
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();

        PiwigoGroupPermissionsAddedResponse r = new PiwigoGroupPermissionsAddedResponse(getMessageId(), getPiwigoMethod(), groupId, newAlbumsAllowedAccessTo, isCached);
        storeResponse(r);
    }

    public static class PiwigoGroupPermissionsAddedResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private final ArrayList<Long> albumsForWhichPermissionAdded;
        private final long groupId;

        public PiwigoGroupPermissionsAddedResponse(long messageId, String piwigoMethod, long groupId, ArrayList<Long> albumsForWhichPermissionAdded, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.groupId = groupId;
            this.albumsForWhichPermissionAdded = albumsForWhichPermissionAdded;
        }

        public long getGroupId() {
            return groupId;
        }

        public ArrayList<Long> getAlbumsForWhichPermissionAdded() {
            return albumsForWhichPermissionAdded;
        }
    }
}