package delit.piwigoclient.piwigoApi.upload.messages;

import android.net.Uri;

import java.util.ArrayList;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class PiwigoUploadFileFilesExistAlreadyResponse extends PiwigoResponseBufferingHandler.BaseResponse {

    private final ArrayList<Uri> existingFiles;

    public PiwigoUploadFileFilesExistAlreadyResponse(long jobId, ArrayList<Uri> existingFiles) {
        super(jobId, false);
        this.existingFiles = existingFiles;
    }

    public ArrayList<Uri> getExistingFiles() {
        return existingFiles;
    }

    public long getJobId() {
        return getMessageId();
    }
}
