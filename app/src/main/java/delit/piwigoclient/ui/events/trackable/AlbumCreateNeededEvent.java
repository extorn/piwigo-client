package delit.piwigoclient.ui.events.trackable;

import android.os.Parcel;

import delit.libs.ui.util.ParcelUtils;
import delit.piwigoclient.model.piwigo.CategoryItemStub;

/**
 * Created by gareth on 13/06/17.
 */

public class AlbumCreateNeededEvent extends TrackableRequestEvent {

    private final CategoryItemStub parentAlbum;

    public AlbumCreateNeededEvent(CategoryItemStub parentAlbum) {
        this.parentAlbum = parentAlbum;
    }

    public AlbumCreateNeededEvent(Parcel in) {
        super(in);
        parentAlbum = ParcelUtils.readParcelable(in, CategoryItemStub.class);
    }

    public CategoryItemStub getParentAlbum() {
        return parentAlbum;
    }

    public static final Creator<AlbumCreateNeededEvent> CREATOR = new Creator<AlbumCreateNeededEvent>() {
        @Override
        public AlbumCreateNeededEvent createFromParcel(Parcel in) {
            return new AlbumCreateNeededEvent(in);
        }

        @Override
        public AlbumCreateNeededEvent[] newArray(int size) {
            return new AlbumCreateNeededEvent[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        ParcelUtils.writeParcelable(dest, parentAlbum);
    }
}
