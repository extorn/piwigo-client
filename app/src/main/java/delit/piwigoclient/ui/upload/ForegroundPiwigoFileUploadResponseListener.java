package delit.piwigoclient.ui.upload;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.exoplayer2.ExoPlaybackException;

import org.greenrobot.eventbus.EventBus;

import java.io.FileNotFoundException;
import java.util.HashSet;

import delit.libs.core.util.Logging;
import delit.libs.util.IOUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.upload.BasePiwigoUploadService;
import delit.piwigoclient.piwigoApi.upload.ForegroundPiwigoUploadService;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.events.AlbumAlteredEvent;

class ForegroundPiwigoFileUploadResponseListener<F extends AbstractUploadFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  extends PiwigoFileUploadResponseListener<F,FUIH> {

    private static final String TAG = "FgFileUploadRespL";

    ForegroundPiwigoFileUploadResponseListener(Context context) {
        super(context);
    }

    @Override
    public void onBeforeHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
        if (getParent() != null && getParent().isVisible()) {
            getParent().updateActiveSessionDetails();
        }
        super.onBeforeHandlePiwigoResponse(response);
    }

    @Override
    protected void onRequestedFileUploadCancelComplete(@NonNull Context context, Uri cancelledFile) {
        if (getParent() != null && getParent().isAdded()) {
            FilesToUploadRecyclerViewAdapter<?,?,?> adapter = getParent().getFilesForUploadViewAdapter();
            adapter.remove(cancelledFile);
            getParent().releaseUriPermissionsForUploadItem(cancelledFile);
            UploadJob uploadJob = ForegroundPiwigoUploadService.getActiveForegroundJob(context, getParent().getUploadJobId());
            updateOverallUploadProgress(uploadJob.getOverallUploadProgressInt());
        }
        if (getParent() != null && getParent().getUploadJobId() != null) {
            if (getParent() != null && getParent().isAdded()) {
                UploadJob uploadJob = ForegroundPiwigoUploadService.getActiveForegroundJob(context, getParent().getUploadJobId());
                if (uploadJob.isFilePartiallyUploaded(cancelledFile)) {
                    getParent().getUiHelper().showDetailedMsg(R.string.alert_warning, getParent().getString(R.string.alert_partial_upload_deleted));
                }
            }
        } else {
            Logging.logAnalyticEvent(context,"noJobDelFile", null);
        }
    }

    @Override
    protected void onUploadComplete(@NonNull final Context context, final UploadJob job) {
        if (getParent() != null && getParent().isAdded()) {
            if (job.hasJobCompletedAllActionsSuccessfully() && job.isFinished()) {
                ForegroundPiwigoUploadService.removeJob(job);
                HashSet<Uri> filesPendingApproval = job.getFilesPendingApproval();
                if (filesPendingApproval.size() > 0) {
                    String msg = getParent().getString(R.string.alert_message_info_files_already_pending_approval_pattern, filesPendingApproval.size());
                    getParent().getUiHelper().showOrQueueDialogMessage(R.string.alert_warning, msg);
                }
            } else if (job.getAndClearWasLastRunCancelled()) {
                getParent().getUiHelper().showOrQueueDialogMessage(R.string.alert_message_upload_cancelled, context.getString(R.string.alert_message_upload_cancelled_message), R.string.button_ok);
            }
            updateOverallUploadProgress(job.getOverallUploadProgressInt());
        }

        // ensure the album view is refreshed if visible (to remove temp upload album).
        for (Long albumParent : job.getUploadToCategoryParentage()) {
            EventBus.getDefault().post(new AlbumAlteredEvent(albumParent));
        }
        EventBus.getDefault().post(new AlbumAlteredEvent(job.getUploadToCategory()));
        // notify the user the upload has finished.
        notifyUserUploadJobComplete(context, job);
    }

    private void notifyUserUploadJobComplete(@NonNull Context context, UploadJob job) {
        if (job.hasJobCompletedAllActionsSuccessfully()) {
            getParent().onUploadJobSuccess(job);
        } else {
            getParent().onUploadJobFailure(job);
        }
        getParent().hideOverallUploadProgressIndicator();
    }

    @Override
    protected void onLocalUnexpectedError(Context context, BasePiwigoUploadService.PiwigoUploadUnexpectedLocalErrorResponse response) {
        String errorMessage;
        Logging.log(Log.ERROR, TAG, "Local Upload Error");
        Logging.recordException(response.getError());
        errorMessage = response.getError().getMessage();
        //TODO show the user the full cause perhaps
        getParent().notifyUser(context, R.string.alert_error, errorMessage);
    }

    @Override
    protected void onLocalFileError(Context context, final BasePiwigoUploadService.PiwigoUploadFileLocalErrorResponse response) {
        if(response.isItemUploadCancelled()) {
            getParent().getFilesForUploadViewAdapter().updateUploadStatus(response.getFileForUpload(), UploadJob.ERROR);
        }
        String errorMessage;
        Logging.log(Log.ERROR, TAG, "Local file Upload Error");
        Logging.recordException(response.getError());
        Uri fileForUploadUri = response.getFileForUpload();
        DocumentFile fileForUpload = IOUtils.getSingleDocFile(context, fileForUploadUri);
        String uploadFilename = fileForUpload == null ? null : fileForUpload.getName();
        if (response.getError() instanceof FileNotFoundException) {
            errorMessage = String.format(context.getString(R.string.alert_error_upload_file_no_longer_available_message_pattern), uploadFilename,fileForUploadUri);
        } else if (response.getError() instanceof ExoPlaybackException) {
            errorMessage = String.format(context.getString(R.string.alert_error_upload_file_compression_error_message_pattern), uploadFilename, fileForUploadUri);
        } else {
            errorMessage = String.format(context.getString(R.string.alert_error_upload_file_read_error_message_pattern), uploadFilename, fileForUploadUri);
        }
        //TODO show the user the full cause perhaps
        getParent().notifyUser(context, R.string.alert_error, errorMessage);
    }

    @Override
    protected void onPrepareUploadFailed(Context context, final BasePiwigoUploadService.PiwigoPrepareUploadFailedResponse response) {

        PiwigoResponseBufferingHandler.Response error = response.getError();
        getParent().processPiwigoError(context, error);
    }

    @Override
    protected void onCleanupPostUploadFailed(@NonNull Context context, BasePiwigoUploadService.PiwigoCleanupPostUploadFailedResponse response) {
        PiwigoResponseBufferingHandler.Response error = response.getError();
        getParent().processPiwigoError(context, error);
    }

    private void updateOverallUploadProgress(int progress) {
        getParent().showOverallUploadProgressIndicator(R.string.uploading_progress_bar_message, progress);
        if (progress == 100) {
            getParent().hideOverallUploadProgressIndicator();
        }
    }

    @Override
    protected void onFileUploadProgressUpdate(@NonNull Context context, final BasePiwigoUploadService.PiwigoUploadProgressUpdateResponse response) {
        if (getParent() != null && getParent().isAdded()) {
            FilesToUploadRecyclerViewAdapter<?,?,?> adapter = getParent().getFilesForUploadViewAdapter();
            adapter.updateUploadProgress(response.getFileForUpload(), response.getProgress());
            UploadJob activeJob = getParent().getActiveJob(context);
            if (activeJob != null) {
                updateOverallUploadProgress(activeJob.getOverallUploadProgressInt());
            }
        }
        if (response.getProgress() == 100) {
            onUploadOfFileComplete(context, response);
        }
    }

    @Override
    protected void onFileCompressionProgressUpdate(@NonNull Context context, BasePiwigoUploadService.PiwigoVideoCompressionProgressUpdateResponse response) {
        if (getParent() != null && getParent().isAdded()) {
            FilesToUploadRecyclerViewAdapter<?,?,?> adapter = getParent().getFilesForUploadViewAdapter();
            adapter.updateCompressionProgress(response.getFileForUpload(), response.getCompressedFileUpload(), response.getProgress());
            updateOverallUploadProgress(getParent().getActiveJob(context).getOverallUploadProgressInt());
        }
        if (response.getProgress() == 100) {
            onFileCompressionComplete(context, response);
        }
    }

    private void onFileCompressionComplete(@NonNull Context context, final BasePiwigoUploadService.PiwigoVideoCompressionProgressUpdateResponse response) {
        FilesToUploadRecyclerViewAdapter<?,?,?> adapter = getParent().getFilesForUploadViewAdapter();
        adapter.updateUploadStatus(response.getFileForUpload(), UploadJob.COMPRESSED);
        //FIXME is this next line needed or in preference to the one above?
//        adapter.updateUploadStatus(response.getCompressedFileUpload(), UploadJob.COMPRESSED);
    }

    private void onUploadOfFileComplete(@NonNull Context context, final BasePiwigoUploadService.PiwigoUploadProgressUpdateResponse response) {

        //TODO This method causes lots of server calls and is really unnecessary! Refresh once at the end

        if(getParent() == null) {
            Logging.log(Log.ERROR, TAG, "Unable to notify user of file upload complete");
            return;
        }


        UploadJob uploadJob = getParent().getActiveJob(context);
        if (uploadJob != null) {
            updateOverallUploadProgress(uploadJob.getOverallUploadProgressInt());
//                ResourceItem item = uploadJob.getUploadedFileResource(response.getFileForUpload());
//                for (Long albumParent : uploadJob.getUploadToCategoryParentage()) {
//                    EventBus.getDefault().post(new AlbumAlteredEvent(albumParent));
//                }
//                EventBus.getDefault().post(new AlbumAlteredEvent(uploadJob.getUploadToCategory(), item.getId()));

        }

        if (getParent() != null && getParent().isAdded()) {
            // somehow upload job can be null... hopefully this copes with that scenario.
            FilesToUploadRecyclerViewAdapter<?,?,?> adapter = getParent().getFilesForUploadViewAdapter();
            adapter.remove(response.getFileForUpload());
            getParent().releaseUriPermissionsForUploadItem(response.getFileForUpload());
        }
    }

    @Override
    protected void onFilesSelectedForUploadAlreadyExistOnServer(@NonNull Context context, final BasePiwigoUploadService.PiwigoUploadFileFilesExistAlreadyResponse response) {
        if (getParent() != null && getParent().isAdded()) {
            UploadJob uploadJob = getParent().getActiveJob(context);

            if (uploadJob != null) {
                FilesToUploadRecyclerViewAdapter<?,?,?> adapter = getParent().getFilesForUploadViewAdapter();
                for (Uri existingFile : response.getExistingFiles()) {
                    int progress = uploadJob.getUploadProgress(existingFile);
                    adapter.updateUploadProgress(existingFile, progress);
//                    adapter.remove(existingFile);
                }
                updateOverallUploadProgress(uploadJob.getOverallUploadProgressInt());
            }
        }
        String message = String.format(context.getString(R.string.alert_items_for_upload_already_exist_message_pattern), response.getExistingFiles().size());
        getParent().notifyUser(context, R.string.alert_information, message);
    }

    @Override
    protected void onMessageForUser(Context context, BasePiwigoUploadService.MessageForUserResponse response) {
        getParent().notifyUser(context, R.string.alert_information, response.getMessage());
    }

    @Override
    protected void onChunkUploadFailed(Context context, final BasePiwigoUploadService.PiwigoUploadFileChunkFailedResponse response) {
        PiwigoResponseBufferingHandler.Response error = response.getError();
        Uri fileForUpload = response.getFileForUpload();
        String errorMessage = null;

        if (error instanceof PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) {
            PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse err = ((PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) error);
            String msg = err.getErrorMessage();
            if ("java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1 path $".equals(msg)) {
                msg = err.getResponse();
            }
            errorMessage = String.format(context.getString(R.string.alert_upload_file_failed_webserver_message_pattern), fileForUpload, err.getStatusCode(), msg);
        } else if (error instanceof PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse) {
            PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse err = (PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse) error;
            errorMessage = String.format(context.getString(R.string.alert_upload_file_failed_webresponse_message_pattern), fileForUpload, err.getRawResponse());
        } else if (error instanceof PiwigoResponseBufferingHandler.PiwigoServerErrorResponse) {
            PiwigoResponseBufferingHandler.PiwigoServerErrorResponse err = ((PiwigoResponseBufferingHandler.PiwigoServerErrorResponse) error);
            errorMessage = String.format(context.getString(R.string.alert_upload_file_failed_piwigo_message_pattern), fileForUpload, err.getPiwigoErrorCode(), err.getPiwigoErrorMessage());
        }
        if (errorMessage != null) {
            getParent().notifyUser(context, R.string.alert_error, errorMessage);
        }
    }

    @Override
    public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
        if (response instanceof AlbumDeleteResponseHandler.PiwigoAlbumDeletedResponse) {
            getParent().onAlbumDeleted((AlbumDeleteResponseHandler.PiwigoAlbumDeletedResponse) response);
        } else {
            super.onAfterHandlePiwigoResponse(response);
        }
    }

    @Override
    protected void onErrorResponse(PiwigoResponseBufferingHandler.ErrorResponse response) {
        //don't care. used to be used when retrieving album names for the spinner.
    }

    @Override
    protected void onAddUploadedFileToAlbumFailure(Context context, final BasePiwigoUploadService.PiwigoUploadFileAddToAlbumFailedResponse response) {
        PiwigoResponseBufferingHandler.Response error = response.getError();
        Uri fileForUploadUri = response.getFileForUpload();
        DocumentFile fileForUpload = IOUtils.getSingleDocFile(context, fileForUploadUri);
        String errorMessage = null;
        String uploadFilename = fileForUpload == null ? "" : fileForUpload.getName();

        if(response.isItemUploadCancelled()) {
            getParent().getFilesForUploadViewAdapter().updateUploadStatus(response.getFileForUpload(), UploadJob.ERROR);
        }

        if (error instanceof PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) {
            PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse err = ((PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) error);
            errorMessage = String.format(context.getString(R.string.alert_upload_file_failed_webserver_message_pattern), uploadFilename, err.getStatusCode(), err.getErrorMessage());
        } else if (error instanceof PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse) {
            PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse err = (PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse) error;
            errorMessage = String.format(context.getString(R.string.alert_upload_file_failed_webresponse_message_pattern), uploadFilename, err.getRawResponse());
        } else if (error instanceof PiwigoResponseBufferingHandler.PiwigoServerErrorResponse) {
            PiwigoResponseBufferingHandler.PiwigoServerErrorResponse err = ((PiwigoResponseBufferingHandler.PiwigoServerErrorResponse) error);
            if ("file already exists".equals(err.getPiwigoErrorMessage())) {
                if (getParent() != null && getParent().isAdded()) {
                    FilesToUploadRecyclerViewAdapter<?,?,?> adapter = getParent().getFilesForUploadViewAdapter();
                    adapter.remove(fileForUploadUri);
                    getParent().releaseUriPermissionsForUploadItem(fileForUploadUri);
                }
                errorMessage = String.format(context.getString(R.string.alert_error_upload_file_already_on_server_message_pattern), uploadFilename);
            } else {
                errorMessage = String.format(context.getString(R.string.alert_upload_file_failed_piwigo_message_pattern), uploadFilename, err.getPiwigoErrorCode(), err.getPiwigoErrorMessage());
            }
        } else if (error instanceof PiwigoResponseBufferingHandler.CustomErrorResponse) {
            errorMessage = ((PiwigoResponseBufferingHandler.CustomErrorResponse) error).getErrorMessage();
        }
        if (errorMessage != null) {
            F parent = getParent();
            if (parent != null && parent.isAdded()) {
                parent.notifyUser(context, R.string.alert_error, errorMessage);
            }
        }
    }
}
