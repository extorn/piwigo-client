package delit.piwigoclient.ui.events.trackable;

import android.os.Parcel;

public class ViewAutoUploadJobEvent extends TrackableRequestEvent {
    private int jobId;

    public ViewAutoUploadJobEvent(int jobId) {
        this.jobId = jobId;
    }

    public ViewAutoUploadJobEvent(Parcel in) {
        super(in);
        jobId = in.readInt();
    }

    public int getJobId() {
        return jobId;
    }

    public static final Creator<ViewAutoUploadJobEvent> CREATOR = new Creator<ViewAutoUploadJobEvent>() {
        @Override
        public ViewAutoUploadJobEvent createFromParcel(Parcel in) {
            return new ViewAutoUploadJobEvent(in);
        }

        @Override
        public ViewAutoUploadJobEvent[] newArray(int size) {
            return new ViewAutoUploadJobEvent[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(jobId);
    }
}
