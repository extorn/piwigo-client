package delit.piwigoclient.util;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.nio.channels.FileChannel;
import java.util.Locale;

import delit.piwigoclient.BuildConfig;

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
            if(BuildConfig.DEBUG) {
                Log.e("IOUtils", "Error reading object from disk", e);
            }
        } catch(InvalidClassException e) {
            if(BuildConfig.DEBUG) {
                Log.e("IOUtils", "Error reading object from disk (class blueprint has altered since saved)", e);
            }
            deleteFileNow = true;
        } catch (ObjectStreamException e) {
            if(BuildConfig.DEBUG) {
                Log.e("IOUtils", "Error reading object from disk", e);
            }
            deleteFileNow = true;
        } catch (IOException e) {
            if(BuildConfig.DEBUG) {
                Log.e("IOUtils", "Error reading object from disk", e);
            }
        } catch (ClassNotFoundException e) {
            if(BuildConfig.DEBUG) {
                Log.e("IOUtils", "Error reading object from disk", e);
            }
            deleteFileNow = true;
        } finally {
            if (ois != null) {
                try {
                    ois.close();
                } catch (IOException e) {
                    if(BuildConfig.DEBUG) {
                        Log.d("IOUtils", "Error closing stream when reading object from disk", e);
                    }
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
                if(BuildConfig.DEBUG) {
                    Log.d("IOUtils", "Error writing job to disk - unable to delete previous temporary file");
                }
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
            if(BuildConfig.DEBUG) {
                Log.d("IOUtils", "Error writing Object to disk", e);
            }
        } finally {
            if (oos != null) {
                try {
                    oos.close();
                } catch (IOException e) {
                    if(BuildConfig.DEBUG) {
                        Log.d("IOUtils", "Error closing stream when writing Object to disk", e);
                    }
                }
            }
        }
        boolean canWrite = true;
        if (destinationFile.exists()) {
            if (!destinationFile.delete()) {
                if(BuildConfig.DEBUG) {
                    Log.d("IOUtils", "Error writing Object to disk - unable to delete previous file to allow replace");
                }
                canWrite = false;
            }
        }
        if (canWrite) {
            tmpFile.renameTo(destinationFile);
        }
    }

    public static String toNormalizedText(double cacheBytes) {
        long KB = 1024;
        long MB = KB * 1024;
        String text = " ";
        if(cacheBytes < KB) {
            text += String.format(Locale.getDefault(), "%1$.0f Bytes", cacheBytes);
        } else if(cacheBytes < MB) {
            double kb = (cacheBytes / KB);
            text += String.format(Locale.getDefault(), "%1$.1f KB", kb);
        } else {
            double mb = (cacheBytes / MB);
            text += String.format(Locale.getDefault(), "%1$.1f MB", mb);
        }
        return text;
    }
}
