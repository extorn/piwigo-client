package delit.piwigoclient.ui.events.trackable;

import android.os.Parcel;

public class AutoUploadJobViewRequestedEvent extends TrackableRequestEvent {
    private int jobId;

    public AutoUploadJobViewRequestedEvent(int jobId) {
        this.jobId = jobId;
    }

    public AutoUploadJobViewRequestedEvent(Parcel in) {
        super(in);
        jobId = in.readInt();
    }

    public int getJobId() {
        return jobId;
    }

    public static final Creator<AutoUploadJobViewRequestedEvent> CREATOR = new Creator<AutoUploadJobViewRequestedEvent>() {
        @Override
        public AutoUploadJobViewRequestedEvent createFromParcel(Parcel in) {
            return new AutoUploadJobViewRequestedEvent(in);
        }

        @Override
        public AutoUploadJobViewRequestedEvent[] newArray(int size) {
            return new AutoUploadJobViewRequestedEvent[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(jobId);
    }
}
