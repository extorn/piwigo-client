package delit.piwigoclient.ui.events;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.File;

import delit.libs.ui.util.ParcelUtils;

public class DownloadFileRequestEvent implements Parcelable {
    private final boolean shareDownloadedWithAppSelector;
    private final String resourceName;
    private final String remoteUri;
    private final String outputFilename;
    private final File localFileToCopy;
    public static final Creator<DownloadFileRequestEvent> CREATOR = new Creator<DownloadFileRequestEvent>() {
        @Override
        public DownloadFileRequestEvent createFromParcel(Parcel in) {
            return new DownloadFileRequestEvent(in);
        }

        @Override
        public DownloadFileRequestEvent[] newArray(int size) {
            return new DownloadFileRequestEvent[size];
        }
    };
    private long requestId;

    public DownloadFileRequestEvent(String resourceName, String remoteUri, String outputFilename, boolean shareDownloadedWithAppSelector) {
        this.resourceName = resourceName;
        this.remoteUri = remoteUri;
        this.outputFilename = outputFilename;
        this.shareDownloadedWithAppSelector = shareDownloadedWithAppSelector;
        this.localFileToCopy = null;
    }

    public DownloadFileRequestEvent(String name, String remoteUri, File localFileToCopy, String outputFilename) {
        this.resourceName = name;
        this.localFileToCopy = localFileToCopy;
        this.outputFilename = outputFilename;
        this.shareDownloadedWithAppSelector = false;
        this.remoteUri = remoteUri;
    }

    public DownloadFileRequestEvent(Parcel in) {
        shareDownloadedWithAppSelector = ParcelUtils.readBool(in);
        resourceName = ParcelUtils.readString(in);
        remoteUri = ParcelUtils.readString(in);
        outputFilename = ParcelUtils.readString(in);
        requestId = in.readLong();
        localFileToCopy = ParcelUtils.readFile(in);
    }

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    public File getLocalFileToCopy() {
        return localFileToCopy;
    }

    public String getResourceName() {
        return resourceName;
    }

    public String getRemoteUri() {
        return remoteUri;
    }

    public String getOutputFilename() {
        return outputFilename;
    }

    public boolean isShareDownloadedWithAppSelector() {
        return shareDownloadedWithAppSelector;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * private final boolean shareDownloadedWithAppSelector;
     * private final String resourceName;
     * private final String remoteUri;
     * private final String outputFilename;
     * private final File localFileToCopy;
     * private long requestId;
     */

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        ParcelUtils.writeBool(dest, shareDownloadedWithAppSelector);
        dest.writeValue(resourceName);
        dest.writeValue(remoteUri);
        dest.writeValue(outputFilename);
        dest.writeLong(requestId);
        ParcelUtils.writeFile(dest, localFileToCopy);
    }
}
