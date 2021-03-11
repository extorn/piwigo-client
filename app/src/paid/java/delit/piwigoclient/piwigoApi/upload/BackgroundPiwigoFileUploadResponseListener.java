package delit.piwigoclient.piwigoApi.upload;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

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
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.AlbumAlteredEvent;
import delit.piwigoclient.ui.upload.PiwigoFileUploadResponseListener;

public class BackgroundPiwigoFileUploadResponseListener<P extends UIHelper<P,T>, T> extends PiwigoFileUploadResponseListener<P,T> {

    public BackgroundPiwigoFileUploadResponseListener(Context context) {
        super(context);
    }

    @Override
    protected void onMessageForUser(@NonNull Context context, MessageForUserResponse response) {

    }

    @Override
    protected void onErrorResponse(PiwigoResponseBufferingHandler.ErrorResponse response) {
        // do nothing.
    }

    @Override
    protected void onRequestedFileUploadCancelComplete(@NonNull Context context, Uri cancelledFile) {

    }

    @Override
    protected void onAddUploadedFileToAlbumFailure(@NonNull Context context, PiwigoUploadFileAddToAlbumFailedResponse response) {

    }

    @Override
    protected void onChunkUploadFailed(@NonNull Context context, PiwigoUploadFileChunkFailedResponse response) {

    }

    @Override
    protected void onFilesSelectedForUploadAlreadyExistOnServer(@NonNull Context context, PiwigoUploadFileFilesExistAlreadyResponse response) {

    }

    @Override
    protected void onLocalFileError(@NonNull Context context, PiwigoUploadFileLocalErrorResponse response) {

    }

    @Override
    protected void onLocalUnexpectedError(@NonNull Context context, PiwigoUploadUnexpectedLocalErrorResponse response) {

    }

    @Override
    protected void onFileCompressionProgressUpdate(@NonNull Context context, PiwigoVideoCompressionProgressUpdateResponse response) {
        // Do nothing for now.
    }

    @Override
    protected void onFileUploadProgressUpdate(@NonNull Context context, @NonNull PiwigoUploadProgressUpdateResponse response) {
        if (response.getProgress() == 100) {
            onFileUploadComplete(context, response);
        }
    }

    private void onFileUploadComplete(@NonNull Context context, @NonNull final PiwigoUploadProgressUpdateResponse response) {
        UploadJob uploadJob = BackgroundJobLoadActor.getDefaultInstance(context).getActiveBackgroundJobByJobId(response.getJobId());
        if (uploadJob != null) {
            //TODO too much updating?
            for (Long albumParent : uploadJob.getUploadToCategory().getParentageChain()) {
                EventBus.getDefault().post(new AlbumAlteredEvent(albumParent));
            }
            EventBus.getDefault().post(new AlbumAlteredEvent(uploadJob.getUploadToCategory().getId()));
        }
    }

    @Override
    protected void onPrepareUploadFailed(@NonNull Context context, PiwigoPrepareUploadFailedResponse response) {

    }

    @Override
    protected void onCleanupPostUploadFailed(@NonNull Context context, PiwigoCleanupPostUploadFailedResponse response) {

    }

    @Override
    protected void onUploadComplete(@NonNull Context context, UploadJob job) {
        // update the album view(s) if relevant.
        for (Long albumParent : job.getUploadToCategory().getParentageChain()) {
            EventBus.getDefault().post(new AlbumAlteredEvent(albumParent));
        }
        EventBus.getDefault().post(new AlbumAlteredEvent(job.getUploadToCategory().getId()));
    }
}
