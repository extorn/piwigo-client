package delit.piwigoclient.business.video.compression;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.crashlytics.android.Crashlytics;

import net.ypresto.androidtranscoder.MediaTranscoder;
import net.ypresto.androidtranscoder.format.MediaFormatStrategy;
import net.ypresto.androidtranscoder.format.MediaFormatStrategyPresets;
import net.ypresto.qtfaststart.QtFastStart;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static net.ypresto.androidtranscoder.format.MediaFormatStrategyPresets.AUDIO_BITRATE_AS_IS;
import static net.ypresto.androidtranscoder.format.MediaFormatStrategyPresets.AUDIO_CHANNELS_AS_IS;

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class YPrestoCompressor {

    private static final String TAG = "YPrestoCompression";
    private Future<Void> mFuture;

    public void invokeFileCompression(Context context, File inputFile, File outputFile, ExoPlayerCompression.CompressionListener listener) {
        ContentResolver resolver = context.getContentResolver();
        ParcelFileDescriptor inputFileParcelFileDescriptor = null;
        try {
            inputFileParcelFileDescriptor = resolver.openFileDescriptor(Uri.fromFile(inputFile), "r");
        } catch (FileNotFoundException e) {
            listener.onCompressionError(e);
            return;
        }
        CustomMediaTranscoder transcodeListener = new CustomMediaTranscoder(listener, outputFile, inputFileParcelFileDescriptor);
        Log.d(TAG, "transcoding into " + outputFile);
        MediaFormatStrategy formatStrategy = new CustomYPrestoFormatStrategy(8000 * 250, AUDIO_BITRATE_AS_IS/*128 * 1000*/, AUDIO_CHANNELS_AS_IS /*1*/);
        formatStrategy = MediaFormatStrategyPresets.createAndroid720pStrategy(8000 * 250);
        mFuture = MediaTranscoder.getInstance().transcodeVideo(inputFileParcelFileDescriptor.getFileDescriptor(), outputFile.getAbsolutePath(), formatStrategy, transcodeListener);
    }

    public void waitForCompressorToFinish() {
        while (!mFuture.isCancelled() && !mFuture.isDone()) {
            try {
                mFuture.get();
            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void cancel() {
        mFuture.cancel(true);
    }

    class CustomMediaTranscoder implements MediaTranscoder.Listener {

        private final ParcelFileDescriptor inputFileParcelFileDescriptor;
        private File outputFile;
        private ExoPlayerCompression.CompressionListener listener;
        private long mediaDurationMs; //TODO get this from the transcoder
        private double compressionProgress = -1;
        private double minReportedChange = 5f;

        public CustomMediaTranscoder(ExoPlayerCompression.CompressionListener listener, File outputFile, ParcelFileDescriptor inputFileParcelFileDescriptor) {
            this.listener = listener;
            this.inputFileParcelFileDescriptor = inputFileParcelFileDescriptor;
            this.outputFile = outputFile;
        }

        @Override
        public void onTranscodeProgress(double progress) {
            double newCompressionProgress = progress * 100;
            if (compressionProgress < 0) {
                listener.onCompressionStarted();
            }
            if (newCompressionProgress - compressionProgress > minReportedChange) {
                compressionProgress = newCompressionProgress;
                listener.onCompressionProgress(compressionProgress, mediaDurationMs);
            }
        }

        @Override
        public void onTranscodeCompleted() {
            closeInputFile();
            makeTranscodedFileStreamable(outputFile);
            listener.onCompressionComplete();
        }

        private void makeTranscodedFileStreamable(File input) {
            Log.d(TAG, "Enabling streaming for transcoded MP4");
            File tmpFile = new File(input.getParentFile(), input.getName() + ".streaming.mp4");
            try {
                boolean wroteFastStartFile = QtFastStart.fastStart(input, tmpFile);
                if (wroteFastStartFile) {
                    boolean deletedOriginal = input.delete();
                    if (!deletedOriginal) {
                        Crashlytics.log(Log.ERROR, TAG, "Error deleting streaming input file");
                    }
                    boolean renamed = tmpFile.renameTo(new File(tmpFile.getParentFile(), input.getName()));
                    if (!renamed) {
                        Crashlytics.log(Log.ERROR, TAG, "Error renaming streaming output file");
                    }
                }
            } catch (IOException e) {
                Crashlytics.log(Log.ERROR, TAG, "Error enabling streaming for transcoded MP4");
                Crashlytics.logException(e);
            } catch (QtFastStart.MalformedFileException e) {
                Crashlytics.log(Log.ERROR, TAG, "Error enabling streaming for transcoded MP4");
                Crashlytics.logException(e);
            } catch (QtFastStart.UnsupportedFileException e) {
                Crashlytics.log(Log.ERROR, TAG, "Error enabling streaming for transcoded MP4");
                Crashlytics.logException(e);
            }
        }

        private void closeInputFile() {
            try {
                inputFileParcelFileDescriptor.close();
            } catch (IOException e) {
                Log.w("Error while closing", e);
            }
        }

        @Override
        public void onTranscodeCanceled() {
//            listener.onCompressionCancelled();
            Crashlytics.log(Log.ERROR, TAG, "transcode media file cancelled");
            closeInputFile();
        }

        @Override
        public void onTranscodeFailed(Exception exception) {
//            listener.onCompressionFailed();
            Crashlytics.log(Log.ERROR, TAG, "Unable to transcode media file");
            Crashlytics.logException(exception);
            closeInputFile();
            listener.onCompressionError(exception);
        }
    }
}
