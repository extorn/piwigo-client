package delit.piwigoclient.ui.events.trackable;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by gareth on 02/10/17.
 */

public class TrackableRequestEvent implements Parcelable {
    private static final AtomicInteger actionIdGenerator = new AtomicInteger(0);
    private int actionId;

    public TrackableRequestEvent() {
        actionId = getNextEventId();
    }

    protected TrackableRequestEvent(Parcel in) {
        actionId = in.readInt();
    }

    public static final Creator<TrackableRequestEvent> CREATOR = new Creator<TrackableRequestEvent>() {
        @Override
        public TrackableRequestEvent createFromParcel(Parcel in) {
            return new TrackableRequestEvent(in);
        }

        @Override
        public TrackableRequestEvent[] newArray(int size) {
            return new TrackableRequestEvent[size];
        }
    };

    public synchronized static int getNextEventId() {
        int id = actionIdGenerator.incrementAndGet();
        if(id > Short.MAX_VALUE) {
            id = 0;
            actionIdGenerator.set(0);
        }
        return id;
    }

    public int getActionId() {
        return actionId;
    }

    protected void setActionId(int actionId) {
        this.actionId = actionId;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(actionId);
    }
}
