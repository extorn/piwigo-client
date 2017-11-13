package delit.piwigoclient.ui.events.trackable;

import java.util.ArrayList;
import java.util.HashSet;

import delit.piwigoclient.model.piwigo.CategoryItemStub;

/**
 * Created by gareth on 23/06/17.
 */

public class AlbumPermissionsSelectionNeededEvent extends TrackableRequestEvent {

    private final boolean allowEdit;
    private ArrayList<CategoryItemStub> availableAlbums;
    private HashSet<Long> directAlbumPermissions;
    private HashSet<Long> indirectAlbumPermissions;

    public AlbumPermissionsSelectionNeededEvent(ArrayList<CategoryItemStub> availableAlbums, HashSet<Long> directAlbumPermissions, boolean allowEdit) {
        this(availableAlbums, directAlbumPermissions, null, allowEdit);
    }

    public AlbumPermissionsSelectionNeededEvent(ArrayList<CategoryItemStub> availableAlbums, HashSet<Long> directAlbumPermissions, HashSet<Long> indirectAlbumPermissions, boolean allowEdit) {
        this.allowEdit = allowEdit;
        this.availableAlbums = availableAlbums;
        this.directAlbumPermissions = directAlbumPermissions;
        this.indirectAlbumPermissions = indirectAlbumPermissions;
    }

    public boolean isAllowEdit() {
        return allowEdit;
    }

    public ArrayList<CategoryItemStub> getAvailableAlbums() {
        return availableAlbums;
    }

    public HashSet<Long> getDirectAlbumPermissions() {
        return directAlbumPermissions;
    }

    public HashSet<Long> getIndirectAlbumPermissions() {
        return indirectAlbumPermissions;
    }
}
