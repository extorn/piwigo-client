package delit.piwigoclient.ui.events.trackable;

import android.os.Parcel;

import java.util.ArrayList;
import java.util.HashSet;

import delit.libs.ui.util.ParcelUtils;
import delit.piwigoclient.model.piwigo.CategoryItemStub;

/**
 * Created by gareth on 23/06/17.
 */

public class AlbumPermissionsSelectionNeededEvent extends TrackableRequestEvent {

    private final boolean allowEdit;
    private final ArrayList<CategoryItemStub> availableAlbums;
    private final HashSet<Long> directAlbumPermissions;
    private final HashSet<Long> indirectAlbumPermissions;

    public AlbumPermissionsSelectionNeededEvent(ArrayList<CategoryItemStub> availableAlbums, HashSet<Long> directAlbumPermissions, boolean allowEdit) {
        this(availableAlbums, directAlbumPermissions, null, allowEdit);
    }

    public AlbumPermissionsSelectionNeededEvent(ArrayList<CategoryItemStub> availableAlbums, HashSet<Long> directAlbumPermissions, HashSet<Long> indirectAlbumPermissions, boolean allowEdit) {
        this.allowEdit = allowEdit;
        this.availableAlbums = availableAlbums;
        this.directAlbumPermissions = directAlbumPermissions;
        this.indirectAlbumPermissions = indirectAlbumPermissions;
    }

    public AlbumPermissionsSelectionNeededEvent(Parcel in) {
        super(in);
        allowEdit = ParcelUtils.readBool(in);
        availableAlbums = ParcelUtils.readArrayList(in, CategoryItemStub.class.getClassLoader());
        directAlbumPermissions = ParcelUtils.readLongSet(in);
        indirectAlbumPermissions = ParcelUtils.readLongSet(in);
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

    public static final Creator<AlbumPermissionsSelectionNeededEvent> CREATOR = new Creator<AlbumPermissionsSelectionNeededEvent>() {
        @Override
        public AlbumPermissionsSelectionNeededEvent createFromParcel(Parcel in) {
            return new AlbumPermissionsSelectionNeededEvent(in);
        }

        @Override
        public AlbumPermissionsSelectionNeededEvent[] newArray(int size) {
            return new AlbumPermissionsSelectionNeededEvent[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        ParcelUtils.writeBool(dest, allowEdit);
        ParcelUtils.writeArrayList(dest, availableAlbums);
        ParcelUtils.writeLongSet(dest, directAlbumPermissions);
        ParcelUtils.writeLongSet(dest, indirectAlbumPermissions);
    }
}
