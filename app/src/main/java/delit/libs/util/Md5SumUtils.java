package delit.libs.util;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Log;

import com.crashlytics.android.Crashlytics;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
            Crashlytics.logException(e);
            Log.e(TAG, "Exception while getting digest", e);
            throw new Md5SumException("Exception while getting digest", e);
        }

        BufferedInputStream is;
        try {
            //TODO will this be quicker to read? FileChannels? contentResolver.openFileDescriptor(uri, "r").getFileDescriptor();
            is = new BufferedInputStream(contentResolver.openInputStream(uri));

            byte[] buffer = new byte[8192];
            int read;
            try {
                while ((read = is.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
                byte[] md5sum = digest.digest();
                BigInteger bigInt = new BigInteger(1, md5sum);
                String output = bigInt.toString(16);
                // Fill to 32 chars
                output = String.format("%32s", output).replace(' ', '0');
                return output;
            } catch (IOException e) {
                Crashlytics.logException(e);
                throw new Md5SumException("Unable to process file for MD5", e);
            } finally {
                try {
                    is.close();
                } catch (IOException e) {
                    Crashlytics.logException(e);
                    Log.e(TAG, "Exception on closing MD5 input stream", e);
                }
            }
        } catch (FileNotFoundException e) {
            Crashlytics.logException(e);
            Log.e(TAG, "Exception while getting FileInputStream", e);
            throw new Md5SumException("Exception while getting FileInputStream", e);
        }
    }

    public static class Md5SumException extends Exception {
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
