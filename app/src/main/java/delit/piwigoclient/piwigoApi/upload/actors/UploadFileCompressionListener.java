package delit.piwigoclient.piwigoApi.upload.actors;

import android.net.Uri;

import androidx.annotation.FloatRange;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.exoplayer2.ExoPlaybackException;

import delit.libs.util.progress.TaskProgressTracker;
import delit.piwigoclient.piwigoApi.upload.BasePiwigoUploadService;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadFileLocalErrorResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoVideoCompressionProgressUpdateResponse;

import static delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler.getNextMessageId;

public class UploadFileCompressionListener implements VideoCompressor.VideoCompressorListener {

    private boolean compressionComplete;
    private final BasePiwigoUploadService uploadService;
    private final UploadJob job;
    private Exception compressionError;
    private TaskProgressTracker singleFileCompressionProgressTracker;

    public UploadFileCompressionListener(BasePiwigoUploadService uploadService, UploadJob job) {
        this.uploadService = uploadService;
        this.job = job;
    }

    @Override
    public void onCompressionStarted(Uri inputFile, Uri outputFile) {
        singleFileCompressionProgressTracker = job.getTaskProgressTrackerForSingleFileCompression(inputFile);
    }

    @Override
    public void onCompressionComplete(Uri inputFile, Uri outputFile) {
        singleFileCompressionProgressTracker.markComplete();
        uploadService.postNewResponse(job.getJobId(), new PiwigoVideoCompressionProgressUpdateResponse(getNextMessageId(), inputFile, outputFile, 100));
        compressionComplete = true;
        // wake the main upload thread.
        synchronized (this) {
            notifyAll();
        }
    }

    @Override
    public void onCompressionSuccess(Uri inputFile, DocumentFile compressedFile) {
        uploadService.updateJobWithCompressedFile(job, inputFile, compressedFile);
    }

    @Override
    public boolean isCompressionComplete() {
        return compressionComplete;
    }

    @Override
    public void onCompressionProgress(Uri inputFile, Uri outputFile, @FloatRange(from = 0, to = 100) double compressionProgress, long mediaDurationMs) {
        int intCompProgress = (int) Math.round(compressionProgress);
        singleFileCompressionProgressTracker.setWorkDone(intCompProgress);
        uploadService.updateNotificationProgressText(job.getOverallUploadProgressInt());
        uploadService.postNewResponse(job.getJobId(), new PiwigoVideoCompressionProgressUpdateResponse(getNextMessageId(),  inputFile, outputFile, intCompProgress));
    }

    @Override
    public void onCompressionError(Uri inputFile, Uri outputFile, Exception e) {
        singleFileCompressionProgressTracker.markComplete();
        compressionError = e;
        // wake the main upload thread.
        synchronized (this) {
            notifyAll();
        }
    }

    @Override
    public void notifyUsersOfError(long jobId, PiwigoUploadFileLocalErrorResponse piwigoUploadFileLocalErrorResponse) {
        uploadService.postNewResponse(jobId, piwigoUploadFileLocalErrorResponse);
    }

    @Override
    public Exception getCompressionError() {
        return compressionError;
    }

    @Override
    public boolean isUnsupportedVideoFormat() {
        if (compressionError instanceof ExoPlaybackException) {
            ExoPlaybackException err = (ExoPlaybackException) compressionError;
            return err.type == ExoPlaybackException.TYPE_SOURCE;
        }
        return false;
    }
}
