package delit.piwigoclient.ui.upload;

import android.content.Context;

import java.io.File;

import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.upload.UploadJob;

public abstract class PiwigoFileUploadResponseListener extends BasicPiwigoResponseListener {

    private final Context context;

    public PiwigoFileUploadResponseListener(Context context) {
        this.context = context;
    }

    @Override
    public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {

        if (response instanceof PiwigoResponseBufferingHandler.PiwigoUploadFileJobCompleteResponse) {
            onUploadComplete(context, ((PiwigoResponseBufferingHandler.PiwigoUploadFileJobCompleteResponse)response).getJob());
        } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoPrepareUploadFailedResponse) {
            onPrepareUploadFailed(context, (PiwigoResponseBufferingHandler.PiwigoPrepareUploadFailedResponse) response);
        } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoUploadProgressUpdateResponse) {
            onFileUploadProgressUpdate(context, (PiwigoResponseBufferingHandler.PiwigoUploadProgressUpdateResponse) response);
        } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoUploadFileLocalErrorResponse) {
            onLocalFileError(context, (PiwigoResponseBufferingHandler.PiwigoUploadFileLocalErrorResponse) response);
        } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoUploadFileFilesExistAlreadyResponse) {
            onFilesSelectedForUploadAlreadyExistOnServer(context, (PiwigoResponseBufferingHandler.PiwigoUploadFileFilesExistAlreadyResponse) response);
        } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoUploadFileChunkFailedResponse) {
            onChunkUploadFailed(context, (PiwigoResponseBufferingHandler.PiwigoUploadFileChunkFailedResponse) response);
        } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoUploadFileAddToAlbumFailedResponse) {
            onAddUploadedFileToAlbumFailure(context, (PiwigoResponseBufferingHandler.PiwigoUploadFileAddToAlbumFailedResponse) response);
        } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoGetSubAlbumNamesResponse) {
            onGetSubGalleryNames((PiwigoResponseBufferingHandler.PiwigoGetSubAlbumNamesResponse) response);
        } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoGetSubAlbumsAdminResponse) {
            onGetSubGalleries((PiwigoResponseBufferingHandler.PiwigoGetSubAlbumsAdminResponse) response);
        } else if(response instanceof PiwigoResponseBufferingHandler.FileUploadCancelledResponse) {
            onRequestedFileUploadCancelComplete(context, ((PiwigoResponseBufferingHandler.FileUploadCancelledResponse)response).getCancelledFile());
        } else if(response instanceof PiwigoResponseBufferingHandler.PiwigoStartUploadFileResponse) {
            // ignore for now.
        } else if(response instanceof PiwigoResponseBufferingHandler.ErrorResponse) {
            onErrorResponse((PiwigoResponseBufferingHandler.ErrorResponse) response);
        }
    }

    protected abstract void onErrorResponse(PiwigoResponseBufferingHandler.ErrorResponse response);

    protected abstract void onGetSubGalleries(PiwigoResponseBufferingHandler.PiwigoGetSubAlbumsAdminResponse response);

    protected abstract void onRequestedFileUploadCancelComplete(Context context, File cancelledFile);

    protected abstract void onGetSubGalleryNames(PiwigoResponseBufferingHandler.PiwigoGetSubAlbumNamesResponse response);

    protected abstract void onAddUploadedFileToAlbumFailure(Context context, PiwigoResponseBufferingHandler.PiwigoUploadFileAddToAlbumFailedResponse response);

    protected abstract void onChunkUploadFailed(Context context, PiwigoResponseBufferingHandler.PiwigoUploadFileChunkFailedResponse response);

    protected abstract void onFilesSelectedForUploadAlreadyExistOnServer(Context context, PiwigoResponseBufferingHandler.PiwigoUploadFileFilesExistAlreadyResponse response);

    protected abstract void onLocalFileError(Context context, PiwigoResponseBufferingHandler.PiwigoUploadFileLocalErrorResponse response);

    protected abstract void onFileUploadProgressUpdate(Context context, PiwigoResponseBufferingHandler.PiwigoUploadProgressUpdateResponse response);

    protected abstract void onPrepareUploadFailed(Context context, PiwigoResponseBufferingHandler.PiwigoPrepareUploadFailedResponse response);

    protected abstract void onUploadComplete(Context context, UploadJob job);

}