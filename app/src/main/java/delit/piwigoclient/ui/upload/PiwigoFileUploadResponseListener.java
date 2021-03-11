package delit.piwigoclient.ui.upload;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import delit.libs.core.util.Logging;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.piwigoApi.upload.messages.FileUploadCancelledResponse;
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
import delit.piwigoclient.ui.common.UIHelper;

public abstract class PiwigoFileUploadResponseListener<P extends UIHelper<P,T>, T>  extends BasicPiwigoResponseListener<P,T> {

    private final Context context;

    public PiwigoFileUploadResponseListener(@NonNull Context context) {
        this.context = context.getApplicationContext();// use app context so it doesn't matter if it leaks.
    }

    @Override
    public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
        try {
            if (response instanceof PiwigoUploadFileJobCompleteResponse) {
                onUploadComplete(context, ((PiwigoUploadFileJobCompleteResponse) response).getJob());
            } else if (response instanceof PiwigoPrepareUploadFailedResponse) {
                onPrepareUploadFailed(context, (PiwigoPrepareUploadFailedResponse) response);
            } else if (response instanceof PiwigoCleanupPostUploadFailedResponse) {
                onCleanupPostUploadFailed(context, (PiwigoCleanupPostUploadFailedResponse) response);
            } else if (response instanceof PiwigoUploadProgressUpdateResponse) {
                onFileUploadProgressUpdate(context, (PiwigoUploadProgressUpdateResponse) response);
            } else if (response instanceof PiwigoVideoCompressionProgressUpdateResponse) {
                onFileCompressionProgressUpdate(context, (PiwigoVideoCompressionProgressUpdateResponse) response);
            } else if (response instanceof PiwigoUploadFileLocalErrorResponse) {
                onLocalFileError(context, (PiwigoUploadFileLocalErrorResponse) response);
            } else if (response instanceof PiwigoUploadUnexpectedLocalErrorResponse) {
                // this is after the localFileError as that extends this exception.
                onLocalUnexpectedError(context, (PiwigoUploadUnexpectedLocalErrorResponse) response);
            } else if (response instanceof PiwigoUploadFileFilesExistAlreadyResponse) {
                onFilesSelectedForUploadAlreadyExistOnServer(context, (PiwigoUploadFileFilesExistAlreadyResponse) response);
            } else if (response instanceof PiwigoUploadFileChunkFailedResponse) {
                onChunkUploadFailed(context, (PiwigoUploadFileChunkFailedResponse) response);
            } else if (response instanceof PiwigoUploadFileAddToAlbumFailedResponse) {
                onAddUploadedFileToAlbumFailure(context, (PiwigoUploadFileAddToAlbumFailedResponse) response);
            } else if (response instanceof FileUploadCancelledResponse) {
                onRequestedFileUploadCancelComplete(context, ((FileUploadCancelledResponse) response).getCancelledFile());
            } else if (response instanceof PiwigoStartUploadFileResponse) {
                // ignore for now.
            } else if (response instanceof MessageForUserResponse) {
                onMessageForUser(context, (MessageForUserResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.ErrorResponse) {
                onErrorResponse((PiwigoResponseBufferingHandler.ErrorResponse) response);
            }
        } catch(RuntimeException e) {
            Logging.recordException(e);
            Logging.waitForExceptionToBeSent();
        }
    }

    protected abstract void onMessageForUser(@NonNull Context context, MessageForUserResponse response);

    protected abstract void onErrorResponse(PiwigoResponseBufferingHandler.ErrorResponse response);

    protected abstract void onRequestedFileUploadCancelComplete(@NonNull Context context, Uri cancelledFile);

    protected abstract void onAddUploadedFileToAlbumFailure(@NonNull Context context, PiwigoUploadFileAddToAlbumFailedResponse response);

    protected abstract void onChunkUploadFailed(@NonNull Context context, PiwigoUploadFileChunkFailedResponse response);

    protected abstract void onFilesSelectedForUploadAlreadyExistOnServer(@NonNull Context context, PiwigoUploadFileFilesExistAlreadyResponse response);

    protected abstract void onLocalFileError(@NonNull Context context, PiwigoUploadFileLocalErrorResponse response);

    protected abstract void onFileUploadProgressUpdate(@NonNull Context context, PiwigoUploadProgressUpdateResponse response);

    protected abstract void onLocalUnexpectedError(@NonNull Context context, PiwigoUploadUnexpectedLocalErrorResponse response);

    protected abstract void onFileCompressionProgressUpdate(@NonNull Context context, PiwigoVideoCompressionProgressUpdateResponse response);

    protected abstract void onPrepareUploadFailed(@NonNull Context context, PiwigoPrepareUploadFailedResponse response);

    protected abstract void onCleanupPostUploadFailed(@NonNull Context context, PiwigoCleanupPostUploadFailedResponse response);

    protected abstract void onUploadComplete(@NonNull Context context, UploadJob job);

    @Override
    public boolean canHandlePiwigoResponseNow(PiwigoResponseBufferingHandler.Response response) {
        return true; // otherwise it won't get handled unless the fragment is showing (i.e. the upload activity is also showing).
    }
}