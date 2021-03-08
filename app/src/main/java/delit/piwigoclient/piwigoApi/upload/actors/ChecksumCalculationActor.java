package delit.piwigoclient.piwigoApi.upload.actors;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import delit.libs.core.util.Logging;
import delit.libs.util.Md5SumUtils;
import delit.libs.util.progress.DividableProgressTracker;
import delit.libs.util.progress.TrackerUpdatingProgressListener;
import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.upload.FileUploadDetails;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadFileLocalErrorResponse;

import static delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler.getNextMessageId;

public class ChecksumCalculationActor extends UploadActor {

    private static final String TAG = "ChecksumCalculationActor";

    public ChecksumCalculationActor(@NonNull Context context, @NonNull UploadJob uploadJob, @NonNull ActorListener listener) {
        super(context, uploadJob, listener);
    }

    public boolean run() {
        calculateChecksums(getContext());
        return true;
    }

    public void calculateChecksums(@NonNull Context context) {
        DividableProgressTracker overallChecksumProgressTracker = getUploadJob().getTaskProgressTrackerForAllChecksumCalculation();
        overallChecksumProgressTracker.setWorkDone(0);//reset the progress.
        try {
            for(FileUploadDetails fud : getUploadJob ().getFilesForUpload()) {
                if(fud.isChecksumNeeded()) {
                    DividableProgressTracker fileChecksumProgressTracker = overallChecksumProgressTracker.addChildTask("checksum calc : "+fud.getFileUri(), 100, 1); // tick one file off. Each file has 0 - 100% completion
                    calculateChecksum(context, fud, fileChecksumProgressTracker);
                }
            }
        } finally {
            if(!overallChecksumProgressTracker.isComplete()) {
                Logging.log(Log.WARN,TAG, "Checksums progress tracker not complete");
            }
//            overallChecksumProgressTracker.markComplete();
        }
    }

    /**
     *
     * @param context
     * @param fud
     * @param singleFileChecksumProgressTracker
     */
    public void calculateChecksum(Context context, FileUploadDetails fud, DividableProgressTracker singleFileChecksumProgressTracker) {

        try {
            Uri fileForChecksumCalc = fud.getFileToBeUploaded();
            if(fud.hasCompressedFile()) {
                if (FileUploadDetails.isFileAvailable(context, fileForChecksumCalc)) {
                    fileForChecksumCalc = fud.getCompressedFileUri();
                } else {
                    fud.resetStatus();
                    Logging.log(Log.WARN, TAG, "Previously Compressed file no longer available. Rolling back to original");
                }
            }

            if (!FileUploadDetails.isFileAvailable(context, fileForChecksumCalc)) {
                fud.setProcessingFailed();
                // Remove file from upload list
                Md5SumUtils.Md5SumException error = new Md5SumUtils.Md5SumException(context.getString(R.string.error_file_not_found));
                // notify listeners of the error
                getListener().recordAndPostNewResponse(getUploadJob(), new PiwigoUploadFileLocalErrorResponse(getNextMessageId(), fud.getFileUri(), true, error));
            } else {
                // calculate checksum
                try {
                    TrackerUpdatingProgressListener md5Listener = new TrackerUpdatingProgressListener(singleFileChecksumProgressTracker);
                    String checksum = Md5SumUtils.calculateMD5(context.getContentResolver(), fileForChecksumCalc, md5Listener);
                    fud.setChecksum(fileForChecksumCalc, checksum);
                } catch (Md5SumUtils.Md5SumException e) {
                    fud.setProcessingFailed();
                    getListener().recordAndPostNewResponse(getUploadJob(), new PiwigoUploadFileLocalErrorResponse(getNextMessageId(), fud.getFileUri(), true, e));
                    Logging.log(Log.DEBUG, TAG, "Error calculating MD5 hash for file. Noting failure");
                }
            }
        } finally {
            singleFileChecksumProgressTracker.markComplete();
        }
    }
}
