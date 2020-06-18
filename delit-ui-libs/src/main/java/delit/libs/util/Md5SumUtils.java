package delit.libs.util;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import delit.libs.core.util.Logging;

/**
 * Created by gareth on 17/05/17.
 */

public class Md5SumUtils {

    private static final String TAG = "Md5Sum";

    public static String calculateMD5(ContentResolver contentResolver, Uri uri) throws RuntimeException, Md5SumException {
        MessageDigest digest;
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
            try(FileChannel channel = new FileInputStream(pfd.getFileDescriptor()).getChannel()) {
                ByteBuffer bb = ByteBuffer.allocateDirect(8192);
                while (channel.read(bb) >= 0) {
                    bb.flip(); // ready buffer for reading
                    digest.update(bb);
                    bb.flip(); // ready buffer for writing
                }

                byte[] md5sum = digest.digest();
                BigInteger bigInt = new BigInteger(1, md5sum);
                String output = bigInt.toString(16);
                // Fill to 32 chars
                output = String.format("%32s", output).replace(' ', '0');
                return output;

            }


        } catch (FileNotFoundException e) {
            Logging.recordException(e);
            Logging.log(Log.ERROR, TAG, "Exception while getting FileInputStream");
            throw new Md5SumException("Exception while getting FileInputStream", e);
        } catch (IOException e) {
            Logging.recordException(e);
            Logging.log(Log.ERROR, TAG, "Unable to process file for MD5");
            throw new Md5SumException("Unable to process file for MD5", e);
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
