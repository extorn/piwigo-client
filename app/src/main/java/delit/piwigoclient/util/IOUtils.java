package delit.piwigoclient.util;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.channels.FileChannel;

/**
 * Created by gareth on 01/07/17.
 */

public class IOUtils {
    public static void copy(File src, File dst) throws IOException {
        FileInputStream inStream = new FileInputStream(src);
        //TODO check what happens if file exists
        FileOutputStream outStream = new FileOutputStream(dst);
        FileChannel inChannel = inStream.getChannel();
        FileChannel outChannel = outStream.getChannel();
        inChannel.transferTo(0, inChannel.size(), outChannel);
        inStream.close();
        outStream.close();
    }

    public static <T extends Serializable> T readObjectFromFile(File sourceFile) {
        boolean deleteFileNow = false;
        ObjectInputStream ois = null;
        try {

            ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(sourceFile)));
            Object o = ois.readObject();
            return (T) o;

        } catch (FileNotFoundException e) {
            Log.e("IOUtils", "Error reading object from disk", e);
        } catch (IOException e) {
            Log.e("IOUtils", "Error reading object from disk", e);
        } catch (ClassNotFoundException e) {
            Log.e("IOUtils", "Error reading object from disk", e);
            deleteFileNow = true;
        } finally {
            if (ois != null) {
                try {
                    ois.close();
                } catch (IOException e) {
                    Log.d("IOUtils", "Error closing stream when reading object from disk", e);
                }
            }
            if (deleteFileNow) {
                sourceFile.delete();
            }
        }
        return null;
    }

    public static void saveObjectToFile(File destinationFile, Serializable o) {
        boolean canContinue = true;
        if(destinationFile.isDirectory()) {
            throw new RuntimeException("Not designed to work with a folder as a destination!");
        }
        File tmpFile = new File(destinationFile.getParentFile(), destinationFile.getName()+".tmp");
        if (tmpFile.exists()) {
            if (!tmpFile.delete()) {
                Log.d("IOUtils", "Error writing job to disk - unable to delete previous temporary file");
                canContinue = false;
            }
        }
        if (!canContinue) {
            return;
        }
        ObjectOutputStream oos = null;
        try {
            oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(tmpFile)));
            oos.writeObject(o);
            oos.flush();
        } catch (IOException e) {
            Log.d("IOUtils", "Error writing Object to disk", e);
        } finally {
            if (oos != null) {
                try {
                    oos.close();
                } catch (IOException e) {
                    Log.d("IOUtils", "Error closing stream when writing Object to disk", e);
                }
            }
        }
        boolean canWrite = true;
        if (destinationFile.exists()) {
            if (!destinationFile.delete()) {
                Log.d("IOUtils", "Error writing Object to disk - unable to delete previous file to allow replace");
                canWrite = false;
            }
        }
        if (canWrite) {
            tmpFile.renameTo(destinationFile);
        }
    }
}
