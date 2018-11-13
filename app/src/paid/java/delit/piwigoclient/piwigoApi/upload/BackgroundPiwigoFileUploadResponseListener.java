package delit.piwigoclient.piwigoApi.upload;

import android.content.Context;

import java.io.File;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.ui.upload.PiwigoFileUploadResponseListener;

public class BackgroundPiwigoFileUploadResponseListener extends PiwigoFileUploadResponseListener {

    public BackgroundPiwigoFileUploadResponseListener(Context context) {
        super(context);
    }

    @Override
    protected void onErrorResponse(PiwigoResponseBufferingHandler.ErrorResponse response) {
        // do nothing.
    }

    @Override
    protected void onRequestedFileUploadCancelComplete(Context context, File cancelledFile) {

    }

    @Override
    protected void onAddUploadedFileToAlbumFailure(Context context, BasePiwigoUploadService.PiwigoUploadFileAddToAlbumFailedResponse response) {

    }

    @Override
    protected void onChunkUploadFailed(Context context, BasePiwigoUploadService.PiwigoUploadFileChunkFailedResponse response) {

    }

    @Override
    protected void onFilesSelectedForUploadAlreadyExistOnServer(Context context, BasePiwigoUploadService.PiwigoUploadFileFilesExistAlreadyResponse response) {

    }

    @Override
    protected void onLocalFileError(Context context, BasePiwigoUploadService.PiwigoUploadFileLocalErrorResponse response) {

    }

    @Override
    protected void onFileUploadProgressUpdate(Context context, BasePiwigoUploadService.PiwigoUploadProgressUpdateResponse response) {

    }

    @Override
    protected void onPrepareUploadFailed(Context context, BasePiwigoUploadService.PiwigoPrepareUploadFailedResponse response) {

    }

    @Override
    protected void onCleanupPostUploadFailed(Context context, BasePiwigoUploadService.PiwigoCleanupPostUploadFailedResponse response) {

    }

    @Override
    protected void onUploadComplete(Context context, UploadJob job) {
    }
}
