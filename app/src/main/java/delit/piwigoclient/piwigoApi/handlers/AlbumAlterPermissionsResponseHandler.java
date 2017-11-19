package delit.piwigoclient.piwigoApi.handlers;

import java.util.HashSet;

import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.PiwigoGalleryDetails;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public abstract class AlbumAlterPermissionsResponseHandler extends AbstractPiwigoWsResponseHandler {

    private final Long galleryId;
    private final HashSet<Long> newAllowedUsers;
    private final HashSet<Long> newAllowedGroups;
    private final boolean recursive;

    public AlbumAlterPermissionsResponseHandler(CategoryItem gallery, HashSet<Long> groupIds, HashSet<Long> userIds, String piwigoMethod, String tag, boolean recursive) {
        super(piwigoMethod, tag);
        this.galleryId = gallery.getId();
        newAllowedUsers = userIds;
        newAllowedGroups = groupIds;
        this.recursive = recursive;
    }


    public AlbumAlterPermissionsResponseHandler(PiwigoGalleryDetails gallery, HashSet<Long> groupIds, HashSet<Long> userIds, String piwigoMethod, String tag, boolean recursive) {
        super(piwigoMethod, tag);
        this.galleryId = gallery.getGalleryId();
        newAllowedUsers = userIds;
        newAllowedGroups = groupIds;
        this.recursive = recursive;
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
        params.put("cat_id", galleryId.toString());
        if(newAllowedUsers != null) {
            for (Long id : newAllowedUsers) {
                params.add("1", id.toString());
            }
        }
        if(newAllowedGroups != null) {
            for (Long id : newAllowedGroups) {
                params.add("1", id.toString());
            }
        }
        if(recursive) {
            params.put("recursive", "true");
        }
        params.put("pwg_token", sessionToken);
        return params;
    }

    public HashSet<Long> getNewAllowedGroups() {
        return newAllowedGroups;
    }

    public HashSet<Long> getNewAllowedUsers() {
        return newAllowedUsers;
    }
}