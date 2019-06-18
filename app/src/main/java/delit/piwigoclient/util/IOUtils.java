package delit.piwigoclient.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.core.os.EnvironmentCompat;

import com.crashlytics.android.Crashlytics;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import delit.piwigoclient.BuildConfig;

/**
 * Created by gareth on 01/07/17.
 */

public class IOUtils {

    public static void write(InputStream src, OutputStream dst) throws IOException {
        BufferedInputStream inStream = new BufferedInputStream(src);
        byte[] buf = new byte[10240];
        int read = -1;
        do {
            read = inStream.read(buf, 0, buf.length);
            if (read > 0) {
                dst.write(buf, 0, read);
            }
        } while (read >= 0);
        inStream.close();
        dst.close();
    }

    public static void write(InputStream src, File dst) throws IOException {
        BufferedInputStream inStream = new BufferedInputStream(src);
        //TODO check what happens if file exists
        FileOutputStream outStream = new FileOutputStream(dst);
        ReadableByteChannel inChannel = Channels.newChannel(inStream);
            FileChannel outChannel = outStream.getChannel();
            outChannel.transferFrom(inChannel, 0, Long.MAX_VALUE);
        inStream.close();
        outStream.close();
    }

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
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e("IOUtils", "Error reading object from disk", e);
            }
        } catch (InvalidClassException e) {
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e("IOUtils", "Error reading object from disk (class blueprint has altered since saved)", e);
            }
            deleteFileNow = true;
        } catch (ObjectStreamException e) {
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e("IOUtils", "Error reading object from disk", e);
            }
            deleteFileNow = true;
        } catch (IOException e) {
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e("IOUtils", "Error reading object from disk", e);
            }
        } catch (ClassNotFoundException e) {
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e("IOUtils", "Error reading object from disk", e);
            }
            deleteFileNow = true;
        } finally {
            if (ois != null) {
                try {
                    ois.close();
                } catch (IOException e) {
                    Crashlytics.logException(e);
                    if (BuildConfig.DEBUG) {
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

    public static boolean saveObjectToFile(File destinationFile, Serializable o) {
        boolean canContinue = true;
        if (destinationFile.isDirectory()) {
            throw new RuntimeException("Not designed to work with a folder as a destination!");
        }
        File tmpFile = new File(destinationFile.getParentFile(), destinationFile.getName() + ".tmp");
        if (tmpFile.exists()) {
            if (!tmpFile.delete()) {
                if (BuildConfig.DEBUG) {
                    Log.d("IOUtils", "Error writing job to disk - unable to delete previous temporary file");
                }
                canContinue = false;
            }
        }
        if(canContinue && !destinationFile.getParentFile().isDirectory()) {
            if (!destinationFile.getParentFile().mkdir()) {
                if (BuildConfig.DEBUG) {
                    Log.d("IOUtils", "Error writing job to disk - unable to create parent folder");
                }
                canContinue = false;
            }
        }
        try {
            if (canContinue && !tmpFile.createNewFile()) {
                if (BuildConfig.DEBUG) {
                    Log.d("IOUtils", "Error writing job to disk - unable to create new temporary file");
                }
                canContinue = false;
            }
        } catch (IOException e) {
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.d("IOUtils", "Error writing Object to disk (creating new file)", e);
            }
        }

        if (!canContinue) {
            return false;
        }

        ObjectOutputStream oos = null;
        try {
            oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(tmpFile)));
            oos.writeObject(o);
            oos.flush();
        } catch (IOException e) {
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.d("IOUtils", "Error writing Object to disk", e);
            }
        } finally {
            if (oos != null) {
                try {
                    oos.close();
                } catch (IOException e) {
                    Crashlytics.logException(e);
                    if (BuildConfig.DEBUG) {
                        Log.d("IOUtils", "Error closing stream when writing Object to disk", e);
                    }
                }
            }
        }
        boolean canWrite = true;
        if (destinationFile.exists()) {
            if (!destinationFile.delete()) {
                if (BuildConfig.DEBUG) {
                    Log.d("IOUtils", "Error writing Object to disk - unable to delete previous file to allow replace");
                }
                canWrite = false;
            }
        }
        if (canWrite) {
            return tmpFile.renameTo(destinationFile);
        }
        return false;
    }

    private static Executor FILESEEKEREXECUTOR = Executors.newFixedThreadPool(5);

    /**
     * returns a list of all available sd cards paths, or null if not found.
     *
     * @param includePrimaryExternalStorage set to true if you wish to also include the path of the primary external storage
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static List<String> getSdCardPaths(final Context context, final boolean includePrimaryExternalStorage)
    {
//        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//            StorageManager sm = (StorageManager) context.getApplicationContext().getSystemService(Context.STORAGE_SERVICE);
//            StorageVolume volume = sm.getPrimaryStorageVolume();
//        }


        final File[] externalCacheDirs= ContextCompat.getExternalCacheDirs(context);
        if(externalCacheDirs==null||externalCacheDirs.length==0)
            return null;
        if(externalCacheDirs.length==1)
        {
            if(externalCacheDirs[0]==null)
                return null;
            final String storageState= EnvironmentCompat.getStorageState(externalCacheDirs[0]);
            if(!Environment.MEDIA_MOUNTED.equals(storageState))
                return null;
            if(!includePrimaryExternalStorage&& Build.VERSION.SDK_INT>= Build.VERSION_CODES.HONEYCOMB&&Environment.isExternalStorageEmulated())
                return null;
        }
        final List<String> result=new ArrayList<>();
        if(includePrimaryExternalStorage||externalCacheDirs.length==1)
            result.add(getRootOfInnerSdCardFolder(externalCacheDirs[0]));
        for(int i=1;i<externalCacheDirs.length;++i)
        {
            final File file=externalCacheDirs[i];
            if(file==null)
                continue;
            final String storageState=EnvironmentCompat.getStorageState(file);
            if(Environment.MEDIA_MOUNTED.equals(storageState))
                result.add(getRootOfInnerSdCardFolder(externalCacheDirs[i]));
        }
        if(result.isEmpty())
            return null;
        return result;
    }

    /** Given any file/folder inside an sd card, this will return the path of the sd card */
    private static String getRootOfInnerSdCardFolder(File file)
    {
        if(file==null)
            return null;
        final long totalSpace=file.getTotalSpace();
        while(true)
        {
            final File parentFile=file.getParentFile();
            if(parentFile==null||parentFile.getTotalSpace()!=totalSpace)
                return file.getAbsolutePath();
            file=parentFile;
        }
    }

    public static String getFileExt(String filename) {
        int extStartAtIdx = filename.lastIndexOf('.');
        return filename.substring(extStartAtIdx + 1);
    }

    public static String toNormalizedText(long cacheBytes) {
        long KB = 1024;
        long MB = KB * 1024;
        String text = " ";
        if (cacheBytes < KB) {
            text += String.format(Locale.getDefault(), "%1$d Bytes", cacheBytes);
        } else if (cacheBytes < MB) {
            double kb = ((double) cacheBytes) / KB;
            text += String.format(Locale.getDefault(), "%1$.1f KB", kb);
        } else {
            double mb = ((double) cacheBytes) / MB;
            text += String.format(Locale.getDefault(), "%1$.1f MB", mb);
        }
        return text;
    }

    public static ByteBuffer deepCopy(ByteBuffer orig) {
        int pos = orig.position(), lim = orig.limit();
        try {
            orig.position(0).limit(orig.capacity()); // set range to entire buffer
            ByteBuffer toReturn = deepCopyVisible(orig); // deep copy range
            toReturn.position(pos).limit(lim); // set range to original
            return toReturn;
        } finally // do in finally in case something goes wrong we don't bork the orig
        {
            orig.position(pos).limit(lim); // restore original
        }
    }

    public static ByteBuffer deepCopyVisible(ByteBuffer orig) {
        int pos = orig.position();
        try {
            ByteBuffer toReturn;
            // try to maintain implementation to keep performance
            if (orig.isDirect())
                toReturn = ByteBuffer.allocateDirect(orig.remaining());
            else
                toReturn = ByteBuffer.allocate(orig.remaining());

            toReturn.put(orig);
            toReturn.order(orig.order());

            return (ByteBuffer) toReturn.position(0);
        } finally {
            orig.position(pos);
        }
    }

    public static File[] getFilesNotBeingWritten(File[] matchingFiles, long timePeriodMillis) {
        ArrayList<File> filesToUpload = new ArrayList(Arrays.asList(matchingFiles));
        Map<File, Long> fileSizes = new HashMap<>(filesToUpload.size());
        for (File file : filesToUpload) {
            if (file.length() > 0) {
                fileSizes.put(file, file.length());
            }
        }
        try {
            Thread.sleep(timePeriodMillis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        for (File file : filesToUpload) {
            if (file.length() > 0) {
                Long oldLen = fileSizes.get(file);
                if (oldLen == null) {
                    fileSizes.put(file, file.length());
                } else if (oldLen != file.length()) {
                    fileSizes.remove(file);
                }

            }
        }
        return fileSizes.keySet().toArray(new File[fileSizes.size()]);
    }
}
