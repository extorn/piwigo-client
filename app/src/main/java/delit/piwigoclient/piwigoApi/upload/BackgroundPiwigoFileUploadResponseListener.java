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
    protected void onGetSubGalleries(PiwigoResponseBufferingHandler.PiwigoGetSubAlbumsAdminResponse response) {

    }

    @Override
    protected void onRequestedFileUploadCancelComplete(Context context, File cancelledFile) {

    }

    @Override
    protected void onGetSubGalleryNames(PiwigoResponseBufferingHandler.PiwigoGetSubAlbumNamesResponse response) {

    }

    @Override
    protected void onAddUploadedFileToAlbumFailure(Context context, PiwigoResponseBufferingHandler.PiwigoUploadFileAddToAlbumFailedResponse response) {

    }

    @Override
    protected void onChunkUploadFailed(Context context, PiwigoResponseBufferingHandler.PiwigoUploadFileChunkFailedResponse response) {

    }

    @Override
    protected void onFilesSelectedForUploadAlreadyExistOnServer(Context context, PiwigoResponseBufferingHandler.PiwigoUploadFileFilesExistAlreadyResponse response) {

    }

    @Override
    protected void onLocalFileError(Context context, PiwigoResponseBufferingHandler.PiwigoUploadFileLocalErrorResponse response) {

    }

    @Override
    protected void onFileUploadProgressUpdate(Context context, PiwigoResponseBufferingHandler.PiwigoUploadProgressUpdateResponse response) {

    }

    @Override
    protected void onPrepareUploadFailed(Context context, PiwigoResponseBufferingHandler.PiwigoPrepareUploadFailedResponse response) {

    }

    @Override
    protected void onUploadComplete(Context context, UploadJob job) {

    }
}
