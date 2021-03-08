package delit.piwigoclient.piwigoApi.upload.actors;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import delit.libs.core.util.Logging;
import delit.libs.util.IOUtils;
import delit.libs.util.ObjectUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.handlers.BaseImageGetInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageGetInfoResponseHandler;
import delit.piwigoclient.piwigoApi.upload.FileUploadDetails;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.piwigoApi.upload.handlers.ImageCheckFilesResponseHandler;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadProgressUpdateResponse;

import static delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler.getNextMessageId;

public class FileUploadVerifyActor extends UploadActor {

    private static final String TAG = "FileUploadVerifyActor";

    public FileUploadVerifyActor(@NonNull Context context, @NonNull UploadJob uploadJob, @NonNull ActorListener listener) {
        super(context, uploadJob, listener);
    }

    public void run(FileUploadDetails fud) {
        doVerificationOfUploadedFileData(getUploadJob(), fud);
    }

    public void doVerificationOfUploadedFileData(UploadJob thisUploadJob, FileUploadDetails fud) {
        if (!fud.isUploadCancelled()) {
//            TaskProgressTracker verificationTracker = thisUploadJob.getTaskProgressTrackerForSingleFileVerification();
            try {

                Boolean verifiedUploadedFile = verifyFileNotCorrupted(fud);
                if (verifiedUploadedFile == null) {
                    // notify the listener of the final error we received from the server
                    String errorMsg = getContext().getString(R.string.error_upload_file_verification_failed, fud.getFileToBeUploaded().getPath());
                    getListener().notifyListenersOfCustomErrorUploadingFile(thisUploadJob, fud.getFileUri(), false, errorMsg);
                    // this file isn't on the server at all, so just clear the upload progress to allow retry.
                    fud.resetStatus();
                } else if (verifiedUploadedFile) {
                    //TODO send AJAX request to generate all derivatives. Need Custom method in piwigo client plugin. - method will be sent id of image and the server will invoke a get on all derivative urls (obtained using a ws call to pwg.getDerivatives).

                    fud.setStatusVerified();
                    getListener().recordAndPostNewResponse(thisUploadJob, new PiwigoUploadProgressUpdateResponse(getNextMessageId(), fud.getFileUri(), fud.getOverallUploadProgress()));
                    deleteCompressedVersionIfExists(fud);
                } else {
                    // the file verification failed - this file is corrupt (needs delete but then re-upload).
                    fud.setStatusCorrupt();
                    // reduce the overall progress again.
                    thisUploadJob.getTaskProgressTrackerForOverallCompressionAndUploadOfData(getContext()).decrementWorkDone(thisUploadJob.getUploadUploadProgressTicksForFile(fud.getFileUri()));
                }
            } catch(RuntimeException e) {
                Logging.recordException(e);
                getListener().notifyListenersOfCustomErrorUploadingFile(thisUploadJob, fud.getFileUri(), false, e.getMessage());
            } finally {
//                verificationTracker.markComplete();
            }
        } else {
            fud.setStatusRequiresDelete();
        }
    }

    private Boolean verifyFileNotCorrupted(FileUploadDetails fileUploadDetail) {
        ResourceItem uploadedResource = fileUploadDetail.getServerResource();
        if (uploadedResource.getFileChecksum() == null) {
            return Boolean.FALSE; // cannot verify it as don't have a local checksum for some reason. Will have to assume it is corrupt.
        }
        ImageCheckFilesResponseHandler<ResourceItem> imageFileCheckHandler = new ImageCheckFilesResponseHandler<>(uploadedResource);
        getServerCaller().invokeWithRetries(getUploadJob(), fileUploadDetail, imageFileCheckHandler, 2);
        Boolean val = imageFileCheckHandler.isSuccess() ? imageFileCheckHandler.isFileMatch() : null;
        if (Boolean.FALSE.equals(val)) {

            ResourceItem uploadedResourceDummy = new ResourceItem(uploadedResource.getId(), uploadedResource.getName(), null, null, null, null);
            ImageGetInfoResponseHandler<ResourceItem> imageDetailsHandler = new ImageGetInfoResponseHandler<>(uploadedResourceDummy);
            getServerCaller().invokeWithRetries(getUploadJob(), imageDetailsHandler, 2);
            if (imageDetailsHandler.isSuccess()) {
                BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse<?> rsp = (ImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse<?>) imageDetailsHandler.getResponse();
                val = ObjectUtils.areEqual(uploadedResource.getFileChecksum(), rsp.getResource().getFileChecksum());
                if (val) {
                    String msgStr = String.format(getContext().getString(R.string.message_piwigo_server_inconsistent_results), imageFileCheckHandler.getPiwigoMethod(), imageDetailsHandler.getPiwigoMethod());
                    getListener().reportForUser(msgStr);
                }
            }
        }
        return val;
    }

    private void deleteCompressedVersionIfExists(FileUploadDetails fud) {
        DocumentFile compressedFile = IOUtils.getSingleDocFile(getContext(), fud.getCompressedFileUri());
        if (compressedFile != null && compressedFile.exists()) {
            if (!compressedFile.delete()) {
                IOUtils.onFileDeleteFailed(TAG, compressedFile, "compressed file after upload of file (or upload cancelled)");
            }
        }
    }
}
