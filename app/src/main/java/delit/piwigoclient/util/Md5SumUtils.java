package delit.piwigoclient.util;

import android.util.Log;

import com.crashlytics.android.Crashlytics;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by gareth on 17/05/17.
 */

public class Md5SumUtils {

    private static final String TAG = "Md5Sum";

    public static String calculateMD5(File updateFile) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            Crashlytics.logException(e);
            Log.e(TAG, "Exception while getting digest", e);
            return null;
        }

        InputStream is;
        try {
            is = new BufferedInputStream(new FileInputStream(updateFile));

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
                throw new RuntimeException("Unable to process file for MD5", e);
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
            return null;
        }
    }
}
