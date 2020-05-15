package delit.piwigoclient.piwigoApi.upload;

import android.content.Context;
import android.net.Uri;

import org.greenrobot.eventbus.EventBus;

import java.io.File;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.ui.events.AlbumAlteredEvent;
import delit.piwigoclient.ui.upload.PiwigoFileUploadResponseListener;

public class BackgroundPiwigoFileUploadResponseListener extends PiwigoFileUploadResponseListener {

    public BackgroundPiwigoFileUploadResponseListener(Context context) {
        super(context);
    }

    @Override
    protected void onMessageForUser(Context context, BasePiwigoUploadService.MessageForUserResponse response) {

    }

    @Override
    protected void onErrorResponse(PiwigoResponseBufferingHandler.ErrorResponse response) {
        // do nothing.
    }

    @Override
    protected void onRequestedFileUploadCancelComplete(Context context, Uri cancelledFile) {

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
    protected void onFileCompressionProgressUpdate(Context context, BasePiwigoUploadService.PiwigoVideoCompressionProgressUpdateResponse response) {
        // Do nothing for now.
    }

    @Override
    protected void onFileUploadProgressUpdate(Context context, BasePiwigoUploadService.PiwigoUploadProgressUpdateResponse response) {
        if (response.getProgress() == 100) {
            onFileUploadComplete(context, response);
        }
    }

    private void onFileUploadComplete(Context context, final BasePiwigoUploadService.PiwigoUploadProgressUpdateResponse response) {
        UploadJob uploadJob = BackgroundPiwigoUploadService.getActiveBackgroundJobByJobId(context, response.getJobId());
        if (uploadJob != null) {
            for (Long albumParent : uploadJob.getUploadToCategoryParentage()) {
                EventBus.getDefault().post(new AlbumAlteredEvent(albumParent));
            }
            EventBus.getDefault().post(new AlbumAlteredEvent(uploadJob.getUploadToCategory()));
        }
    }

    @Override
    protected void onPrepareUploadFailed(Context context, BasePiwigoUploadService.PiwigoPrepareUploadFailedResponse response) {

    }

    @Override
    protected void onCleanupPostUploadFailed(Context context, BasePiwigoUploadService.PiwigoCleanupPostUploadFailedResponse response) {

    }

    @Override
    protected void onUploadComplete(Context context, UploadJob job) {
        // update the album view(s) if relevant.
        for (Long albumParent : job.getUploadToCategoryParentage()) {
            EventBus.getDefault().post(new AlbumAlteredEvent(albumParent));
        }
        EventBus.getDefault().post(new AlbumAlteredEvent(job.getUploadToCategory()));
    }
}
