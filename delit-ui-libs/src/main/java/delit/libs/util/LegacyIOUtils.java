package delit.libs.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.os.EnvironmentCompat;
import androidx.documentfile.provider.DocumentFile;

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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import delit.libs.BuildConfig;
import delit.libs.core.util.Logging;

public class LegacyIOUtils {
    private static final String TAG = "LegacyIO";

    public static long getFolderSize(File directory, boolean recursive) {
        long length = 0;
        File[] fileList = directory.listFiles();
        if (fileList != null) {
            for (File file : fileList) {
                if (file.isFile()) {
                    boolean isSymLink = false;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        isSymLink = Files.isSymbolicLink(file.toPath());
                    }
                    length += isSymLink ? 0 : file.length();
                } else if (recursive) {
                    length += getFolderSize(file, true);
                }
            }
        }
        return length;
    }

    public static <T extends Serializable> T readObjectFromFile(File sourceFile) {
        boolean deleteFileNow = false;
        ObjectInputStream ois = null;
        try {

            ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(sourceFile)));
            Object o = ois.readObject();
            return (T) o;

        } catch (FileNotFoundException e) {
            Logging.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getAbsolutePath());
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading object from disk", e);
            }
        } catch (InvalidClassException e) {
            Logging.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getAbsolutePath());
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading object from disk (class blueprint has altered since saved)", e);
            }
            deleteFileNow = true;
        } catch (ObjectStreamException e) {
            Logging.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getAbsolutePath());
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading object from disk", e);
            }
            deleteFileNow = true;
        } catch (IOException e) {
            Logging.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getAbsolutePath());
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading object from disk", e);
            }
        } catch (ClassNotFoundException e) {
            Logging.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getAbsolutePath());
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading object from disk", e);
            }
            deleteFileNow = true;
        } finally {
            if (ois != null) {
                try {
                    ois.close();
                } catch (IOException e) {
                    Logging.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getAbsolutePath());
                    Logging.recordException(e);
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Error closing stream when reading object from disk", e);
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
                Logging.log(Log.ERROR, TAG, "Error writing Object to disk - unable to delete previous temporary file : " + destinationFile.getAbsolutePath());
                canContinue = false;
            }
        }
        if(canContinue && !destinationFile.getParentFile().isDirectory()) {
            if (!destinationFile.getParentFile().mkdir()) {
                Logging.log(Log.ERROR, TAG, "Error writing Object to disk - unable to create parent folder : " + destinationFile.getParentFile().getAbsolutePath());
                canContinue = false;
            }
        }
        try {
            if (canContinue && !tmpFile.createNewFile()) {
                Logging.log(Log.ERROR, TAG, "Error writing Object to disk - unable to create new temporary file : " + destinationFile.getAbsolutePath());
                canContinue = false;
            }
        } catch (IOException e) {
            Logging.log(Log.ERROR, TAG, "Error writing Object to disk (creating new file) : " + destinationFile.getAbsolutePath());
            Logging.recordException(e);
        }

        if (!canContinue) {
            return false;
        }

        try(ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(tmpFile)))) {
            oos.writeObject(o);
            oos.flush();
        } catch (IOException e) {
            Logging.log(Log.ERROR, TAG, "Error writing Object to disk : " + tmpFile.getAbsolutePath());
            Logging.recordException(e);
        }
        boolean canWrite = true;
        if (destinationFile.exists()) {
            if (!destinationFile.delete()) {
                Logging.log(Log.ERROR, TAG, "Error writing Object to disk - unable to delete previous file to allow replace : " + destinationFile.getAbsolutePath());
                canWrite = false;
            }
        }
        if (canWrite) {
            return tmpFile.renameTo(destinationFile);
        }
        return false;
    }

    /** Given any file/folder inside an sd card, this will return the path of the sd card */
    static String getRootOfInnerSdCardFolder(File file)
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

    public static File changeFileExt(File file, String fileExt) {
        return new File(file.getParent(), IOUtils.getFileNameWithoutExt(file.getName()) + '.' + fileExt);
    }

    public static @Nullable
    File getFile(@Nullable Uri fileUri) throws IOException {
        if(fileUri == null) {
            return null;
        }
        String filePath = fileUri.getPath();
        if(filePath == null) {
            throw new IOException("Uri does not represent a local file " + fileUri);
        }
        return new File(filePath);
    }

    public static Map<String,String> getUniqueExtAndMimeTypes(File[] files) {
        Map<String, String> map = new HashMap<>();
        if(files == null) {
            return map;
        }
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        for(File f : files) {
            String ext = IOUtils.getFileExt(f.getName());
            if(ext != null) {
                map.put(ext.toLowerCase(), mimeTypeMap.getMimeTypeFromExtension(ext));
            }
        }
        return map;
    }

    /**
     * returns a list of all available sd cards paths, or null if not found.
     *
     * @param includePrimaryExternalStorage set to true if you wish to also include the path of the primary external storage
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static List<String> getSdCardPaths(final Context context, final boolean includePrimaryExternalStorage)
    {
//        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//            StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
//            StorageVolume volume = sm.getPrimaryStorageVolume();
//        }


        final File[] externalCacheDirs= ContextCompat.getExternalCacheDirs(context);
        if (externalCacheDirs.length == 0)
            return null;
        if(externalCacheDirs.length==1)
        {
            if(externalCacheDirs[0]==null)
                return null;
            final String storageState= EnvironmentCompat.getStorageState(externalCacheDirs[0]);
            if(!Environment.MEDIA_MOUNTED.equals(storageState))
                return null;
            if(!includePrimaryExternalStorage && Environment.isExternalStorageEmulated())
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

    public static @Nullable DocumentFile getDocFile(@Nullable Uri initialFolder) {
        try {
            return getDocFile(getFile(initialFolder));
        } catch (IOException e) {
            Logging.recordException(e);
        }
        return null;
    }

    public static @Nullable DocumentFile getDocFile(@Nullable File file) {
        if(file != null) {
            return DocumentFile.fromFile(file);
        }
        return null;
    }
}
