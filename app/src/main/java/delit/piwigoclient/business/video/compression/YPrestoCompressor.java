package delit.piwigoclient.business.video.compression;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.crashlytics.android.Crashlytics;

import net.ypresto.androidtranscoder.MediaTranscoder;
import net.ypresto.androidtranscoder.format.MediaFormatStrategyPresets;
import net.ypresto.qtfaststart.QtFastStart;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.Future;

import androidx.annotation.RequiresApi;

import static net.ypresto.androidtranscoder.format.MediaFormatStrategyPresets.AUDIO_BITRATE_AS_IS;
import static net.ypresto.androidtranscoder.format.MediaFormatStrategyPresets.AUDIO_CHANNELS_AS_IS;

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class YPrestoCompressor {

    private static final String TAG = "YPrestoCompression";
    private Future<Void> mFuture;

    public void compressFile(Context context, File inputFile, File outputFile, ExoPlayerCompression.CompressionListener listener) throws FileNotFoundException {
        ContentResolver resolver = context.getContentResolver();
        ParcelFileDescriptor inputFileParcelFileDescriptor = resolver.openFileDescriptor(Uri.fromFile(inputFile), "r");
        CustomMediaTranscoder transcodeListener = new CustomMediaTranscoder(listener, outputFile, inputFileParcelFileDescriptor);
        Log.d(TAG, "transcoding into " + outputFile);
        mFuture = MediaTranscoder.getInstance().transcodeVideo(inputFileParcelFileDescriptor.getFileDescriptor(), outputFile.getAbsolutePath(),
                MediaFormatStrategyPresets.createAndroid720pStrategy(8000 * 250/*8000 * 1000*/, AUDIO_BITRATE_AS_IS/*128 * 1000*/, AUDIO_CHANNELS_AS_IS /*1*/), transcodeListener);
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
            File tmpFile = new File(input.getParentFile(), input.getName() + "final.mp4");
            try {
                QtFastStart.fastStart(input, tmpFile);
                input.delete();
                tmpFile.renameTo(input);
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
            if (tmpFile.exists()) {
                tmpFile.delete();
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
