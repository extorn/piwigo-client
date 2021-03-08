package delit.piwigoclient.piwigoApi.upload.actors;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import delit.libs.core.util.Logging;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.handlers.ImageDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.upload.FileUploadDetails;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.piwigoApi.upload.messages.FileUploadCancelledResponse;

import static delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler.getNextMessageId;

public class DeleteFromServerActor extends UploadActor {
    private static final String TAG = "DeleteFromServerActor";

    public DeleteFromServerActor(@NonNull Context context, @NonNull UploadJob uploadJob, @NonNull ActorListener listener) {
        super(context, uploadJob, listener);
    }

    public boolean run(FileUploadDetails fud, boolean allowRetry) {
        if(doDeleteUploadedResourceFromServer(getUploadJob(), fud.getServerResource())) {
            if(allowRetry) {
                fud.resetStatus();
                //FIXME what about the Overall progress ???
                //TODO notify user that the file bytes were deleted and upload must be started over
            } else {
                fud.setStatusDeleted();
                // notify the listener that upload has been cancelled for this file
                getListener().postNewResponse(getUploadJob().getJobId(), new FileUploadCancelledResponse(getNextMessageId(), fud.getFileUri()));
            }
            return true;
        } else {
            //TODO notify user the uploaded file couldn't be deleted - needs manual intervention to remove it. Will be handled on Retry?
            return false;
        }
    }

    private boolean doDeleteUploadedResourceFromServer(UploadJob uploadJob, ResourceItem uploadedResource) {

        if (uploadedResource == null) {
            Logging.log(Log.WARN, TAG, "cannot delete uploaded resource from server, as we are missing a reference to it (presumably upload has not been started)!");
            return true;
        }
        if (PiwigoSessionDetails.isAdminUser(uploadJob.getConnectionPrefs())) {
            ImageDeleteResponseHandler<ResourceItem> imageDeleteHandler = new ImageDeleteResponseHandler<>(uploadedResource);
            getServerCaller().invokeWithRetries(uploadJob, imageDeleteHandler, 2);
            return imageDeleteHandler.isSuccess();
        } else {
            // community plugin... can't delete files... have to pretend we did...
            return true;
        }
    }
}
