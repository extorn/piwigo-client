package delit.piwigoclient.ui.events;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

import delit.libs.ui.util.ParcelUtils;

public class DownloadFileRequestEvent implements Parcelable {
    private final boolean shareDownloadedWithAppSelector;
    private final ArrayList<FileDetails> details = new ArrayList<>();

    public FileDetails getNextFileDetailToDownload() {
        for(FileDetails detail : details) {
            if(!detail.isDownloaded()) {
                return detail;
            }
        }
        return null;
    }

    public void markDownloaded(String url, Uri downloadedFile) {
        for(FileDetails detail : details) {
            if(detail.getRemoteUri().equals(url)) {
                detail.setDownloadedFile(downloadedFile);
                break; // don't check the rest.
            }
        }
    }

    public List<FileDetails> getFileDetails() {
        return details;
    }

    public static class FileDetails implements Parcelable {
        private final String resourceName;
        private final String remoteUri;
        private final String outputFilename;
        private final Uri localFileToCopy;
        private Uri downloadedFile;

        public Uri getLocalFileToCopy() {
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

        public void setDownloadedFile(Uri downloadedFile) {
            this.downloadedFile = downloadedFile;
        }

        public Uri getDownloadedFile() {
            return downloadedFile;
        }

        public boolean isDownloaded() {
            return downloadedFile != null;
        }

        public static final Creator<FileDetails> CREATOR = new Creator<FileDetails>() {
            @Override
            public FileDetails createFromParcel(Parcel in) {
                return new FileDetails(in);
            }

            @Override
            public FileDetails[] newArray(int size) {
                return new FileDetails[size];
            }

        };

        public FileDetails(String resourceName, String remoteUri, String outputFilename, Uri localFileToCopy) {
            this.resourceName = resourceName;
            this.remoteUri = remoteUri;
            this.outputFilename = outputFilename;
            this.localFileToCopy = localFileToCopy;
        }

        public FileDetails(Parcel in) {
            resourceName = ParcelUtils.readString(in);
            remoteUri = ParcelUtils.readString(in);
            outputFilename = ParcelUtils.readString(in);
            localFileToCopy = ParcelUtils.readParcelable(in, Uri.class);
            downloadedFile = ParcelUtils.readParcelable(in, Uri.class);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeValue(resourceName);
            dest.writeValue(remoteUri);
            dest.writeValue(outputFilename);
            ParcelUtils.writeParcelable(dest, localFileToCopy);
            ParcelUtils.writeParcelable(dest, downloadedFile);
        }

        @Override
        public int describeContents() {
            return 0;
        }
    }

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

    public void addFileDetail(String resourceName, String remoteUri, String outputFilename) {
        details.add(new FileDetails(resourceName, remoteUri, outputFilename, null));
    }

    public void addFileDetail(String resourceName, String remoteUri, String outputFilename, Uri preDownloadedFile) {
        details.add(new FileDetails(resourceName, remoteUri, outputFilename, preDownloadedFile));
    }

    public DownloadFileRequestEvent(boolean shareDownloadedWithAppSelector) {
        this.shareDownloadedWithAppSelector = shareDownloadedWithAppSelector;
    }

    public DownloadFileRequestEvent(Parcel in) {
        shareDownloadedWithAppSelector = ParcelUtils.readBool(in);
        requestId = in.readLong();
        ParcelUtils.readArrayList(in, FileDetails.class.getClassLoader(),details);
    }

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
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
        dest.writeLong(requestId);
        ParcelUtils.writeArrayList(dest, details);
    }
}
