package delit.piwigoclient.piwigoApi.upload.actors;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.Set;

import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.handlers.CommunityNotifyUploadCompleteResponseHandler;
import delit.piwigoclient.piwigoApi.upload.UploadJob;

public class CommunityUploadJobCompleteActor extends UploadActor {
    public CommunityUploadJobCompleteActor(@NonNull Context context, @NonNull UploadJob uploadJob, @NonNull ActorListener listener) {
        super(context, uploadJob, listener);
    }

    public void run() {
        updateCommunityPlugin(getUploadJob());
    }

    private void updateCommunityPlugin(UploadJob thisUploadJob) {
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(thisUploadJob.getConnectionPrefs());
        if (sessionDetails.isUseCommunityPlugin() && sessionDetails.isCommunityApiAvailable()) {
            Set<Long> ids = thisUploadJob.getIdsOfResourcesForFilesSuccessfullyUploaded();
            CommunityNotifyUploadCompleteResponseHandler hndlr = new CommunityNotifyUploadCompleteResponseHandler(ids, thisUploadJob.getUploadToCategory().getId());
            if (sessionDetails.isMethodAvailable(hndlr.getPiwigoMethod())) {
                getServerCaller().invokeWithRetries(thisUploadJob, hndlr, 2);
            }
        }
    }
}
