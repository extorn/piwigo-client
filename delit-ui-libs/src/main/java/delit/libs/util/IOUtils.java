package delit.libs.util;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.MimeTypeFilter;
import androidx.documentfile.provider.DocumentFile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
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
import java.math.BigDecimal;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import delit.libs.BuildConfig;
import delit.libs.R;
import delit.libs.core.util.Logging;
import delit.libs.ui.util.ParcelUtils;

import static android.os.Build.VERSION_CODES.KITKAT;

/**
 * Created by gareth on 01/07/17.
 */

public class IOUtils {

    public static final int URI_PERMISSION_READ_WRITE = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
    public static final int URI_PERMISSION_READ = Intent.FLAG_GRANT_READ_URI_PERMISSION;
    public static final int URI_PERMISSION_WRITE = Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

    private static final String TAG = "IOUtils";

    public static void write(InputStream src, OutputStream dst) throws IOException {
        try(BufferedInputStream inStream = new BufferedInputStream(src); BufferedOutputStream outStream = new BufferedOutputStream(dst);) {
            byte[] buf = new byte[10240];
            int read = -1;
            do {
                read = inStream.read(buf, 0, buf.length);
                if (read > 0) {
                    outStream.write(buf, 0, read);
                }
            } while (read >= 0);
        }
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

    public static <T extends Parcelable> T readParcelableFromDocumentFile(ContentResolver contentResolver, DocumentFile sourceFile, Class<T> parcelableClass) {
        boolean deleteFileNow = false;
        try(InputStream is = contentResolver.openInputStream(sourceFile.getUri());) {
            if(is == null) {
                throw new IllegalArgumentException("Unable to open input stream to uri : " + sourceFile.getUri());
            }
            try(ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(is))) {
                Parcel p = ParcelUtils.readParcel(ois);
                T item = ParcelUtils.readParcelable(p, parcelableClass);
                p.recycle();
                return item;
            }
        } catch (FileNotFoundException e) {
            Logging.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getUri());
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading object from disk", e);
            }
        } catch (InvalidClassException e) {
            Logging.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getUri());
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading object from disk (class blueprint has altered since saved)", e);
            }
            deleteFileNow = true;
        } catch (ObjectStreamException e) {
            Logging.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getUri());
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading object from disk", e);
            }
            deleteFileNow = true;
        } catch (IOException e) {
            Logging.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getUri());
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading object from disk", e);
            }
            deleteFileNow = true;
        } catch (OutOfMemoryError e) {
            Logging.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getUri());
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading object from disk", e);
            }
            deleteFileNow = true;
        }
        if (deleteFileNow) {
            sourceFile.delete();
        }
        return null;
    }

    public static <T extends Serializable> T readObjectFromDocumentFile(ContentResolver contentResolver, DocumentFile sourceFile) {
        boolean deleteFileNow = false;
        try(InputStream is = contentResolver.openInputStream(sourceFile.getUri())) {
            if(is == null) {
                throw new IllegalArgumentException("Unable to open input stream to uri : " + sourceFile.getUri());
            }
            try(ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(is))) {
                Object o = ois.readObject();
                return (T) o;
            }
        } catch (FileNotFoundException e) {
            Logging.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getUri());
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading object from disk", e);
            }
        } catch (InvalidClassException e) {
            Logging.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getUri());
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading object from disk (class blueprint has altered since saved)", e);
            }
            deleteFileNow = true;
        } catch (ObjectStreamException e) {
            Logging.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getUri());
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading object from disk", e);
            }
            deleteFileNow = true;
        } catch (IOException e) {
            Logging.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getUri());
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading object from disk", e);
            }
        } catch (ClassNotFoundException e) {
            Logging.log(Log.ERROR, TAG, "Error loading class fromm file : " + sourceFile.getUri());
            Logging.recordException(e);
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error reading object from disk", e);
            }
            deleteFileNow = true;
        }
        if (deleteFileNow) {
            sourceFile.delete();
        }
        return null;
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

        if(folder == null) {
            folder = DocumentFile.fromFile(context.getExternalCacheDir());
        }
        tmpFile = IOUtils.getTmpFile(folder, filename, "tmp", mimeType, true);

        if (tmpFile == null) {
            Logging.log(Log.ERROR, TAG, "Error writing parcelable to disk - unable to create new temporary file : " + destinationFile.getName() + ".tmp");
            return false;
        }

        try(OutputStream os = context.getContentResolver().openOutputStream(tmpFile.getUri())) {
            try(ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(os))) {

                Parcel p = Parcel.obtain();
                ParcelUtils.writeParcelable(p, parcelable);
                ParcelUtils.writeParcel(oos, p);
                oos.flush();
                p.recycle();

            }
        } catch (IOException e) {
            Logging.log(Log.ERROR, TAG, "Error writing parcelable to disk : " + tmpFile.getUri());
            Logging.recordException(e);
        }
        boolean canWrite = true;
        if (destinationFile.exists() && destinationFile.isFile()) {
            if (!destinationFile.delete()) {
                boolean deleted =false;
                if("file".equals(destinationFile.getUri().getScheme())) {
                    deleted = new File(Objects.requireNonNull(destinationFile.getUri().getPath())).delete();
                }
                if(deleted) {
                    Logging.log(Log.WARN, TAG, "Had to convert to file for delete to work - weird");
                } else {
                    Logging.log(Log.ERROR, TAG, "Error writing Parcelable to disk - unable to delete previous file to allow replace : " + destinationFile.getUri());
                    canWrite = false;
                }
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

        if (folder == null) {
            File extCacheFolder = context.getExternalCacheDir();
            if (extCacheFolder == null) {
                throw new RuntimeException("Unable to get destination folder for tmp file");
            }
            folder = DocumentFile.fromFile(extCacheFolder);
        }
        tmpFile = IOUtils.getTmpFile(folder, filename, "tmp", mimeType, true);
        if (tmpFile == null) {
            Logging.log(Log.ERROR, TAG, "Error writing Object to disk - unable to create new temporary file : " + destinationFile.getName() + ".tmp");
            return false;
        }

        try(OutputStream os = context.getContentResolver().openOutputStream(tmpFile.getUri())) {
            if(os == null) {
                throw new IOException("Error ");
            }
            try(ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(os))) {
                oos.writeObject(o);
                oos.flush();
            }
        } catch (IOException e) {
            Logging.log(Log.ERROR, TAG, "Error writing Object to disk : " + tmpFile.getUri());
            Logging.recordException(e);
        }
        boolean canWrite = true;
        if (destinationFile.exists()) {
            if (!destinationFile.delete()) {
                Logging.log(Log.ERROR, TAG, "Error writing Object to disk - unable to delete previous file to allow replace(2) : " + destinationFile.getUri());
                canWrite = false;
            }
        }
        if (canWrite) {
            return tmpFile.renameTo(getFilename(destinationFile));
        }
        return false;
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

    public static @Nullable String getFileExt(@Nullable String filename) {
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

    // access ordered list.
    private static final LinkedHashMap<String,String> knownExtsToMimes = new LinkedHashMap<>(0, 0.75f, true);

    /**
     * Bomb proof get MimeType. (I hope)
     * @param context
     * @param uri any uri to a resource (file or otherwise)
     * @return mime type for the uri
     */
    public static @Nullable String getMimeType(@NonNull Context context, @NonNull Uri uri) {
        String mimeType = null;
        if("content".equals(uri.getScheme())) {
            mimeType = context.getContentResolver().getType(uri);
        }
        if(mimeType == null) {
            String fileExt = null;
            if("file".equals(uri.getScheme())) {
                String path = uri.getPath();
                if(path != null) {
                    fileExt = IOUtils.getFileExt(new File(path).getName());
                }
            }
            if(fileExt == null) {
                fileExt = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            }
            mimeType = getMimeType(fileExt);
        }
        return mimeType;
    }

    public static String getMimeType(@Nullable String fileExt) {
        if(fileExt != null) {
            String mimeType = knownExtsToMimes.get(fileExt.toLowerCase());
            if(mimeType == null) {
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExt);
                knownExtsToMimes.put(fileExt.toLowerCase(), mimeType);
                if(knownExtsToMimes.size() > 50) {
                    knownExtsToMimes.entrySet().iterator().remove();
                }
            }
            return mimeType;
        }
        return null;
    }

    public static String bytesToNormalizedText(long sizeInBytes) {
        long KB = 1024;
        long MB = KB * 1024;
        String text = " ";
        if (sizeInBytes < KB) {
            text += String.format(Locale.getDefault(), "%1$d Bytes", sizeInBytes);
        } else if (sizeInBytes < MB) {
            double kb = ((double) sizeInBytes) / KB;
            text += String.format(Locale.getDefault(), "%1$.1f KB", kb);
        } else {
            double mb = ((double) sizeInBytes) / MB;
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
            Thread.sleep(timePeriodMillis); // wait x milliseconds before double checking the file size etc (if its in use, it will have altered)
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
            String fileExt = IOUtils.getFileExt(context, f);
            if(fileExt != null) {
                exts.add(fileExt.toLowerCase());
            }
        }
        return exts;
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
                if(rootDocFile == null) {
                    return null;
                }
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
                    Logging.log(Log.DEBUG, TAG, "Unsupported URI scheme for retrieving filename : " + uri);
                    return null;
                }
            } else {
                Logging.log(Log.DEBUG, TAG, "Unsupported URI scheme for retrieving filename : " + uri);
                return null;
            }
        }
        return displayName;
    }

    public static boolean exists(Context context, Uri uri) {
        return getFilesize(context, uri) >= 0;
    }

    public static List<DocumentFile> toDocumentFileList(Context context, List<Uri> uris) {
        List<DocumentFile> output = new ArrayList<>(uris.size());
        for(Uri uri : uris) {
            output.add(DocumentFile.fromTreeUri(context, uri));
        }
        return output;
    }

    @RequiresApi(api = KITKAT)
    public static boolean setLastModified(Context context, @NonNull Uri uri, long lastModified) {
        String uriScheme = uri.getScheme();
        if(uriScheme == null || "file".equals(uri.getScheme())) {
            File f = new File(Objects.requireNonNull(uri.getPath()));
            if(f.exists() && lastModified > 0) {
                return f.setLastModified(lastModified);
            } else {
                return false;
            }
        }
        ContentValues updateValues = new ContentValues();
        updateValues.put(DocumentsContract.Document.COLUMN_LAST_MODIFIED, lastModified);
        int updated = context.getContentResolver().update(uri, updateValues, null, null);
        return updated == 1;
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

    /**
     * Get a document file for an item Uri where getParent works to the provided root Uri
     * @param context
     * @param rootUri a known root Uri
     * @param itemUri the item Uri
     * @throws IllegalStateException if it was not possible for any reason.
     * @return DocumentFile which is chained together all the way from the itemUri to the rootUri allowing traversal
     */
    public static DocumentFile getTreeLinkedDocFile(@NonNull Context context, @NonNull Uri rootUri, @NonNull Uri itemUri) {
        if(itemUri.getScheme() == null || itemUri.getAuthority() == null || !(itemUri.getScheme().equals(rootUri.getScheme()) && itemUri.getAuthority().equals(rootUri.getAuthority()))) {
            Logging.log(Log.WARN, TAG, "Something went badly wrong here! Uri not child of Uri:\n%1$s\n%2$s", itemUri, rootUri);
            throw new IllegalStateException("Something went badly wrong here! Uri not child of Uri:\n" + itemUri + "\n" + rootUri);
        }
        if("file".equals(itemUri.getScheme())) {
            return DocumentFile.fromFile(new File(Objects.requireNonNull(itemUri.getPath())));
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            return getTreeLinkedDocFileO(context, rootUri, itemUri);
        } else {
            return getTreeLinkedDocFilePreO(context, rootUri, itemUri);
        }
    }

    private static String getTreePathFromPathElements(List<String> pathSegments) {
        boolean capture = false;
        for(String segment : pathSegments) {
            if(capture) {
                return segment;
            }
            if(segment.equals("document")) {
                capture = true;
            }
        }
        return "";
    }

    private static String getTreeBaseFromPathElements(List<String> pathSegments) {
        boolean capture = false;
        for(String segment : pathSegments) {
            if(capture) {
                return segment;
            }
            if(segment.equals("tree")) {
                capture = true;
            }
        }
        return "";
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private static @NonNull DocumentFile getTreeLinkedDocFileO(Context context, Uri rootUri, Uri itemUri) {
        // this works really well - but its android O and above only. :-(
        /*
                tree
                primary:DCIM


                tree
                primary:
                document
                primary:DCIM/Camera
         */
        DocumentFile rootedDocFile = DocumentFile.fromTreeUri(context, rootUri);

        List<String> rootPathSegments = rootUri.getPathSegments();
        List<String> itemPathSegments = itemUri.getPathSegments();
        String rootTree = getTreeBaseFromPathElements(rootPathSegments);
        String itemTree = getTreeBaseFromPathElements(itemPathSegments);
        String extraRoot = rootTree.replaceAll("^" + itemTree, "");
        if (rootTree.equals(extraRoot)) {
            // The item is from a more specific root or has different root entirely than that currently offered.
            if(!itemTree.startsWith(rootTree)) {
                // The path is not a match.
                Logging.log(Log.WARN, TAG, "Incompatible Paths could not be relinked, root: %1$s <- item: %2$s", rootUri, itemUri);
                throw new IllegalStateException("Something went badly wrong here! Uri not child of Uri:\n" + itemUri + "\n" + rootUri);
            } else {
                String extraItemPathElement = itemTree.replaceAll("^"+rootTree, "");
                String itemPath = getTreePathFromPathElements(itemPathSegments);
                String adjustedItemPath;
                if(itemTree.equals(rootTree + extraItemPathElement)) {
                    adjustedItemPath = extraItemPathElement;
                } else {
                    adjustedItemPath = extraItemPathElement + '/' + itemPath;
                }
                String[] newPathElements = adjustedItemPath.split("/");
                for (String pe : newPathElements) {
                    if(pe.isEmpty()) {
                        continue;
                    }
                    rootedDocFile = rootedDocFile.findFile(pe);
                    if (rootedDocFile == null) {
                        //NOTE this is likely because the child has been deleted.
                        Logging.log(Log.WARN, TAG, "Unable to find item within new root: %1$s <- itemPath: %2$s", rootUri, adjustedItemPath);
                        throw new IllegalStateException("Something went badly wrong here! Uri not child of Uri:\n" + itemUri + "\n" + rootUri);
                    }
                }
                return Objects.requireNonNull(rootedDocFile);
            }
        } else {
            // the item is within the root somewhere
            String pathBase = extraRoot; //DCIM
            String itemPath = getTreePathFromPathElements(itemPathSegments);
            String adjustedItemPath = itemPath.replaceAll("^" + pathBase, "");
            if (pathBase.length() > 0 && itemPath.equals(adjustedItemPath)) {
                // The path is not a match.
                Logging.log(Log.WARN, TAG, "Incompatible Paths could not be relinked, root: %1$s <- item: %2$s", rootUri, itemUri);
                throw new IllegalStateException("Something went badly wrong here! Uri not child of Uri:\n" + itemUri + "\n" + rootUri);
            } else {
                String newItemPath = adjustedItemPath;
                // trim the item tree from the front (if possible - it will be if the item is inside the same tree)
                newItemPath = newItemPath.replaceFirst("^"+itemTree, "");
                String[] newPathElements = newItemPath.split("/");
                for (String pe : newPathElements) {
                    if(pe.isEmpty()) {
                        continue;
                    }
                    rootedDocFile = rootedDocFile.findFile(pe);
                    if (rootedDocFile == null) {
                        //NOTE this is likely because the child has been deleted.
                        Logging.log(Log.WARN, TAG, "Unable to find item within new root: %1$s <- itemPath: %2$s", rootUri, newItemPath);
                        throw new IllegalStateException("Something went badly wrong here! Uri not child of Uri:\n" + itemUri + "\n" + rootUri);
                    }
                }
                return Objects.requireNonNull(rootedDocFile);
            }
        }
    }

    //FIXME This is not likely to be working!
    public static DocumentFile getTreeLinkedDocFilePreO(Context context, Uri rootUri, Uri itemUri) {

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
        if("file".equals(itemUri.getScheme())) {
            return LegacyIOUtils.getDocFile(itemUri);
        }

//        String start = rootUri.getLastPathSegment();
        String childPathSegment = itemPath.get(itemPath.size() -1);
        if(childPathSegment.startsWith(itemPath.get(1))) {
            childPathSegment = childPathSegment.substring(itemPath.get(1).length());
        }
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
                            stripChars++; // remove the delimiter too
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

    public static DocumentFile getTmpFile(@NonNull DocumentFile outputFolder, @NonNull String baseFilename, @NonNull String fileExt, @NonNull String mimeType) {
        return getTmpFile(outputFolder, baseFilename, fileExt, mimeType, false);
    }

    public static DocumentFile getSharedFilesFolder(@NonNull Context context) {
        DocumentFile folder = DocumentFile.fromFile(context.getExternalCacheDir()).findFile("piwigo-shared");
        if(folder == null) {
            folder = DocumentFile.fromFile(context.getExternalCacheDir()).createDirectory("piwigo-shared");
        }
        return folder;
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
    public static Uri copyDocumentUriDataToUri(@NonNull Context context, @NonNull Uri copyFrom, @NonNull Uri copyTo) throws IOException {

        try(FileInputStream is = new FileInputStream(context.getContentResolver().openFileDescriptor(copyFrom, "r").getFileDescriptor());
            FileOutputStream os = new FileOutputStream(context.getContentResolver().openFileDescriptor(copyTo, "rwt").getFileDescriptor())) {

            try(FileChannel inChannel = is.getChannel();
                FileChannel outChannel = os.getChannel()) {
                outChannel.transferFrom(inChannel, 0, inChannel.size());
                return copyTo;
            }
        } catch(NullPointerException e) {
            throw new IllegalStateException("Unable to open input or output stream from uri " + copyFrom + " to uri " + copyTo);
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
                File f = LegacyIOUtils.getFile(uri);
                return f.isDirectory();
            } catch (IOException e) {
               throw new IllegalArgumentException("Uri provided was not for a local file : " + uri);
            }
        }


    }

    public static @Nullable Uri getTreeUri(@Nullable Uri uri) {
        if(uri == null || "file".equals(uri.getScheme()) || uri.toString().startsWith("/")) {
            // already a tree URI
            return uri;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return DocumentsContract.buildTreeDocumentUri(uri.getAuthority(), DocumentsContract.getDocumentId(uri));
        } else {
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
            return uri;
        }
    }


    public static int getUriPermissionsFlagsHeld(Context context, Uri uri) {
        // we have read write for any file uri
        if("file".equals(uri.getScheme())) {
            return URI_PERMISSION_READ_WRITE;
        }
        // for all other uris we must check

        Uri rootUri = getRootUriForUri(context, uri);
        rootUri = getTreeUri(rootUri);
        List<UriPermission> uriPerms = context.getContentResolver().getPersistedUriPermissions();
        int permissionFlags = 0;
        for(UriPermission p : uriPerms) {
            if(p.getUri().equals(uri) || (rootUri != null && p.getUri().equals(rootUri))) {
                permissionFlags |= IOUtils.getFlagsFromUriPermission(p);
            }
        }
        return permissionFlags;
    }

    /**
     *
     * @param context
     * @param uri uri to check against our accessible roots
     * @return @code{null} if there is no accessible root for this uri less specific than itself
     */
    private static @Nullable Uri getRootUriForUri(@NonNull Context context, @Nullable Uri uri) {
        Uri rootUri = null;
        if(uri != null) {
            DocumentFile accessibleRootFile = IOUtils.getDocumentFileForUriLinkedToAnAccessibleRoot(context, uri);
            if(accessibleRootFile != null) {
                // the file is tied to a root we have access to.
                DocumentFile accessibleRoot = IOUtils.getRootDocFile(accessibleRootFile);
                if (accessibleRoot != null && !accessibleRoot.getUri().equals(uri)) {
                    rootUri = Objects.requireNonNull(accessibleRoot).getUri();
                    if (rootUri.equals(uri)) {
                        rootUri = null;
                    }
                }
            }
        }
        return rootUri;
    }

    private static int getFlagsFromUriPermission(@Nullable UriPermission uriPermission) {
        int permissionFlags = 0;
        if(uriPermission != null) {
            if (uriPermission.isReadPermission()) {
                permissionFlags |= Intent.FLAG_GRANT_READ_URI_PERMISSION;
            }
            if (uriPermission.isWritePermission()) {
                permissionFlags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            }
        }
        return permissionFlags;
    }

    public static boolean appHoldsAllUriPermissionsForUri(@NonNull Context context, @Nullable Uri uri, int permissions) {
        if (uri == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return true;
        }
        int permissionsHeld = getUriPermissionsFlagsHeld(context, uri);
        return (permissions & permissionsHeld) == permissions;
    }

    /**
     * We have to try each root so as to get the shortest path to the linked folder.
     * @param context
     * @param initialFolder
     * @return
     */
    public static @Nullable DocumentFile getDocumentFileForUriLinkedToAnAccessibleRoot(Context context, Uri initialFolder) {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            // All files are accessible.
            return LegacyIOUtils.getDocFile(initialFolder);
        }
        List<UriPermission> persistedPermissions = context.getContentResolver().getPersistedUriPermissions();
        DocumentFile match = null;
        int matchTreeDepth = Integer.MAX_VALUE;
        for(UriPermission perm : persistedPermissions) {
            try {
                DocumentFile file = DocumentFile.fromTreeUri(context, perm.getUri());
//                DocumentFile file = DocumentFile.fromSingleUri(context, perm.getUri());
                if(file != null && file.isDirectory()) {
                    DocumentFile item = IOUtils.getTreeLinkedDocFile(context, perm.getUri(), initialFolder);
                    int thisTreeDepth = getTreeDepth(item);
                    if (thisTreeDepth < matchTreeDepth || item.getUri().equals(initialFolder)) {
                        match = item;
                        matchTreeDepth = getTreeDepth(match);
                    }
                }
            } catch(IllegalStateException | IllegalArgumentException e) {
                Logging.log(Log.WARN,TAG,"sinking exception : %1$s", e.getMessage());
                // Illegal argument is when the item is a file not folder
                // Illegal state is thrown in getTreeLinkedDocFile
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
    public static <T extends Collection<Uri>> ArrayList<Uri> removeUrisWeLackPermissionFor(@NonNull Context context, @NonNull T uris) {
        // remove the persisted uri permission for each
        Set<Uri> heldPerms = new HashSet<>();
        for(UriPermission actualHeldPerm : context.getContentResolver().getPersistedUriPermissions()) {
            heldPerms.add(actualHeldPerm.getUri());
        }
        ArrayList<Uri> editable = new ArrayList<>(uris);
        if(!heldPerms.isEmpty()) {
            if (editable.retainAll(heldPerms)) {
                Logging.log(Log.INFO, TAG, "Some permissions to remove are no longer held (removing silently)");
            }
        }
        return editable;
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

    public static void copyFile(Context context, File tmpFile, Uri toUri) {

        try(ParcelFileDescriptor pfdOut = context.getContentResolver().openFileDescriptor(toUri,"rwt")) {
            FileDescriptor fdOut = pfdOut.getFileDescriptor();
            try(FileChannel fcOut = new FileOutputStream(fdOut).getChannel(); FileChannel fcIn = new FileInputStream(tmpFile).getChannel()) {
                fcOut.transferFrom(fcIn, 0, fcIn.size());
            }
        } catch (FileNotFoundException e) {
            Logging.recordException(e);
        } catch (IOException e) {
            Logging.recordException(e);
        }
    }

    public static long getFilesize(Context context, Uri uri) {

        if("file".equals(uri.getScheme())) {
            return new File(uri.getPath()).length();
        }

        DocumentFile docFile = DocumentFile.fromSingleUri(context, uri);
        if(docFile != null) {
            if (docFile.exists()) {
                return docFile.length();
            }
        }

        // Maybe was shared and isn't available as a document file. Open and use a file descriptor.
        ParcelFileDescriptor pfd = null;
        try {
            pfd = context.getContentResolver().openFileDescriptor(uri, "r");
            if(pfd != null) {
                return pfd.getStatSize();
            }
            return -1;
        } catch (FileNotFoundException e) {
            return -1;
        } catch(SecurityException e) {
            Logging.log(Log.WARN, TAG, "No longer able to access file. %1$s", e.getMessage());
//            Logging.recordException(e); (just noise on dashboard)
            return -1;
        } finally {
            if(pfd != null) {
                try {
                    pfd.close();
                } catch (IOException e) {
                    Logging.recordException(e);
                }
            }
        }
    }

    public static long getLastModifiedTime(@NonNull Context context, @NonNull Uri uri) {
        if("file".equals(uri.getScheme())) {
            File f = new File(Objects.requireNonNull(uri.getPath()));
            return f.lastModified();
        }
        try (Cursor c = context.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null) {
                int idx = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED);
                if(idx < 0 && Build.VERSION.SDK_INT >= KITKAT) {
                    idx = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED);
                }
                if(idx >= 0) {
                    c.moveToFirst();
                    return c.getLong(idx);
                }
            }
        } catch(CursorIndexOutOfBoundsException e) {
            Logging.log(Log.ERROR, TAG, "Unable to retrieve column from cursor");
            Logging.recordException(e);
        }

        return -1;
    }


    public static double bytesToMb(long bytes) {
        return BigDecimal.valueOf(bytes).divide(BigDecimal.valueOf(1024 * 1024), 2, BigDecimal.ROUND_HALF_EVEN).doubleValue();
    }

    public static boolean delete(Context context, Uri uri) {
        String path = uri.getPath();
        if(path != null) {
            File f = new File(path);
            if (f.exists()) {
                return f.delete();
            }
        }
        if("file".equals(uri.getScheme())) {
            return false;
        }
        return 0 < context.getContentResolver().delete(uri, null, null);
    }

    public static Uri addFileToMediaStore(Context context, Uri fileUri) {
        String mimeType = IOUtils.getMimeType(context, fileUri);
        return addFileToMediaStore(context, fileUri, mimeType);
    }

    public static Uri addFileToMediaStore(Context context, Uri fileUri, String mimeType) {
        Uri mediaStoreUri = getMediaStoreUri(context, fileUri);
        if(mediaStoreUri != null) {
            return mediaStoreUri;
        }

        boolean isFileUri = "file".equals(fileUri.getScheme());

        if(mimeType == null && isFileUri) {
            // guess from the file content.
            try(FileInputStream fis = new FileInputStream(fileUri.getPath())) {
                mimeType = URLConnection.guessContentTypeFromStream(fis);
                Logging.log(Log.INFO, TAG, "Guessed mime type for unknown file : " + mimeType);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            //FIXME all are being pushed to downloads here
            mediaStoreUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;//getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        }
        if(mediaStoreUri != null) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.TITLE, IOUtils.getFilename(context, fileUri));
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            values.put(MediaStore.MediaColumns.DATA, fileUri.toString());//TODO toPath does not work for a content Uri
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            }
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures");
//            }
//            if(MimeTypeFilter.matches(mimeType,"video/*")) {
//                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES);
//            } else if(MimeTypeFilter.matches(mimeType,"image/*")) {
//                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
//            } else {
//                //TODO maybe work out how to always use this when downloaded file....
//                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
//            }
//            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            try {
                Uri uri = context.getContentResolver().insert(mediaStoreUri, values);
                if(uri != null) {
                    // let everyone know that the file has been added.
                    context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri));
                } else {
                    uri = IOUtils.getMediaStoreUriForFile(mediaStoreUri, context, fileUri);
                }
                if(uri == null) {
                    Logging.log(Log.ERROR, TAG, "MediaStore Uri is null for uri : " + fileUri);
                }
                return uri;
            } catch(IllegalArgumentException e) {
                Logging.recordException(e); // shouldn't ever occur, but, just in case.
            }
        }
        Logging.log(Log.ERROR, TAG, "Unable to get a MediaStore Uri. Returning original Uri %1$s", fileUri);
        return fileUri;
    }

    /**
     *
     * @param context
     * @param fileUri uri to check
     * @return null if not in media store.
     */
    private static @Nullable Uri getMediaStoreUri(@NonNull Context context, Uri fileUri) {
        Uri mediaUri = null;
        boolean inMediaStore = true;
        if(fileUri == null) {
            return null;
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                mediaUri = MediaStore.getMediaUri(context, fileUri);
            } catch (SecurityException|IllegalArgumentException e) {
                inMediaStore = false;
            }
        }
        return mediaUri;
    }

    private static @Nullable Uri selectExternalMediaStoreContentProviderUriForFile(@Nullable String mimeType, @Nullable Uri fileUri) {

        boolean isVideo = MimeTypeFilter.matches(mimeType, "video/*");
        boolean isAudio = MimeTypeFilter.matches(mimeType, "audio/*");
        boolean isImage = MimeTypeFilter.matches(mimeType, "image/*");
        Uri mediaStoreUri = null;
        if(isVideo) {
            if(fileUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mediaStoreUri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            } else {
                mediaStoreUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            }
        } else if(isAudio) {
            if(fileUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mediaStoreUri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            } else {
                mediaStoreUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            }
        } else if(isImage) {
            if(fileUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mediaStoreUri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            } else {
                mediaStoreUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            }
        }
        return mediaStoreUri;
    }

    /**
     *
     * @param mediaStoreUri mediastore to query (e.g. MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
     * @param context
     * @param fileUri file uri to locate in the media store
     * @return null if not present in the media store. (in which case it can be added).
     */
    private static @Nullable Uri getMediaStoreUriForFile(@NonNull Uri mediaStoreUri, @NonNull Context context, @NonNull Uri fileUri) {
        String[] what = new String[]{ MediaStore.MediaColumns._ID};
        String where = MediaStore.MediaColumns.DATA + "='"+fileUri.getPath()+ "'";
        try(Cursor c = context.getContentResolver().query(mediaStoreUri, what ,where, null, null)) {
            if(c != null) {
                c.moveToFirst();
                int id = c.getInt(c.getColumnIndex(MediaStore.Images.ImageColumns._ID));
                return Uri.withAppendedPath(mediaStoreUri, String.valueOf(id));
            }
        }
        return null;
    }

    public static void deleteAllFilesSharedWithThisApp(@NonNull Context context) {
        DocumentFile sharedFilesFolder = IOUtils.getSharedFilesFolder(context);
        DocumentFile[] sharedFiles = sharedFilesFolder.listFiles();
        boolean success = true;
        for (DocumentFile sharedFile : sharedFiles) {
            success &= sharedFile.delete();
        }
        if(!success) {
            Logging.log(Log.WARN, TAG, "Unable to delete all files shared with this app");
        }
    }

    public static DocumentFile getSingleDocFile(@NonNull Context context, @Nullable Uri uri) {
        if(uri == null) {
            return null;
        }
        if("file".equals(uri.getScheme())) {
            return DocumentFile.fromFile(new File(uri.getPath()));
        }
        return DocumentFile.fromSingleUri(context, uri);
    }

    public static Set<String> getMimeTypesFromFileExts(Set<String> mimeTypes, Set<String> fileExts) {
        MimeTypeMap map = MimeTypeMap.getSingleton();
        if(fileExts == null) {
            return mimeTypes;
        }
        for(String fileExt : fileExts) {
            String mimeType = knownExtsToMimes.get(fileExt.toLowerCase());
            if(mimeType == null) {
                mimeType = map.getMimeTypeFromExtension(fileExt.toLowerCase());
            }
            if(mimeType == null) {
                if(fileExt.equals("webmv")) {
                    mimeType = "video/webm";
                }
            }
            if(mimeType != null) {
                mimeTypes.add(mimeType);
            } else {
                Logging.log(Log.WARN, TAG, "Unrecognised file extension - no mime type found : " + fileExt);
            }
        }
        return mimeTypes;
    }

    /**
     * @param input a combined set of flags
     * @param filterFlags flags to filter the input to (remove everything else)
     * @return a set of combined filterFlags less any that wern't found in the input
     */
    public static int filterToCombinedFlags(int input, int ... filterFlags) {
        int combinedFilter = combineIntFlags(filterFlags);
        return input & combinedFilter;
    }

    /**
     * @param permFlags a set of individual flags that are ORd together
     * @return a combined (ORd set of the flags)
     */
    public static int combineIntFlags(int ... permFlags) {
        int flags = 0;
        if(permFlags != null && permFlags.length > 0) {
            for(int flag : permFlags) {
                flags |= flag;
            }
        }
        return flags;
    }

    /**
     * @param input a mix of flags to test
     * @param permFlags the flags wanted
     * @return true if all the flags wanted are contained in the input
     */
    public static boolean allUriFlagsAreSet(int input, int ... permFlags) {
        int expectedFlags = combineIntFlags(permFlags);
        int filteredPerms = input & expectedFlags; // filter everything not expected out
        return expectedFlags == filteredPerms; // is what is left matching what was expected or are bits of expected missing?
    }

    public static boolean needsWritePermission(int selectedUriPermissionFlags) {
        return allUriFlagsAreSet(selectedUriPermissionFlags, IOUtils.URI_PERMISSION_WRITE);
    }

    public static boolean isPrivateFolder(@NonNull Context context, @Nullable String path) {
        if(path == null) {
            return false;
        }
        File file = context.getExternalFilesDir(null);
        if(file != null && path.startsWith(file.getPath())) {
            return true;
        }
        return path.startsWith(context.getFilesDir().getPath());
    }

    public static String getManifestFilePermissionsNeeded(int selectedUriPermissionFlags) {
        String requiredPermission = null;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            requiredPermission = Manifest.permission.READ_EXTERNAL_STORAGE;
            if (IOUtils.needsWritePermission(selectedUriPermissionFlags)) {
                requiredPermission = Manifest.permission.WRITE_EXTERNAL_STORAGE;
            }
        }
        return requiredPermission;
    }

    public static String getI18LocalisedFilePermissionName(Context context, int selectedUriPermissionFlags) {
        if(IOUtils.needsWritePermission(selectedUriPermissionFlags)) {
            return context.getString(R.string.permission_write);
        }
        return context.getString(R.string.permission_read);

    }

    public static @Nullable String getManifestFilePermissionsNeeded(@NonNull Context context, @Nullable Uri folderUri, int uriPermissionReadWrite) {
        if(!IOUtils.appHoldsAllUriPermissionsForUri(context, folderUri, IOUtils.URI_PERMISSION_READ_WRITE)) {
            return IOUtils.getManifestFilePermissionsNeeded(IOUtils.URI_PERMISSION_READ_WRITE);
        }
        return null;
    }

    public static String[] getMimeTypesIncludingFolders(Set<String> mimeTypes) {
        boolean addFolderMime = true;
        int mimeTypeCount = 1;
        String[] mimeTypesArray;
        if(mimeTypes != null) {
            if(mimeTypes.contains(DocumentsContract.Document.MIME_TYPE_DIR)) {
                addFolderMime = false;
                mimeTypeCount = mimeTypes.size();
            } else {
                mimeTypeCount += mimeTypes.size();
            }
            mimeTypesArray = new String[mimeTypeCount];
            mimeTypes.toArray(mimeTypesArray);
        } else {
            mimeTypesArray = new String[1];
        }
        if(addFolderMime) {
            mimeTypesArray[mimeTypesArray.length - 1] = DocumentsContract.Document.MIME_TYPE_DIR;
        }
        return mimeTypesArray;
    }


    public static boolean isPlayableMedia(@Nullable String mimeType) {
        boolean isVideo = MimeTypeFilter.matches(mimeType, "video/*");
        boolean isAudio = MimeTypeFilter.matches(mimeType, "audio/*");
        return isAudio || isVideo;
    }

    public static boolean isPlayableMedia(@NonNull Context context,  @NonNull Uri file) {
        String mimeType = IOUtils.getMimeType(context, file);
        return null != MimeTypeFilter.matches(mimeType, new String[]{"video/*", "audio/*"});
    }

    public static int fillBufferFromStream(InputStream inputStream, byte[] buffer) throws IOException {
        int bytesRead;
        int totalBytesRead = 0;
        do {
            bytesRead = inputStream.read(buffer, totalBytesRead, buffer.length - totalBytesRead);
            if (bytesRead > 0) {
                totalBytesRead += bytesRead;
            }
        } while(bytesRead > 0 && totalBytesRead < buffer.length);
        if(totalBytesRead == 0 && bytesRead == -1) {
            totalBytesRead = -1;
        }
        return totalBytesRead;
    }
}
