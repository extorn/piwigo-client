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
import delit.libs.ui.util.ProgressListener;

/**
 * Created by gareth on 17/05/17.
 */

public class Md5SumUtils {

    private static final String TAG = "Md5Sum";

    public static String calculateMD5(ContentResolver contentResolver, Uri uri) throws RuntimeException, Md5SumException {
        // send in a no-op listener
        return calculateMD5(contentResolver, uri, percent -> {});
    }

    public static String calculateMD5(@NonNull ContentResolver contentResolver, @NonNull Uri uri, @NonNull ProgressListener progressListener) throws RuntimeException, Md5SumException {
        MessageDigest digest;
        progressListener.onProgress(0);
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
            long processedBytes = 0;
            int bytesSinceProgressReport = 0;
            int lastProgressReport = 0; // a % between 0 and 100
            int reportEveryBytes = 512 * 1024; // report progress once 512Kb as a maximum frequency
            try(FileChannel channel = new FileInputStream(pfd.getFileDescriptor()).getChannel()) {
                ByteBuffer bb = ByteBuffer.allocateDirect(65536);//64Kb
                double onePercentOfBytes = ((double)totalBytes)/100;
                while (channel.read(bb) >= 0) {
                    bb.flip(); // ready buffer for reading
                    bytesSinceProgressReport += bb.remaining();
                    processedBytes += bb.remaining();
                    digest.update(bb);
                    int progress = (int)Math.rint(0.95 * (((double)processedBytes) / onePercentOfBytes));
                    if(reportEveryBytes <= bytesSinceProgressReport && progress > lastProgressReport) {
                        bytesSinceProgressReport = 0;
                        lastProgressReport = progress;
                        progressListener.onProgress(progress); // only gets called if the progress has changed
                    }
                    bb.flip(); // ready buffer for writing
                }

                byte[] md5sum = digest.digest();
                BigInteger bigInt = new BigInteger(1, md5sum);
                String output = bigInt.toString(16);
                // Fill to 32 chars
                output = String.format("%32s", output).replace(' ', '0');
                progressListener.onProgress(100);
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
