package delit.libs.util;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import delit.libs.core.util.Logging;
import delit.libs.util.progress.BasicProgressTracker;
import delit.libs.util.progress.DividableProgressTracker;
import delit.libs.util.progress.ProgressListener;
import delit.libs.util.progress.SimpleProgressListener;

/**
 * Created by gareth on 17/05/17.
 */

public class Md5SumUtils {

    private static final String TAG = "Md5Sum";

    public static String calculateMD5(ContentResolver contentResolver, Uri uri) throws RuntimeException, Md5SumException {
        // send in a no-op listener
        return calculateMD5(contentResolver, uri, new SimpleProgressListener(1.0));
    }

    public static String calculateMD5(@NonNull ContentResolver contentResolver, @NonNull Uri uri, @NonNull ProgressListener progressListener) throws RuntimeException, Md5SumException {
        MessageDigest digest;
        progressListener.onProgress(0, true);
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            Logging.recordException(e);
            Logging.log(Log.ERROR, TAG, "Exception while getting digest");
            throw new Md5SumException("Exception while getting digest", e);
        }

        try(ParcelFileDescriptor pfd = contentResolver.openFileDescriptor(uri, "r")) {

            if(pfd == null) {
                throw new FileNotFoundException("File descriptor unavailable (file likely doesn't exist) : " + uri);
            }
            long totalBytes = pfd.getStatSize();
            DividableProgressTracker taskProgressTracker = new DividableProgressTracker("overall md5 calculation", 100, progressListener); // percent
            BasicProgressTracker fileReaderTask = taskProgressTracker.addChildTask("reading data", totalBytes,75);

            try(FileChannel channel = new FileInputStream(pfd.getFileDescriptor()).getChannel()) {
                ByteBuffer bb = ByteBuffer.allocateDirect(65536);//64Kb
                while (channel.read(bb) >= 0) {
                    bb.flip(); // ready buffer for reading
                    fileReaderTask.incrementWorkDone(bb.remaining());
                    digest.update(bb);
                    bb.flip(); // ready buffer for writing
                }
                fileReaderTask.markComplete();
                byte[] md5sum = digest.digest();
                BigInteger bigInt = new BigInteger(1, md5sum);
                String output = bigInt.toString(16);
                // Fill to 32 chars
                output = String.format("%32s", output).replace(' ', '0');
                taskProgressTracker.markComplete();
                return output;
            }


        } catch (FileNotFoundException e) {
            Logging.recordException(e);
            Logging.log(Log.ERROR, TAG, "Exception while getting FileInputStream for uri " + uri);
            throw new Md5SumException("Exception while getting FileInputStream for uri " + uri, e);
        } catch (IOException e) {
            Logging.recordException(e);
            Logging.log(Log.ERROR, TAG, "Unable to process file for MD5 (uri" + uri +")");
            throw new Md5SumException("Unable to process file for MD5 (uri" + uri +")", e);
        }
    }

    public static class Md5SumException extends Exception {
        private static final long serialVersionUID = -5822209681895668615L;

        public Md5SumException(String message) {
            super(message);
        }

        public Md5SumException(Throwable cause) {
            super(cause);
        }

        public Md5SumException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
