package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.util.Date;

import delit.libs.core.util.Logging;

/**
 * Created by gareth on 12/07/17.
 */
public class VideoResourceItem extends ResourceItem {

    private static final String TAG = "VidResItem";
    private static final long serialVersionUID = -2479502149387115863L;

    public VideoResourceItem(long id, String name, String description, Date dateCreated, Date lastAltered, String baseResourceUrl) {
        super(id, name, description, dateCreated, lastAltered, baseResourceUrl);
    }

    public VideoResourceItem(Parcel in) {
        super(in);
    }

    public int getType() {
        return VIDEO_RESOURCE_TYPE;
    }

    public void copyFrom(VideoResourceItem other, boolean copyParentage) {
        super.copyFrom(other, copyParentage);
    }

    public static final Parcelable.Creator<VideoResourceItem> CREATOR
            = new Parcelable.Creator<VideoResourceItem>() {
        public VideoResourceItem createFromParcel(Parcel in) {
            try {
                return new VideoResourceItem(in);
            } catch(RuntimeException e) {
                Logging.log(Log.ERROR, TAG, "Unable to create vid resource item from parcel: " + in.toString());
                throw e;
            }
        }

        public VideoResourceItem[] newArray(int size) {
            return new VideoResourceItem[size];
        }
    };
}
