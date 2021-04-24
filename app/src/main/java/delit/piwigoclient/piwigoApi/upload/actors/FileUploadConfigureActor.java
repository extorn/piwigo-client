package delit.piwigoclient.piwigoApi.upload.actors;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.Iterator;
import java.util.Set;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.upload.FileUploadDetails;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadFileAddToAlbumFailedResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadProgressUpdateResponse;

import static delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler.getNextMessageId;

public class FileUploadConfigureActor extends UploadActor {
    public FileUploadConfigureActor(@NonNull Context context, @NonNull UploadJob uploadJob, @NonNull ActorListener listener) {
        super(context, uploadJob, listener);
    }

    public void run(FileUploadDetails fud, Set<Long> allServerAlbumIds) {
        try {
            doConfigurationOfUploadedFileDetails(getUploadJob(), fud, allServerAlbumIds);
        } catch(RuntimeException e) {
            getListener().notifyListenersOfCustomErrorUploadingFile(getUploadJob(), fud.getFileUri(), false, e.getMessage());
        }
    }

    public void doConfigurationOfUploadedFileDetails(UploadJob thisUploadJob, FileUploadDetails fud, Set<Long> allServerAlbumIds) {
        Uri fileForUpload = fud.getFileToBeUploaded();
        if (!fud.isUploadCancelled()) {

            ResourceItem uploadedResource = fud.getServerResource();
            if (uploadedResource != null) {
                PiwigoResponseBufferingHandler.BaseResponse response = updateImageInfoAndPermissions(thisUploadJob, fud, allServerAlbumIds);

                if (response instanceof PiwigoResponseBufferingHandler.ErrorResponse) {

                    // notify the listener of the final error we received from the server
                    getListener().recordAndPostNewResponse(thisUploadJob, new PiwigoUploadFileAddToAlbumFailedResponse(getNextMessageId(), fileForUpload, false, response));

                } else {
                    fud.setStatusConfigured();
                    getListener().recordAndPostNewResponse(thisUploadJob, new PiwigoUploadProgressUpdateResponse(getNextMessageId(), fud.getFileUri(), thisUploadJob.getFileUploadDetails(fud.getFileUri()).getOverallUploadProgress()));
                }
            } else {
                fud.setProcessingFailed();
                getListener().notifyListenersOfCustomErrorUploadingFile(thisUploadJob, fud.getFileUri(), false, getContext().getString(R.string.upload_error_server_resource_not_yet_retrieved));
            }
        } else {
            fud.setStatusRequiresDelete();
        }
    }

    private PiwigoResponseBufferingHandler.BaseResponse updateImageInfoAndPermissions(UploadJob thisUploadJob, FileUploadDetails fud, Set<Long> allServerAlbumIds) {
        ResourceItem uploadedResource = fud.getServerResource();
        uploadedResource.getLinkedAlbums().add(thisUploadJob.getUploadToCategory().getId());
        if (thisUploadJob.getTemporaryUploadAlbumId() > 0) {
            uploadedResource.getLinkedAlbums().remove(thisUploadJob.getTemporaryUploadAlbumId());
        }
        // Don't update the tags because we aren't altering this aspect of the the image during upload and it (could) cause problems
        ImageUpdateInfoResponseHandler<ResourceItem> imageInfoUpdateHandler = new ImageUpdateInfoResponseHandler<>(uploadedResource, false);


        imageInfoUpdateHandler.setFilename(fud.getFilename(getContext()));
        getServerCaller().invokeWithRetries(thisUploadJob, fud, imageInfoUpdateHandler, 2);
        if (!imageInfoUpdateHandler.isSuccess()) {
            Iterator<Long> iter = uploadedResource.getLinkedAlbums().iterator();
            boolean ghostAlbumRemovedFromImage = false;
            while (iter.hasNext()) {
                Long albumId = iter.next();
                if (!allServerAlbumIds.contains(albumId)) {
                    ghostAlbumRemovedFromImage = true;
                    //FIXME Report general user message
                    iter.remove();
                }
            }
            if (ghostAlbumRemovedFromImage) {
                // retry.
                getServerCaller().invokeWithRetries(thisUploadJob, fud, imageInfoUpdateHandler, 2);
            }
        }
        return imageInfoUpdateHandler.getResponse();
    }
}
