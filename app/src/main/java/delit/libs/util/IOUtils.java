package delit.libs.util;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.core.os.EnvironmentCompat;
import androidx.documentfile.provider.DocumentFile;

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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import delit.libs.ui.util.ParcelUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.piwigoApi.upload.BasePiwigoUploadService;
import delit.piwigoclient.ui.file.DocumentFileFilter;

import static android.os.Build.VERSION_CODES.KITKAT;

/**
 * Created by gareth on 01/07/17.
 */

public class IOUtils {

    public static final String TAG = "IOUtils";

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
                    length += getFolderSize(file, recursive);
                }
            }
        }
        return length;
    }

    public static long getFolderSize(DocumentFile directory, boolean recursive) {
        long length = 0;
        DocumentFile[] fileList = directory.listFiles();
        for (DocumentFile file : fileList) {
            if (file.isDirectory()) {
                length += getFolderSize(file, recursive);
            } else if (recursive) {
                length += file.length();
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
            Crashlytics.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getAbsolutePath());
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading object from disk", e);
            }
        } catch (InvalidClassException e) {
            Crashlytics.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getAbsolutePath());
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading object from disk (class blueprint has altered since saved)", e);
            }
            deleteFileNow = true;
        } catch (ObjectStreamException e) {
            Crashlytics.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getAbsolutePath());
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading object from disk", e);
            }
            deleteFileNow = true;
        } catch (IOException e) {
            Crashlytics.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getAbsolutePath());
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading object from disk", e);
            }
        } catch (ClassNotFoundException e) {
            Crashlytics.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getAbsolutePath());
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading object from disk", e);
            }
            deleteFileNow = true;
        } finally {
            if (ois != null) {
                try {
                    ois.close();
                } catch (IOException e) {
                    Crashlytics.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getAbsolutePath());
                    Crashlytics.logException(e);
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

    public static <T extends Parcelable> T readParcelableFromDocumentFile(ContentResolver contentResolver, DocumentFile sourceFile, Class<T> parcelableClass) {
        boolean deleteFileNow = false;
        ObjectInputStream ois = null;
        InputStream is = null;
        try {

            is = contentResolver.openInputStream(sourceFile.getUri());
            if(is == null) {
                throw new IllegalArgumentException("Unable to open input stream to uri : " + sourceFile.getUri());
            }
            ois = new ObjectInputStream(new BufferedInputStream(is));
            Parcel p = ParcelUtils.readParcel(ois);
            T item = ParcelUtils.readParcelable(p, parcelableClass);
            p.recycle();
            return item;

        } catch (FileNotFoundException e) {
            Crashlytics.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getUri());
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading object from disk", e);
            }
        } catch (InvalidClassException e) {
            Crashlytics.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getUri());
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading object from disk (class blueprint has altered since saved)", e);
            }
            deleteFileNow = true;
        } catch (ObjectStreamException e) {
            Crashlytics.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getUri());
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading object from disk", e);
            }
            deleteFileNow = true;
        } catch (IOException e) {
            Crashlytics.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getUri());
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading object from disk", e);
            }
            deleteFileNow = true;
        } finally {
            if (ois != null) {
                try {
                    ois.close();
                } catch (IOException e) {
                    Crashlytics.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getUri());
                    Crashlytics.logException(e);
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Error closing stream when reading object from disk", e);
                    }
                }
            } else if(is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    Crashlytics.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getUri());
                    Crashlytics.logException(e);
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

    public static <T extends Serializable> T readObjectFromDocumentFile(ContentResolver contentResolver, DocumentFile sourceFile) {
        boolean deleteFileNow = false;
        ObjectInputStream ois = null;
        try {

            InputStream is = contentResolver.openInputStream(sourceFile.getUri());
            if(is == null) {
                throw new IllegalArgumentException("Unable to open input stream to uri : " + sourceFile.getUri());
            }
            ois = new ObjectInputStream(new BufferedInputStream(is));
            Object o = ois.readObject();
            return (T) o;

        } catch (FileNotFoundException e) {
            Crashlytics.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getUri());
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading object from disk", e);
            }
        } catch (InvalidClassException e) {
            Crashlytics.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getUri());
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading object from disk (class blueprint has altered since saved)", e);
            }
            deleteFileNow = true;
        } catch (ObjectStreamException e) {
            Crashlytics.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getUri());
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading object from disk", e);
            }
            deleteFileNow = true;
        } catch (IOException e) {
            Crashlytics.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getUri());
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading object from disk", e);
            }
        } catch (ClassNotFoundException e) {
            Crashlytics.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getUri());
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading object from disk", e);
            }
            deleteFileNow = true;
        } finally {
            if (ois != null) {
                try {
                    ois.close();
                } catch (IOException e) {
                    Crashlytics.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getUri());
                    Crashlytics.logException(e);
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
                Crashlytics.log(Log.ERROR, TAG, "Error writing Object to disk - unable to delete previous temporary file : " + destinationFile.getAbsolutePath());
                canContinue = false;
            }
        }
        if(canContinue && !destinationFile.getParentFile().isDirectory()) {
            if (!destinationFile.getParentFile().mkdir()) {
                Crashlytics.log(Log.ERROR, TAG, "Error writing Object to disk - unable to create parent folder : " + destinationFile.getParentFile().getAbsolutePath());
                canContinue = false;
            }
        }
        try {
            if (canContinue && !tmpFile.createNewFile()) {
                Crashlytics.log(Log.ERROR, TAG, "Error writing Object to disk - unable to create new temporary file : " + destinationFile.getAbsolutePath());
                canContinue = false;
            }
        } catch (IOException e) {
            Crashlytics.log(Log.ERROR, TAG, "Error writing Object to disk (creating new file) : " + destinationFile.getAbsolutePath());
            Crashlytics.logException(e);
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
            Crashlytics.log(Log.ERROR, TAG, "Error writing Object to disk : " + tmpFile.getAbsolutePath());
            Crashlytics.logException(e);
        } finally {
            if (oos != null) {
                try {
                    oos.close();
                } catch (IOException e) {
                    Crashlytics.log(Log.ERROR, TAG, "Error closing stream when writing Object to disk : " + tmpFile.getAbsolutePath());
                    Crashlytics.logException(e);
                }
            }
        }
        boolean canWrite = true;
        if (destinationFile.exists()) {
            if (!destinationFile.delete()) {
                Crashlytics.log(Log.ERROR, TAG, "Error writing Object to disk - unable to delete previous file to allow replace : " + destinationFile.getAbsolutePath());
                canWrite = false;
            }
        }
        if (canWrite) {
            return tmpFile.renameTo(destinationFile);
        }
        return false;
    }

    public static boolean saveParcelableToDocumentFile(@NonNull Context context, @NonNull DocumentFile destinationFile, Parcelable parcelable) {

        if (destinationFile.isDirectory()) {
            throw new RuntimeException("Not designed to work with a folder as a destination!");
        }
        DocumentFile folder = destinationFile.getParentFile();
        DocumentFile tmpFile;
        String filename = destinationFile.getName();
        if(filename == null) {
            filename = getFilename(destinationFile);
        }
        String mimeType = destinationFile.getType();
        if(mimeType == null) {
            mimeType = "application/octet-stream";
        }

        if(folder != null) {
            tmpFile = IOUtils.getTmpFile(folder, filename, "tmp", mimeType, true);
        } else {
            tmpFile = IOUtils.getTmpFile(context, filename, "tmp", mimeType, true);
        }
        if (tmpFile == null) {
            Crashlytics.log(Log.ERROR, TAG, "Error writing Object to disk - unable to create new temporary file : " + destinationFile.getName() + ".tmp");
            return false;
        }

        ObjectOutputStream oos = null;
        OutputStream os = null;
        try {
            os = context.getContentResolver().openOutputStream(tmpFile.getUri());
            oos = new ObjectOutputStream(new BufferedOutputStream(os));

            Parcel p = Parcel.obtain();
            ParcelUtils.writeParcelable(p, parcelable);
            ParcelUtils.writeParcel(oos, p);
            p.recycle();
            oos.flush();

        } catch (IOException e) {
            Crashlytics.log(Log.ERROR, TAG, "Error writing Object to disk : " + tmpFile.getUri());
            Crashlytics.logException(e);
        } finally {
            if (oos != null) {
                try {
                    oos.close();
                } catch (IOException e) {
                    Crashlytics.log(Log.ERROR, TAG, "Error closing stream when writing Object to disk : " + tmpFile.getUri());
                    Crashlytics.logException(e);
                }
            } else if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    Crashlytics.log(Log.ERROR, TAG, "Error closing stream when writing Object to disk : " + tmpFile.getUri());
                    Crashlytics.logException(e);
                }
            }
        }
        boolean canWrite = true;
        if (destinationFile.exists()) {
            if (!destinationFile.delete()) {
                Crashlytics.log(Log.ERROR, TAG, "Error writing Object to disk - unable to delete previous file to allow replace : " + destinationFile.getUri());
                canWrite = false;
            }
        }
        if (canWrite) {
            return tmpFile.renameTo(getFilename(destinationFile));
        }
        return false;
    }

    public static boolean saveObjectToDocumentFile(@NonNull Context context, @NonNull DocumentFile destinationFile, Serializable o) {
        if (destinationFile.isDirectory()) {
            throw new RuntimeException("Not designed to work with a folder as a destination!");
        }
        DocumentFile folder = destinationFile.getParentFile();
        DocumentFile tmpFile;
        String filename = destinationFile.getName();
        if(filename == null) {
            filename = getFilename(destinationFile);
        }
        String mimeType = destinationFile.getType();
        if(mimeType == null) {
            mimeType = "application/octet-stream";
        }

        if(folder != null) {
            tmpFile = IOUtils.getTmpFile(folder, filename, "tmp", mimeType, true);
        } else {
            tmpFile = IOUtils.getTmpFile(context, filename, "tmp", mimeType, true);
        }
        if (tmpFile == null) {
            Crashlytics.log(Log.ERROR, TAG, "Error writing Object to disk - unable to create new temporary file : " + destinationFile.getName() + ".tmp");
            return false;
        }

        ObjectOutputStream oos = null;
        OutputStream os = null;
        try {
            os = context.getContentResolver().openOutputStream(tmpFile.getUri());
            oos = new ObjectOutputStream(new BufferedOutputStream(os));
            oos.writeObject(o);
            oos.flush();
        } catch (IOException e) {
            Crashlytics.log(Log.ERROR, TAG, "Error writing Object to disk : " + tmpFile.getUri());
            Crashlytics.logException(e);
        } finally {
            if (oos != null) {
                try {
                    oos.close();
                } catch (IOException e) {
                    Crashlytics.log(Log.ERROR, TAG, "Error closing stream when writing Object to disk : " + tmpFile.getUri());
                    Crashlytics.logException(e);
                }
            } else if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    Crashlytics.log(Log.ERROR, TAG, "Error closing stream when writing Object to disk : " + tmpFile.getUri());
                    Crashlytics.logException(e);
                }
            }
        }
        boolean canWrite = true;
        if (destinationFile.exists()) {
            if (!destinationFile.delete()) {
                Crashlytics.log(Log.ERROR, TAG, "Error writing Object to disk - unable to delete previous file to allow replace : " + destinationFile.getUri());
                canWrite = false;
            }
        }
        if (canWrite) {
            return tmpFile.renameTo(getFilename(destinationFile));
        }
        return false;
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

    public static String getFileNameWithoutExt(String filename) {
        int extStartAtIdx = filename.lastIndexOf('.');
        return filename.substring(0, extStartAtIdx);
    }

    public static @Nullable String getFileExt(String filename, String mimeType) {
        String fileExt = getFileExt(filename);
        if(fileExt == null) {
            MimeTypeMap mime = MimeTypeMap.getSingleton();
            fileExt = mime.getExtensionFromMimeType(mimeType);
        }
        return fileExt;
    }

    public static @Nullable String getFileExt(String filename) {
        if(filename == null) {
            return null;
        }
        int extStartAtIdx = filename.lastIndexOf('.');
        if(extStartAtIdx < 0) {
            return null;
        }
        return filename.substring(extStartAtIdx + 1);
    }

    public static String getFileExt(@NonNull DocumentFile docFile) {
        String filename = getFilename(docFile);
        String fileExt = IOUtils.getFileExt(filename);
        if(fileExt == null) {
            MimeTypeMap mime = MimeTypeMap.getSingleton();
            fileExt = mime.getExtensionFromMimeType(docFile.getType());
        }
        return fileExt;
    }

    public static String getFileExt(@NonNull Context context, Uri uri) {
        String filename = getFilename(context, uri);
        String fileExt = IOUtils.getFileExt(filename);
        if(fileExt == null) {
            MimeTypeMap mime = MimeTypeMap.getSingleton();
            fileExt = mime.getExtensionFromMimeType(getMimeType(context, uri));
        }
        return fileExt;
    }

    public static String getMimeType(@NonNull Context context, Uri uri) {
        return context.getContentResolver().getType(uri);
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

    public static List<DocumentFile> getFilesNotBeingWritten(List<DocumentFile> matchingFiles, long timePeriodMillis) {
        List<DocumentFile> filesToUpload = new ArrayList<>(matchingFiles);
        Map<DocumentFile, Long> fileSizes = new HashMap<>(filesToUpload.size());
        for (DocumentFile file : filesToUpload) {
            if (file.length() > 0) {
                fileSizes.put(file, file.length());
            }
        }
        try {
            Thread.sleep(timePeriodMillis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        for (DocumentFile file : filesToUpload) {
            if (file.length() > 0) {
                Long oldLen = fileSizes.get(file);
                if (oldLen == null) {
                    fileSizes.put(file, file.length());
                } else if (oldLen != file.length()) {
                    fileSizes.remove(file);
                }
            }
        }
        return new ArrayList<>(fileSizes.keySet());
    }

    public static Set<String> getUniqueFileExts(Context context, Collection<Uri> files) {
        Set<String> exts = new HashSet<>();
        for (Uri f : files) {
            exts.add(IOUtils.getFileExt(context, f).toLowerCase());
        }
        return exts;
    }

    public static File changeFileExt(File file, String fileExt) {
        return new File(file.getParent(), getFileNameWithoutExt(file.getName()) + '.' + fileExt);
    }

    public static Charset getUtf8Charset() {
        if (Build.VERSION.SDK_INT >= KITKAT) {
            return StandardCharsets.UTF_8;
        } else {
            return Charset.forName("UTF-8");
        }
    }


    public static @Nullable String getFilename(@NonNull DocumentFile docFile) {
        String displayName = docFile.getName();
        if(displayName == null) {
            displayName = docFile.getUri().getLastPathSegment();
        }
        return displayName;
    }

    public static @Nullable String getFilename(@NonNull Context context, @NonNull Uri uri) {
        String displayName = null;
        if ("content".equals(uri.getScheme())) {
            try {
                DocumentFile rootDocFile = DocumentFile.fromTreeUri(context, uri);
                displayName = getFilename(rootDocFile);
            } catch(IllegalArgumentException e) {
                // this URI is not a tree document file
                DocumentFile docFile = DocumentFile.fromSingleUri(context, uri);
                displayName = getFilename(docFile);
            }
        } else if ("file".equals(uri.getScheme())) {
            displayName = uri.getLastPathSegment();
        } else {
            String path = uri.getPath();
            if(path != null) {
                File f = new File(path);
                if(f.exists()) {
                    return f.getName();
                } else {
                    Crashlytics.log(Log.DEBUG, TAG, "Unsupported URI scheme for retrieving filename : " + uri);
                    return null;
                }
            } else {
                Crashlytics.log(Log.DEBUG, TAG, "Unsupported URI scheme for retrieving filename : " + uri);
                return null;
            }
        }
        return displayName;
    }

    public static boolean exists(Context context, Uri uri) {
        DocumentFile docFile = DocumentFile.fromSingleUri(context, uri);
        return docFile != null && docFile.exists();
    }

    public static List<DocumentFile> toDocumentFileList(Context context, List<Uri> uris) {
        List<DocumentFile> output = new ArrayList<>(uris.size());
        for(Uri uri : uris) {
            output.add(DocumentFile.fromTreeUri(context, uri));
        }
        return output;
    }

    @RequiresApi(api = KITKAT)
    public static boolean setLastModified(Context context, Uri uri, long lastModified) {
        ContentValues updateValues = new ContentValues();
        updateValues.put(DocumentsContract.Document.COLUMN_LAST_MODIFIED, lastModified);
        int updated = context.getContentResolver().update(uri, updateValues, null, null);
        return updated == 1;
    }

    public static @Nullable File getFile(@Nullable Uri fileUri) throws IOException {
        if(fileUri == null) {
            return null;
        }
        String filePath = fileUri.getPath();
        if(filePath == null) {
            throw new IOException("Uri does not represent a local file " + fileUri);
        }
        return new File(filePath);
    }

    public static @Nullable Uri getLocalFileUri(@Nullable String value) {
        if(value == null) {
            return null;
        }
        Uri uri = Uri.parse(value);
        if(uri.getScheme() == null) {
            uri = Uri.fromFile(new File(value));
        }
        return uri;
    }

    public static boolean equals(DocumentFile doc1, DocumentFile doc2) {
        if(doc1 == null && doc2 == null) {
            return true;
        }
        if(doc1 == null || doc2 == null) {
            return false;
        }
        return ObjectUtils.areEqual(doc1.getUri(), doc2.getUri());
    }

    public static DocumentFile getTreeLinkedDocFile(Context context, Uri rootUri, Uri itemUri) {

        if(!(itemUri.getScheme().equals(rootUri.getScheme()) && itemUri.getAuthority().equals(rootUri.getAuthority()))) {
            throw new IllegalStateException("Something went badly wrong here! Uri not child of Uri:\n" + itemUri + "\n" + rootUri);
        }
        Uri treeUri = getTreeUri(rootUri);
        List<String> treePath = treeUri.getPathSegments();
        List<String> itemPath = itemUri.getPathSegments();
        if(itemPath.size() < treePath.size()) {
            throw new IllegalStateException("Something went badly wrong here! Uri not child of Uri:\n" + itemUri + "\n" + rootUri);
        }
        for(int i = 0; i < treePath.size(); i++) {
            if(!treePath.get(i).equals(itemPath.get(i))) {
                throw new IllegalStateException("Something went badly wrong here! Uri not child of Uri:\n" + itemUri + "\n" + rootUri);
            }
        }

//        String start = rootUri.getLastPathSegment();
        String childPathSegment = itemUri.getLastPathSegment();
        /*if(childPathSegment == null || start == null || !childPathSegment.startsWith(start)) {
            throw new IllegalStateException("Something went badly wrong here! Uri not child of Uri:\n" + itemUri + "\n" + rootUri);
        }
        childPathSegment = childPathSegment.substring(start.length());
        if(childPathSegment.indexOf('/') == 0) {
            childPathSegment = childPathSegment.substring(1);
        }*/

        DocumentFile rootDocFile = DocumentFile.fromTreeUri(context, rootUri);

        DocumentFile thisFile = rootDocFile;
        if(!rootUri.equals(itemUri)) {
            do {
                rootDocFile = thisFile;
                for (DocumentFile df : rootDocFile.listFiles()) {
                    if(df.getUri().equals(itemUri)) {
                        thisFile = df;
                        break;
                    }
                    String dfName = df.getName();
                    if (dfName != null && childPathSegment.startsWith(dfName)) {
                        int stripChars = dfName.length();
                        if(stripChars < childPathSegment.length()) {
                            stripChars++;
                        }
                        childPathSegment = childPathSegment.substring(stripChars);
                        thisFile = df;
                        break;
                    }
                }
            } while(!thisFile.getUri().equals(itemUri) && (!childPathSegment.isEmpty() && rootDocFile != thisFile));
        }

        return thisFile;
    }

    public static DocumentFile getTmpFile(@NonNull Context context, @NonNull String baseFilename, @NonNull String fileExt, @NonNull String mimeType, boolean deleteIfExists) {
        DocumentFile tmpFolder = BasePiwigoUploadService.getTmpUploadFolder(context);
        return getTmpFile(tmpFolder, baseFilename, fileExt, mimeType, deleteIfExists);
    }

    public static DocumentFile getTmpFile(@NonNull DocumentFile outputFolder, @NonNull String baseFilename, @NonNull String fileExt, @NonNull String mimeType) {
        return getTmpFile(outputFolder, baseFilename, fileExt, mimeType, false);
    }

    public static DocumentFile getTmpFile(@NonNull DocumentFile outputFolder, @NonNull String baseFilename, @NonNull String fileExt, @NonNull String mimeType, boolean deleteIfExists) {

        String tmpFilename = baseFilename;
        int i = 0;
        if(deleteIfExists) {
            DocumentFile file = outputFolder.findFile(tmpFilename);
            if(file != null) {
                file.delete();
            }
        } else {
            while (null != outputFolder.findFile(tmpFilename)) {
                i++;
                tmpFilename = baseFilename + '_' + i + fileExt;
            }
        }
        return outputFolder.createFile(mimeType, tmpFilename);
    }

    /**
     * copy data from one uri to another uri
     * @param context
     * @param copyFrom
     * @param copyTo
     * @return null if operation failed
     */
    public static @Nullable Uri copyDocumentUriDataToUri(Context context, Uri copyFrom, @NonNull Uri copyTo) throws IOException {

        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;
        try {
            InputStream is = context.getContentResolver().openInputStream(copyFrom);
            if(is == null) {
                throw new IllegalStateException("Unable to open input stream to uri " + copyFrom);
            }
            bis = new BufferedInputStream(is);
            OutputStream os = context.getContentResolver().openOutputStream(copyTo);
            if(os == null) {
                throw new IllegalStateException("Unable to open output stream to uri " + copyTo);
            }
            bos = new BufferedOutputStream(os);
            IOUtils.write(bis, bos);
            return copyTo;
        } catch (IOException e) {
            throw e;
        } finally {
            if(bos != null) {
                try {
                    bos.close();
                } catch (IOException e) {
                    Crashlytics.logException(e);
                }
            }
            if(bis != null) {
                try {
                    bis.close();
                } catch (IOException e) {
                    Crashlytics.logException(e);
                }
            }
        }
    }

    /**
     *
     * Safe to use on versions below 19 (KITKAT) -  {@link Build.VERSION_CODES#KITKAT}
     * @param context
     * @param uri
     * @return
     */
    public static boolean isDirectory(@NonNull Context context, @NonNull Uri uri) {

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return DocumentFile.fromSingleUri(context, uri).isDirectory();
        } else {
            try {
                File f = getFile(uri);
                return f.isDirectory();
            } catch (IOException e) {
               throw new IllegalArgumentException("Uri provided was not for a local file : " + uri);
            }
        }


    }

    public static Uri getTreeUri(Uri uri) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            return DocumentsContract.buildTreeDocumentUri(uri.getAuthority(), DocumentsContract.getDocumentId(uri));
        } else {
            if(!uri.getScheme().equals("file")) {
                List<String> pathSegments = uri.getPathSegments();

                int toIdx = pathSegments.size() - 1;
                if (pathSegments.size() > 3 && pathSegments.get(toIdx - 1).equals("document")) {
                    toIdx -= 1;
                    Uri.Builder b = new Uri.Builder();
                    b.authority(uri.getAuthority());
                    b.scheme(uri.getScheme());
                    for (int i = 0; i < toIdx; i++) {
                        b.appendPath(pathSegments.get(i));
                    }
                    return b.build();
                }
            }
            // already a tree URI
            return uri;
        }
    }

    public static boolean hasUriPermissions(Context context, Uri uri, int permissions) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return true;
        }
        if(uri.getScheme().equals("file")) {
            return Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q;
        }
        List<UriPermission> uriPerms = context.getContentResolver().getPersistedUriPermissions();
        int readPerm = permissions & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        int writePerm = permissions & Intent.FLAG_GRANT_READ_URI_PERMISSION;

        Uri treeUri = IOUtils.getTreeUri(uri);
        for(UriPermission p : uriPerms) {
            if(p.getUri().equals(uri) || p.getUri().equals(treeUri)) {
                if(p.isReadPermission()) {
                    readPerm = 0;
                }
                if(p.isWritePermission()) {
                    writePerm = 0;
                }
                if(readPerm == 0 && readPerm == writePerm) {
                    return true; // all permissions found.
                }
            }
        }
        return false;
    }

    public static List<DocumentFile> filterDocumentFiles(DocumentFile[] files, DocumentFileFilter filter) {
        if(files == null) {
            return new ArrayList<>(0);
        }
        List<DocumentFile> acceptableFiles = new ArrayList<>(files.length);
        for(int i = 0; i < files.length; i++) {
            DocumentFile file = files[i];
            if(filter.accept(file)) {
                acceptableFiles.add(file);
            }
        }
        return acceptableFiles;
    }

    /**
     * We have to try each root so as to get the shortest path to the linked folder.
     * @param context
     * @param initialFolder
     * @return
     */
    @RequiresApi(api = KITKAT)
    public static @Nullable DocumentFile getDocumentFileForUriLinkedToAnAccessibleRoot(Context context, Uri initialFolder) {
        List<UriPermission> persistedPermissions = context.getContentResolver().getPersistedUriPermissions();
        DocumentFile match = null;
        for(UriPermission perm : persistedPermissions) {
            try {
                DocumentFile item = IOUtils.getTreeLinkedDocFile(context, perm.getUri(), initialFolder);
                if(match == null || getTreeDepth(item) < getTreeDepth(match)) {
                    match = item;
                }
            } catch(IllegalStateException e) {
                //ignore - this isn't the right root. We'll try the next.
            }
        }
        return match;
    }

    private static int getTreeDepth(DocumentFile file) {
        int depth = 0;
        while(file.getParentFile() != null) {
            file = file.getParentFile();
            depth++;
        }
        return depth;
    }

    public static @Nullable DocumentFile getRootDocFile(@Nullable DocumentFile initialFolder) {
        DocumentFile folder = initialFolder;
        while(folder.getParentFile() != null) {
            folder = folder.getParentFile();
        }
        return folder;
    }

    @RequiresApi(api = KITKAT)
    public static <T extends Collection<Uri>> T removeUrisWeLackPermissionFor(@NonNull Context context, @NonNull T uris) {
        // remove the persisted uri permission for each
        Set<Uri> heldPerms = new HashSet<>();
        for(UriPermission actualHeldPerm : context.getContentResolver().getPersistedUriPermissions()) {
            heldPerms.add(actualHeldPerm.getUri());
        }
        if(uris.retainAll(heldPerms)) {
            Crashlytics.log(Log.INFO, TAG, "Some permissions to remove are no longer held (removing silently)");
        }
        return uris;
    }

    public static Map<String,String> getUniqueExtAndMimeTypes(DocumentFile[] files) {
        Map<String, String> map = new HashMap<>();
        if(files == null) {
            return map;
        }
        for (DocumentFile f : files) {
            String ext = IOUtils.getFileExt(f);
            if (ext != null) {
                map.put(ext, f.getType());
            }
        }
        return map;
    }

    public static Map<String,String> getUniqueExtAndMimeTypes(File[] files) {
        Map<String, String> map = new HashMap<>();
        if(files == null) {
            return map;
        }
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        for(File f : files) {
            String ext = getFileExt(f.getName());
            if(ext != null) {
                map.put(ext.toLowerCase(), mimeTypeMap.getMimeTypeFromExtension(ext));
            }
        }
        return map;
    }
}
