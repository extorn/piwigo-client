package delit.piwigoclient.piwigoApi.upload;

import android.content.Context;
import android.net.Uri;

import org.greenrobot.eventbus.EventBus;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.upload.actor.BackgroundJobLoadActor;
import delit.piwigoclient.piwigoApi.upload.messages.MessageForUserResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoCleanupPostUploadFailedResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoPrepareUploadFailedResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadFileAddToAlbumFailedResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadFileChunkFailedResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadFileFilesExistAlreadyResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadFileLocalErrorResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadProgressUpdateResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadUnexpectedLocalErrorResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoVideoCompressionProgressUpdateResponse;
import delit.piwigoclient.ui.events.AlbumAlteredEvent;
import delit.piwigoclient.ui.upload.PiwigoFileUploadResponseListener;

public class BackgroundPiwigoFileUploadResponseListener extends PiwigoFileUploadResponseListener {

    public BackgroundPiwigoFileUploadResponseListener(Context context) {
        super(context);
    }

    @Override
    protected void onMessageForUser(Context context, MessageForUserResponse response) {

    }

    @Override
    protected void onErrorResponse(PiwigoResponseBufferingHandler.ErrorResponse response) {
        // do nothing.
    }

    @Override
    protected void onRequestedFileUploadCancelComplete(Context context, Uri cancelledFile) {

    }

    @Override
    protected void onAddUploadedFileToAlbumFailure(Context context, PiwigoUploadFileAddToAlbumFailedResponse response) {

    }

    @Override
    protected void onChunkUploadFailed(Context context, PiwigoUploadFileChunkFailedResponse response) {

    }

    @Override
    protected void onFilesSelectedForUploadAlreadyExistOnServer(Context context, PiwigoUploadFileFilesExistAlreadyResponse response) {

    }

    @Override
    protected void onLocalFileError(Context context, PiwigoUploadFileLocalErrorResponse response) {

    }

    @Override
    protected void onLocalUnexpectedError(Context context, PiwigoUploadUnexpectedLocalErrorResponse response) {

    }

    @Override
    protected void onFileCompressionProgressUpdate(Context context, PiwigoVideoCompressionProgressUpdateResponse response) {
        // Do nothing for now.
    }

    @Override
    protected void onFileUploadProgressUpdate(Context context, PiwigoUploadProgressUpdateResponse response) {
        if (response.getProgress() == 100) {
            onFileUploadComplete(context, response);
        }
    }

    private void onFileUploadComplete(Context context, final PiwigoUploadProgressUpdateResponse response) {
        UploadJob uploadJob = BackgroundJobLoadActor.getActiveBackgroundJobByJobId(context, response.getJobId());
        if (uploadJob != null) {
            for (Long albumParent : uploadJob.getUploadToCategory().getParentageChain()) {
                EventBus.getDefault().post(new AlbumAlteredEvent(albumParent));
            }
            EventBus.getDefault().post(new AlbumAlteredEvent(uploadJob.getUploadToCategory().getId()));
        }
    }

    @Override
    protected void onPrepareUploadFailed(Context context, PiwigoPrepareUploadFailedResponse response) {

    }

    @Override
    protected void onCleanupPostUploadFailed(Context context, PiwigoCleanupPostUploadFailedResponse response) {

    }

    @Override
    protected void onUploadComplete(Context context, UploadJob job) {
        // update the album view(s) if relevant.
        for (Long albumParent : job.getUploadToCategory().getParentageChain()) {
            EventBus.getDefault().post(new AlbumAlteredEvent(albumParent));
        }
        EventBus.getDefault().post(new AlbumAlteredEvent(job.getUploadToCategory().getId()));
    }
}
