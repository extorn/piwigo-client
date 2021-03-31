package delit.piwigoclient.ui.events;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by gareth on 21/04/18.
 */

public class TagContentAlteredEvent implements Parcelable {
    private final long id;
    private final int contentChange;

    public TagContentAlteredEvent(Parcel in) {
        id = in.readLong();
        contentChange = in.readInt();
    }

    public TagContentAlteredEvent(long id, int contentChange) {
        this.id = id;
        this.contentChange = contentChange;
    }

    public static final Creator<TagContentAlteredEvent> CREATOR = new Creator<TagContentAlteredEvent>() {
        @Override
        public TagContentAlteredEvent createFromParcel(Parcel in) {
            return new TagContentAlteredEvent(in);
        }

        @Override
        public TagContentAlteredEvent[] newArray(int size) {
            return new TagContentAlteredEvent[size];
        }
    };

    public int getContentChange() {
        return contentChange;
    }

    public long getId() {
        return id;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeInt(contentChange);
    }
}
