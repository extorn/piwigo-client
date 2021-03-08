package delit.piwigoclient.piwigoApi.upload.actors;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import delit.libs.util.IOUtils;
import delit.piwigoclient.piwigoApi.upload.FileUploadDetails;
import delit.piwigoclient.piwigoApi.upload.UploadJob;

public class JobCleanupActor extends UploadActor {

    private static final String TAG = "JobCleanupActor";

    public JobCleanupActor(@NonNull Context context, @NonNull UploadJob uploadJob, @NonNull ActorListener listener) {
        super(context, uploadJob, listener);
    }

    public void deleteSuccessfullyUploadedFilesFromDevice() {
        for(FileUploadDetails uploadDetails : getUploadJob().getFilesForUpload()) {
            if(uploadDetails.isSuccessfullyUploaded()) {
                if(uploadDetails.isDeleteAfterUpload()) {
                    DocumentFile docFile = IOUtils.getSingleDocFile(getContext(), uploadDetails.getFileUri());
                    if (docFile != null && docFile.exists()) {
                        if (!docFile.delete()) {
                            IOUtils.onFileDeleteFailed(TAG, docFile, "uploaded file");
                        }
                    }
                }
            }
        }
    }
}
