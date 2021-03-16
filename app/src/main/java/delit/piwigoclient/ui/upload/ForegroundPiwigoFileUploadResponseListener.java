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
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.piwigoApi.upload.actors.ForegroundJobLoadActor;
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
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.events.AlbumAlteredEvent;
import delit.piwigoclient.ui.upload.list.UploadDataItem;

public class ForegroundPiwigoFileUploadResponseListener<F extends AbstractUploadFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  extends PiwigoFileUploadResponseListener<FUIH, F> {

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
    protected void onRequestedFileUploadCancelComplete(@NonNull Context context, @NonNull Uri cancelledFile) {
        if (getParent() != null && getParent().isAdded()) {
            FilesToUploadRecyclerViewAdapter<?,?,?> adapter = getParent().getFilesForUploadViewAdapter();
            adapter.remove(cancelledFile);
            getParent().releaseUriPermissionsForUploadItem(cancelledFile);
            UploadJob uploadJob = new ForegroundJobLoadActor(context).getActiveForegroundJob(getParent().getUploadJobId());
            updateOverallUploadProgress(uploadJob.getOverallUploadProgressInt());
        }
        if (getParent() != null && getParent().getUploadJobId() != null) {
            if (getParent() != null && getParent().isAdded()) {
                UploadJob uploadJob = new ForegroundJobLoadActor(context).getActiveForegroundJob(getParent().getUploadJobId());
                if (uploadJob.getFileUploadDetails(cancelledFile).isFilePartiallyUploaded()) {
                    getParent().getUiHelper().showDetailedMsg(R.string.alert_warning, getParent().getString(R.string.alert_partial_upload_deleted));
                }
            }
        } else {
            Logging.logAnalyticEvent(context,"noJobDelFile", null);
        }
    }

    @Override
    public void onUploadComplete(@NonNull final Context context, final UploadJob job) {
        if (getParent() != null && getParent().isAdded()) {
            if (job.hasJobCompletedAllActionsSuccessfully() && job.isStatusFinished()) {
                new ForegroundJobLoadActor(context).removeJob(job, true);
                HashSet<Uri> filesPendingApproval = job.getFilesPendingCommunityApproval();
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
        for (Long albumParent : job.getUploadToCategory().getParentageChain()) {
            EventBus.getDefault().post(new AlbumAlteredEvent(albumParent));
        }
        EventBus.getDefault().post(new AlbumAlteredEvent(job.getUploadToCategory().getId()));
        // notify the user the upload has finished.
        notifyUserUploadJobComplete(context, job);
    }

    private void notifyUserUploadJobComplete(@NonNull Context context, UploadJob job) {
        F parent = getParent();
        if(parent != null) {
            if (job.hasJobCompletedAllActionsSuccessfully()) {
                getParent().onNotificationUploadJobSuccess(job);
            } else {
                getParent().onNotificationUploadJobFailure(job);
            }
            getParent().hideOverallUploadProgressIndicator();
        } else {
            Logging.logAnalyticEventIfPossible("FGJobListenerDetached", null);
            Logging.log(Log.ERROR, TAG, "Unable to notify user of job completion status as parent is gone");
        }
    }

    @Override
    protected void onLocalUnexpectedError(@NonNull Context context, PiwigoUploadUnexpectedLocalErrorResponse response) {
        String errorMessage;
        Logging.log(Log.ERROR, TAG, "Local Upload Error");
        Logging.recordException(response.getError());
        errorMessage = response.getError().getMessage();
        //TODO show the user the full cause perhaps
        getParent().notifyUser(context, R.string.alert_error, errorMessage);
    }

    @Override
    protected void onLocalFileError(@NonNull Context context, final PiwigoUploadFileLocalErrorResponse response) {
        if(response.isItemUploadCancelled()) {
            getParent().getFilesForUploadViewAdapter().updateUploadStatus(response.getFileForUpload(), UploadDataItem.STATUS_ERROR);
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
    protected void onPrepareUploadFailed(@NonNull Context context, final PiwigoPrepareUploadFailedResponse response) {

        PiwigoResponseBufferingHandler.Response error = response.getError();
        getParent().processPiwigoError(context, error);
    }

    @Override
    protected void onCleanupPostUploadFailed(@NonNull Context context, PiwigoCleanupPostUploadFailedResponse response) {
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
    protected void onFileUploadProgressUpdate(@NonNull Context context, final PiwigoUploadProgressUpdateResponse response) {
        if (getParent() != null && getParent().isAdded()) {
            if(response.getFileForUpload() != null) {
                FilesToUploadRecyclerViewAdapter<?, ?, ?> adapter = getParent().getFilesForUploadViewAdapter();
                adapter.updateUploadProgress(response.getFileForUpload(), response.getProgress());
            }
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
    protected void onFileCompressionProgressUpdate(@NonNull Context context, PiwigoVideoCompressionProgressUpdateResponse response) {
        if (getParent() != null && getParent().isAdded()) {
            FilesToUploadRecyclerViewAdapter<?,?,?> adapter = getParent().getFilesForUploadViewAdapter();
            adapter.updateCompressionProgress(response.getFileForUpload(), response.getCompressedFileUpload(), response.getProgress());
            updateOverallUploadProgress(getParent().getActiveJob(context).getOverallUploadProgressInt());
        }
        if (response.getProgress() == 100) {
            onFileCompressionComplete(context, response);
        }
    }

    private void onFileCompressionComplete(@NonNull Context context, final PiwigoVideoCompressionProgressUpdateResponse response) {
        FilesToUploadRecyclerViewAdapter<?,?,?> adapter = getParent().getFilesForUploadViewAdapter();
        adapter.updateUploadStatus(response.getFileForUpload(), UploadDataItem.STATUS_COMPRESSED);
    }

    private void onUploadOfFileComplete(@NonNull Context context, final PiwigoUploadProgressUpdateResponse response) {

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

        if (getParent() != null && getParent().isAdded() && response.getFileForUpload() != null) {
            // somehow upload job can be null... hopefully this copes with that scenario.
            FilesToUploadRecyclerViewAdapter<?,?,?> adapter = getParent().getFilesForUploadViewAdapter();
            adapter.remove(response.getFileForUpload());
            if(adapter.getItemCount() == 0 && uploadJob != null && uploadJob.isStatusRunningNow()) {
                getUiHelper().showDetailedShortMsg(R.string.alert_information, R.string.upload_of_files_complete_finishing_up);
            }
        }
    }

    @Override
    protected void onFilesSelectedForUploadAlreadyExistOnServer(@NonNull Context context, final PiwigoUploadFileFilesExistAlreadyResponse response) {
        if (getParent() != null && getParent().isAdded()) {
            UploadJob uploadJob = getParent().getActiveJob(context);

            if (uploadJob != null) {
                FilesToUploadRecyclerViewAdapter<?,?,?> adapter = getParent().getFilesForUploadViewAdapter();
                for (Uri existingFile : response.getExistingFiles()) {
                    int progress = uploadJob.getFileUploadDetails(existingFile).getOverallUploadProgress();
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
    protected void onMessageForUser(@NonNull Context context, MessageForUserResponse response) {
        getParent().notifyUser(context, R.string.alert_information, response.getMessage());
    }

    @Override
    protected void onChunkUploadFailed(@NonNull Context context, final PiwigoUploadFileChunkFailedResponse response) {
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
    protected void onAddUploadedFileToAlbumFailure(@NonNull Context context, final PiwigoUploadFileAddToAlbumFailedResponse response) {
        PiwigoResponseBufferingHandler.Response error = response.getError();
        Uri fileForUploadUri = response.getFileForUpload();
        DocumentFile fileForUpload = IOUtils.getSingleDocFile(context, fileForUploadUri);
        String errorMessage = null;
        String uploadFilename = fileForUpload == null ? "" : fileForUpload.getName();

        if(response.isItemUploadCancelled()) {
            getParent().getFilesForUploadViewAdapter().updateUploadStatus(response.getFileForUpload(), UploadDataItem.STATUS_ERROR);
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
