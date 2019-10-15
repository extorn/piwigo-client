package delit.piwigoclient.ui.events;

import java.io.File;

public class DownloadFileRequestEvent {
    private final boolean shareDownloadedWithAppSelector;
    private final String resourceName;
    private final String remoteUri;
    private final String outputFilename;
    private final File localFileToCopy;

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
}
