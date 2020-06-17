package delit.piwigoclient.ui.upload;

import android.content.Context;
import android.net.Uri;

import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.upload.BasePiwigoUploadService;
import delit.piwigoclient.piwigoApi.upload.UploadJob;

public abstract class PiwigoFileUploadResponseListener<T> extends BasicPiwigoResponseListener<T> {

    private final Context context;

    public PiwigoFileUploadResponseListener(Context context) {
        this.context = context.getApplicationContext();// use app context so it doesn't matter if it leaks.
    }

    @Override
    public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {

        if (response instanceof BasePiwigoUploadService.PiwigoUploadFileJobCompleteResponse) {
            onUploadComplete(context, ((BasePiwigoUploadService.PiwigoUploadFileJobCompleteResponse) response).getJob());
        } else if (response instanceof BasePiwigoUploadService.PiwigoPrepareUploadFailedResponse) {
            onPrepareUploadFailed(context, (BasePiwigoUploadService.PiwigoPrepareUploadFailedResponse) response);
        } else if (response instanceof BasePiwigoUploadService.PiwigoCleanupPostUploadFailedResponse) {
            onCleanupPostUploadFailed(context, (BasePiwigoUploadService.PiwigoCleanupPostUploadFailedResponse) response);
        } else if (response instanceof BasePiwigoUploadService.PiwigoUploadProgressUpdateResponse) {
            onFileUploadProgressUpdate(context, (BasePiwigoUploadService.PiwigoUploadProgressUpdateResponse) response);
        } else if (response instanceof BasePiwigoUploadService.PiwigoVideoCompressionProgressUpdateResponse) {
            onFileCompressionProgressUpdate(context, (BasePiwigoUploadService.PiwigoVideoCompressionProgressUpdateResponse) response);
        } else if (response instanceof BasePiwigoUploadService.PiwigoUploadFileLocalErrorResponse) {
            onLocalFileError(context, (BasePiwigoUploadService.PiwigoUploadFileLocalErrorResponse) response);
        } else if (response instanceof BasePiwigoUploadService.PiwigoUploadFileFilesExistAlreadyResponse) {
            onFilesSelectedForUploadAlreadyExistOnServer(context, (BasePiwigoUploadService.PiwigoUploadFileFilesExistAlreadyResponse) response);
        } else if (response instanceof BasePiwigoUploadService.PiwigoUploadFileChunkFailedResponse) {
            onChunkUploadFailed(context, (BasePiwigoUploadService.PiwigoUploadFileChunkFailedResponse) response);
        } else if (response instanceof BasePiwigoUploadService.PiwigoUploadFileAddToAlbumFailedResponse) {
            onAddUploadedFileToAlbumFailure(context, (BasePiwigoUploadService.PiwigoUploadFileAddToAlbumFailedResponse) response);
        } else if (response instanceof BasePiwigoUploadService.FileUploadCancelledResponse) {
            onRequestedFileUploadCancelComplete(context, ((BasePiwigoUploadService.FileUploadCancelledResponse) response).getCancelledFile());
        } else if (response instanceof BasePiwigoUploadService.PiwigoStartUploadFileResponse) {
            // ignore for now.
        } else if (response instanceof BasePiwigoUploadService.MessageForUserResponse) {
            onMessageForUser(context, (BasePiwigoUploadService.MessageForUserResponse) response);
        } else if (response instanceof PiwigoResponseBufferingHandler.ErrorResponse) {
            onErrorResponse((PiwigoResponseBufferingHandler.ErrorResponse) response);
        }
    }

    protected abstract void onMessageForUser(Context context, BasePiwigoUploadService.MessageForUserResponse response);

    protected abstract void onErrorResponse(PiwigoResponseBufferingHandler.ErrorResponse response);

    protected abstract void onRequestedFileUploadCancelComplete(Context context, Uri cancelledFile);

    protected abstract void onAddUploadedFileToAlbumFailure(Context context, BasePiwigoUploadService.PiwigoUploadFileAddToAlbumFailedResponse response);

    protected abstract void onChunkUploadFailed(Context context, BasePiwigoUploadService.PiwigoUploadFileChunkFailedResponse response);

    protected abstract void onFilesSelectedForUploadAlreadyExistOnServer(Context context, BasePiwigoUploadService.PiwigoUploadFileFilesExistAlreadyResponse response);

    protected abstract void onLocalFileError(Context context, BasePiwigoUploadService.PiwigoUploadFileLocalErrorResponse response);

    protected abstract void onFileUploadProgressUpdate(Context context, BasePiwigoUploadService.PiwigoUploadProgressUpdateResponse response);

    protected abstract void onFileCompressionProgressUpdate(Context context, BasePiwigoUploadService.PiwigoVideoCompressionProgressUpdateResponse response);

    protected abstract void onPrepareUploadFailed(Context context, BasePiwigoUploadService.PiwigoPrepareUploadFailedResponse response);

    protected abstract void onCleanupPostUploadFailed(Context context, BasePiwigoUploadService.PiwigoCleanupPostUploadFailedResponse response);

    protected abstract void onUploadComplete(Context context, UploadJob job);

    @Override
    public boolean canHandlePiwigoResponseNow(PiwigoResponseBufferingHandler.Response response) {
        return true; // otherwise it won't get handled unless the fragment is showing (i.e. the upload activity is also showing).
    }
}