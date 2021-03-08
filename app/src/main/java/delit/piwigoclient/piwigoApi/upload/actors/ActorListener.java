package delit.piwigoclient.piwigoApi.upload.actors;

import android.net.Uri;

import java.util.Date;

import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.upload.FileUploadDetails;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.piwigoApi.upload.messages.MessageForUserResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoCleanupPostUploadFailedResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoPrepareUploadFailedResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoStartUploadFileResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadFileAddToAlbumFailedResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadFileChunkFailedResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadFileFilesExistAlreadyResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadFileJobCompleteResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadFileLocalErrorResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadProgressUpdateResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadUnexpectedLocalErrorResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoVideoCompressionProgressUpdateResponse;

import static delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler.getNextMessageId;

public abstract class ActorListener {

    private final UploadNotificationManager uploadNotificationManager;
    private final UploadJob thisUploadJob;

    public ActorListener(UploadJob uploadJob, UploadNotificationManager uploadNotificationManager) {
        this.thisUploadJob = uploadJob;
        this.uploadNotificationManager = uploadNotificationManager;
    }

    public UploadNotificationManager getUploadNotificationManager() {
        return uploadNotificationManager;
    }

    public void reportForUser(String msgStr) {
        MessageForUserResponse msg = new MessageForUserResponse(thisUploadJob.getJobId(), msgStr);
        postNewResponse(msg);
    }

    public abstract void postNewResponse(long jobId, PiwigoResponseBufferingHandler.Response response);

    protected void postNewResponse(MessageForUserResponse msg) {
        recordAndPostNewResponse(thisUploadJob, msg);
    }

    public void updateNotificationProgressText(int uploadProgress) {
        uploadNotificationManager.updateNotificationText(R.string.notification_message_upload_service, uploadProgress);
    }

    public void recordAndPostNewResponse(UploadJob thisUploadJob, PiwigoResponseBufferingHandler.Response response) {
        if (!(response instanceof PiwigoUploadProgressUpdateResponse
                || response instanceof PiwigoVideoCompressionProgressUpdateResponse
                || response instanceof PiwigoStartUploadFileResponse
                || response instanceof PiwigoUploadFileFilesExistAlreadyResponse
                || response instanceof PiwigoUploadFileJobCompleteResponse)) {
            if (response instanceof PiwigoPrepareUploadFailedResponse) {
                PiwigoResponseBufferingHandler.Response error = ((PiwigoPrepareUploadFailedResponse) response).getError();
                String errorMsg = null;
                if (error instanceof PiwigoResponseBufferingHandler.CustomErrorResponse) {
                    errorMsg = ((PiwigoResponseBufferingHandler.CustomErrorResponse) error).getErrorMessage();
                }
                if (errorMsg != null) {
                    thisUploadJob.recordError("PiwigoPrepareUpload:Failed : " + errorMsg);
                } else {
                    thisUploadJob.recordError("PiwigoPrepareUpload:Failed");
                }

            }
            if (response instanceof PiwigoCleanupPostUploadFailedResponse) {
                thisUploadJob.recordError("PiwigoCleanupPostUpload:Failed");
            }
            if (response instanceof PiwigoUploadFileAddToAlbumFailedResponse) {
                FileUploadDetails fud = thisUploadJob.getFileUploadDetails(((PiwigoUploadFileAddToAlbumFailedResponse) response).getFileForUpload());
                fud.addError( "PiwigoUploadFileAddToAlbum:Failed : " + ((PiwigoUploadFileAddToAlbumFailedResponse) response).getFileForUpload().getPath());
            }
            if (response instanceof PiwigoUploadFileChunkFailedResponse) {
                FileUploadDetails fud = thisUploadJob.getFileUploadDetails(((PiwigoUploadFileChunkFailedResponse) response).getFileForUpload());
                fud.addError( "PiwigoUploadFileChunk:Failed : " + ((PiwigoUploadFileChunkFailedResponse) response).getFileForUpload().toString());
            }
            if (response instanceof PiwigoUploadFileLocalErrorResponse) {
                String error = ((PiwigoUploadFileLocalErrorResponse) response).getError().getMessage();
                FileUploadDetails fud = thisUploadJob.getFileUploadDetails(((PiwigoUploadFileLocalErrorResponse) response).getFileForUpload());
                fud.addError( "PiwigoUploadFileLocalError: " + error);
            } else if (response instanceof PiwigoUploadUnexpectedLocalErrorResponse) {
                // need else as this is extended by the previous exception
                String error = ((PiwigoUploadUnexpectedLocalErrorResponse) response).getError().getMessage();
                if(error == null) {
                    thisUploadJob.recordError("PiwigoUploadUnexpectedLocalError");
                } else {
                    thisUploadJob.recordError("PiwigoUploadUnexpectedLocalError: " + error);
                }
            }
        }
        postNewResponse(thisUploadJob.getJobId(), response);
    }

    public void notifyListenersOfCustomErrorUploadingFile(UploadJob thisUploadJob, Uri uploadJobKey, boolean itemUploadCancelled, String errorMessage) {
        long jobId = thisUploadJob.getJobId();
        PiwigoResponseBufferingHandler.CustomErrorResponse errorResponse = new PiwigoResponseBufferingHandler.CustomErrorResponse(jobId, errorMessage);
        PiwigoUploadFileAddToAlbumFailedResponse r1 = new PiwigoUploadFileAddToAlbumFailedResponse(getNextMessageId(), uploadJobKey, itemUploadCancelled, errorResponse);
        thisUploadJob.getFileUploadDetails(uploadJobKey).addError( errorMessage);
        postNewResponse(jobId, r1);
    }

    public void doHandleUserCancelledUpload(UploadJob thisUploadJob, Uri fileForUploadUri) {
        FileUploadDetails fud = thisUploadJob.getFileUploadDetails(fileForUploadUri);
        if(fud.isFilePartiallyUploaded()) {
            fud.setStatusRequiresDelete();
        }
    }
}
