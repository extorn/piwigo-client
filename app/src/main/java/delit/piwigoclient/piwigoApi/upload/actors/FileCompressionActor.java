package delit.piwigoclient.piwigoApi.upload.actors;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.MimeTypeFilter;
import androidx.documentfile.provider.DocumentFile;

import delit.libs.core.util.Logging;
import delit.libs.util.IOUtils;
import delit.piwigoclient.piwigoApi.upload.FileUploadDetails;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadFileLocalErrorResponse;

public class FileCompressionActor extends UploadActor {
    private static final String TAG = "FileCompressionActor";
    private boolean fileWasCompressed;

    public FileCompressionActor(@NonNull Context context, @NonNull UploadJob uploadJob, @NonNull ActorListener listener) {
        super(context, uploadJob, listener);
    }

    public void run(FileUploadDetails fileUploadDetails) {
        doAnyCompressionNeeded(fileUploadDetails);
    }

    public boolean isFileWasCompressed() {
        return fileWasCompressed;
    }

    private void doAnyCompressionNeeded(FileUploadDetails fileUploadDetails) {

        Uri compressedFile = null;
        if(fileUploadDetails.isVerified()) {
            // nothing to do here
            return;
        }
        if(fileUploadDetails.isCompressionWanted()) {
            compressedFile = fileUploadDetails.getCompressedFileUri();
            if (compressedFile == null) {
                if(fileUploadDetails.isUploadProcessStarted()) {
                    fileUploadDetails.resetStatus();
                }
                compressFile(fileUploadDetails);
            }
        }
    }

    private void compressFile(FileUploadDetails fileUploadDetails) {
        if (IOUtils.isPlayableMedia(getContext(), fileUploadDetails.getFileUri())) {
            compressPlayableMedia(fileUploadDetails);
        } else {
            if (isPhoto(getContext(), fileUploadDetails.getFileUri())) {
                compressPhoto(fileUploadDetails);
            }
        }
    }

    private boolean isPhoto(Context context, Uri fileUri) {
        String mimeType = IOUtils.getMimeType(context, fileUri);
        return MimeTypeFilter.matches(mimeType,"image/*");
    }

    private void compressPhoto(FileUploadDetails fileUploadDetails) {
        // need to compress this file
        ImageCompressor imageCompressor = new ImageCompressor(getContext());
        imageCompressor.compressImage(getUploadJob(), fileUploadDetails.getFileUri(), new ImageCompressor.ImageCompressorListener() {
            @Override
            public void onError(long jobId, PiwigoUploadFileLocalErrorResponse piwigoUploadFileLocalErrorResponse) {
                getListener().postNewResponse(jobId, piwigoUploadFileLocalErrorResponse);
            }

            public void onCompressionSuccess(DocumentFile compressedFile) {
                updateJobWithCompressedFile(fileUploadDetails.getFileUri(), compressedFile);
            }
        });
    }

    public void updateJobWithCompressedFile(Uri fileForUploadUri, DocumentFile compressedFile) {
        // we use this checksum to check the file was uploaded successfully
        FileUploadDetails fileUploadDetails = getUploadJob().getFileUploadDetails(fileForUploadUri);
        fileUploadDetails.setStatusCompressed();
        fileWasCompressed = true;
    }

    private void compressPlayableMedia(FileUploadDetails fileUploadDetails) {
        // need to compress this file
        final UploadFileCompressionListener listener = new UploadFileCompressionListener(getContext(), getUploadJob(), getListener());
        VideoCompressor videoCompressor = new VideoCompressor(getContext());
        if(videoCompressor.compressVideo(getUploadJob(), fileUploadDetails.getFileUri(), listener)) {
            fileWasCompressed = true;
        }
    }
}
