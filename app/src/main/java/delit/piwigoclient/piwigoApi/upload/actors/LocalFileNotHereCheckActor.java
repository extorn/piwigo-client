package delit.piwigoclient.piwigoApi.upload.actors;

import android.content.Context;

import androidx.annotation.NonNull;

import delit.libs.util.IOUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.upload.FileUploadDetails;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadFileLocalErrorResponse;

import static delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler.getNextMessageId;

public class LocalFileNotHereCheckActor extends UploadActor {
    public LocalFileNotHereCheckActor(@NonNull Context context, @NonNull UploadJob uploadJob, @NonNull ActorListener listener) {
        super(context, uploadJob, listener);
    }

    /**
     *
     * @param fud
     * @return true if file available
     */
    public void runCheck(FileUploadDetails fud) {

        try {
            if (!IOUtils.exists(getContext(), fud.getFileToBeUploaded())) {
                fud.setProcessingFailed();
                // notify the listener of the final error we received from the server
                String filename = IOUtils.getFilename(getContext(), fud.getFileUri());
                String errorMsg = getContext().getString(R.string.alert_error_upload_file_no_longer_available_message_pattern, filename, fud.getFileToBeUploaded().getPath());
                getListener().notifyListenersOfCustomErrorUploadingFile(getUploadJob(), fud.getFileUri(), true, errorMsg);
            }
        } catch (SecurityException e) {
            fud.setProcessingFailed();
            getListener().recordAndPostNewResponse(getUploadJob(), new PiwigoUploadFileLocalErrorResponse(getNextMessageId(), fud.getFileUri(), true, e));
        }
    }
}
