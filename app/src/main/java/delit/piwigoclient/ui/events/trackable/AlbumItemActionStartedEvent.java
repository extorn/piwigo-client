package delit.piwigoclient.ui.events.trackable;

import android.os.Parcel;

import delit.libs.ui.util.ParcelUtils;
import delit.piwigoclient.model.piwigo.GalleryItem;

/**
 * Created by gareth on 12/06/17.
 */

public class AlbumItemActionStartedEvent extends TrackableRequestEvent {

    private final GalleryItem item;

    public AlbumItemActionStartedEvent(final GalleryItem item) {
        this.item = item;
    }

    public AlbumItemActionStartedEvent(Parcel in) {
        super(in);
        item = ParcelUtils.readParcelable(in, GalleryItem.class);
    }

    public GalleryItem getItem() {
        return item;
    }


    public static final Creator<AlbumItemActionStartedEvent> CREATOR = new Creator<AlbumItemActionStartedEvent>() {
        @Override
        public AlbumItemActionStartedEvent createFromParcel(Parcel in) {
            return new AlbumItemActionStartedEvent(in);
        }

        @Override
        public AlbumItemActionStartedEvent[] newArray(int size) {
            return new AlbumItemActionStartedEvent[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        ParcelUtils.writeParcelable(dest, item);
    }
}
