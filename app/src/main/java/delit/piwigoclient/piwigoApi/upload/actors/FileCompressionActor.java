package delit.piwigoclient.piwigoApi.upload.actors;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.content.MimeTypeFilter;
import androidx.documentfile.provider.DocumentFile;

import delit.libs.util.IOUtils;
import delit.libs.util.progress.DividableProgressTracker;
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

        if(fileUploadDetails.isVerified()) {
            // nothing to do here
            return;
        }
        if(fileUploadDetails.isCompressionWanted()) {
            if (fileUploadDetails.isCompressionNeeded()) {
                if(fileUploadDetails.isUploadProcessStarted()) {
                    fileUploadDetails.resetStatus();
                }
                compressFile(fileUploadDetails);
            }
        }
    }

    private void compressFile(FileUploadDetails fileUploadDetails) {
        // 95% of the compression is compressing the data. 5% is used later to calc a checksum. (search for use of getTaskProgressTrackerForSingleFileCompression)
        DividableProgressTracker progressTracker = getUploadJob().getTaskProgressTrackerForSingleFileCompression(fileUploadDetails.getFileUri()).addChildTask("compression", 100, 95);

        if (IOUtils.isPlayableMedia(getContext(), fileUploadDetails.getFileUri())) {
            compressPlayableMedia(fileUploadDetails, progressTracker);
        } else {
            if (isPhoto(getContext(), fileUploadDetails.getFileUri())) {
                compressPhoto(fileUploadDetails, progressTracker);
            }
        }
    }

    private boolean isPhoto(Context context, Uri fileUri) {
        String mimeType = IOUtils.getMimeType(context, fileUri);
        return MimeTypeFilter.matches(mimeType,"image/*");
    }

    private void compressPhoto(FileUploadDetails fileUploadDetails, DividableProgressTracker progressTracker) {
        // need to compress this file
        ImageCompressor imageCompressor = new ImageCompressor(getContext());
        imageCompressor.compressImage(getUploadJob(), fileUploadDetails.getFileUri(), new ImageCompressor.ImageCompressorListener() {
            @Override
            public void onError(long jobId, PiwigoUploadFileLocalErrorResponse piwigoUploadFileLocalErrorResponse) {
                getListener().postNewResponse(jobId, piwigoUploadFileLocalErrorResponse);
                progressTracker.releaseParent();
            }

            @Override
            public void onCompressionSuccess(DocumentFile compressedFile) {
                updateJobWithCompressedFile(fileUploadDetails.getFileUri(), compressedFile);
                progressTracker.markComplete();
            }
        });
    }

    public void updateJobWithCompressedFile(Uri fileForUploadUri, DocumentFile compressedFile) {
        // we use this checksum to check the file was uploaded successfully
        FileUploadDetails fileUploadDetails = getUploadJob().getFileUploadDetails(fileForUploadUri);
        fileUploadDetails.setStatusCompressed();
        fileWasCompressed = true;
        DividableProgressTracker tracker = getUploadJob().getTaskProgressTrackerForSingleFileCompression(fileUploadDetails.getFileUri());
        tracker = tracker.addChildTask("compressed checksum", 100, 5);
        new ChecksumCalculationActor(getContext(), getUploadJob(), getListener()).calculateChecksum(getContext(), fileUploadDetails, tracker);
    }

    private void compressPlayableMedia(FileUploadDetails fileUploadDetails, DividableProgressTracker progressTracker) {
        // need to compress this file
        final UploadFileCompressionListener listener = new UploadFileCompressionListener(getContext(), getUploadJob(), getListener(), progressTracker);
        VideoCompressor videoCompressor = new VideoCompressor(getContext());
        if(videoCompressor.compressVideo(getUploadJob(), fileUploadDetails.getFileUri(), listener)) {
            fileWasCompressed = true;
        }
    }
}
