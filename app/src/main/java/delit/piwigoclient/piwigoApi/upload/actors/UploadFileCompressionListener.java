package delit.piwigoclient.piwigoApi.upload.actors;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.exoplayer2.ExoPlaybackException;

import delit.libs.util.progress.DividableProgressTracker;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadFileLocalErrorResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoVideoCompressionProgressUpdateResponse;

import static delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler.getNextMessageId;

public class UploadFileCompressionListener extends UploadActor implements VideoCompressor.VideoCompressorListener {

    private boolean compressionComplete;
    private Exception compressionError;
    private DividableProgressTracker singleFileCompressionProgressTracker;

    public UploadFileCompressionListener(@NonNull Context context, @NonNull UploadJob uploadJob, @NonNull ActorListener listener, @NonNull DividableProgressTracker singleFileCompressionProgressTracker) {
        super(context, uploadJob, listener);
        this.singleFileCompressionProgressTracker = singleFileCompressionProgressTracker;
    }



    @Override
    public void onCompressionStarted(Uri inputFile, Uri outputFile) {
    }

    @Override
    public void onCompressionComplete(Uri inputFile, Uri outputFile) {
        singleFileCompressionProgressTracker.markComplete();
        getListener().postNewResponse(getUploadJob().getJobId(), new PiwigoVideoCompressionProgressUpdateResponse(getNextMessageId(), inputFile, outputFile, 100));
        compressionComplete = true;
        // wake the main upload thread.
        synchronized (this) {
            notifyAll();
        }
    }

    @Override
    public void onCompressionSuccess(Uri inputFile, DocumentFile compressedFile) {
        getUploadJob().getFileUploadDetails(inputFile).setCompressedFileUri(compressedFile.getUri());
    }

    @Override
    public boolean isCompressionComplete() {
        return compressionComplete;
    }

    @Override
    public void onCompressionProgress(Uri inputFile, Uri outputFile, @FloatRange(from = 0, to = 100) double compressionProgress, long mediaDurationMs) {
        int intCompProgress = (int) Math.round(compressionProgress);
        singleFileCompressionProgressTracker.setWorkDone(intCompProgress);
        getListener().updateNotificationProgressText(getUploadJob().getOverallUploadProgressInt());
        getListener().postNewResponse(getUploadJob().getJobId(), new PiwigoVideoCompressionProgressUpdateResponse(getNextMessageId(),  inputFile, outputFile, intCompProgress));
    }

    @Override
    public void onCompressionError(Uri inputFile, Uri outputFile, Exception e) {
        if(getUploadJob().isAllowUploadOfRawVideosIfIncompressible()) {
            //This is correct if can upload regardless because if rollback called, overall job progress will never meet 100%
            singleFileCompressionProgressTracker.markComplete();
        } else {
            singleFileCompressionProgressTracker.releaseParent();
        }
        compressionError = e;
        // wake the main upload thread.
        synchronized (this) {
            notifyAll();
        }
    }

    @Override
    public void notifyUsersOfError(long jobId, PiwigoUploadFileLocalErrorResponse piwigoUploadFileLocalErrorResponse) {
        getListener().postNewResponse(jobId, piwigoUploadFileLocalErrorResponse);
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
