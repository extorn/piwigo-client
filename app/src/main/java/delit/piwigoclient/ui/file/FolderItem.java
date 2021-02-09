package delit.piwigoclient.ui.file;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import delit.libs.core.util.Logging;
import delit.libs.util.IOUtils;
import delit.libs.util.LegacyIOUtils;
import delit.libs.util.progress.ProgressListener;
import delit.libs.util.progress.TaskProgressTracker;

public class FolderItem implements Parcelable {
    private static final AtomicLong uidGen = new AtomicLong();
    private static final String TAG = "FolderItem";
    private final long uid;
    private Uri rootUri;
    private final Uri itemUri;
    private DocumentFile itemDocFile;
    private Boolean isFolder;
    private Boolean isFile; // items are not files or folders if they are special system files
    private long lastModified = -1;
    private String name;
    private String ext;
    private String mime;
    private long fileLength;
    private int fieldsLoadedFrom = NONE;
    private final static int NONE = 0;
    private final static int DOCFILE = 1;
    private final static int FILE = 2;
    private final static int MEDIASTORE = 3;
    private int uriPermissions;

    public FolderItem(Uri itemUri) {
        this.itemUri = itemUri;
        uid = uidGen.getAndIncrement();
    }

    public long getUid() {
        return uid;
    }

    public FolderItem(Uri rootUri, DocumentFile itemDocFile) {
        this.itemDocFile = itemDocFile;
        this.rootUri = rootUri;
        this.itemUri = itemDocFile.getUri(); // used for persistence
        uid = uidGen.getAndIncrement();
    }

    protected FolderItem(Parcel in) {
        uid = in.readLong();
        rootUri = in.readParcelable(Uri.class.getClassLoader());
        itemUri = in.readParcelable(Uri.class.getClassLoader());
        byte tmpIsFolder = in.readByte();
        isFolder = tmpIsFolder == 0 ? null : tmpIsFolder == 1;
        byte tmpIsFile = in.readByte();
        isFile = tmpIsFile == 0 ? null : tmpIsFile == 1;
        lastModified = in.readLong();
        name = in.readString();
        ext = in.readString();
        mime = in.readString();
        fileLength = in.readLong();
        fieldsLoadedFrom = in.readInt();
        uriPermissions = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(uid);
        dest.writeParcelable(rootUri, flags);
        dest.writeParcelable(itemUri, flags);
        dest.writeByte((byte) (isFolder == null ? 0 : isFolder ? 1 : 2));
        dest.writeByte((byte) (isFile == null ? 0 : isFile ? 1 : 2));
        dest.writeLong(lastModified);
        dest.writeString(name);
        dest.writeString(ext);
        dest.writeString(mime);
        dest.writeLong(fileLength);
        dest.writeInt(fieldsLoadedFrom);
        dest.writeInt(uriPermissions);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<FolderItem> CREATOR = new Creator<FolderItem>() {
        @Override
        public FolderItem createFromParcel(Parcel in) {
            return new FolderItem(in);
        }

        @Override
        public FolderItem[] newArray(int size) {
            return new FolderItem[size];
        }
    };

    public boolean isFolder() {
        if(!isFieldsCached()) {
            throw new IllegalStateException("Fields not available. Please cache from either DocFile or MediaStore");
        }
        return isFolder;
    }

    public boolean isFile() {
        if(!isFieldsCached()) {
            throw new IllegalStateException("Fields not available. Please cache from either DocFile or MediaStore");
        }
        return isFile;
    }

    public long getLastModified() {
        if(!isFieldsCached()) {
            throw new IllegalStateException("Fields not available. Please cache from either DocFile or MediaStore");
        }
        return lastModified;
    }

    public String getMime() {
        if(!isFieldsCached()) {
            throw new IllegalStateException("Fields not available. Please cache from either DocFile or MediaStore");
        }
        return mime;
    }

    public String getExt() {
        if(!isFieldsCached()) {
            throw new IllegalStateException("Fields not available. Please cache from either DocFile or MediaStore");
        }
        return ext;
    }

    public String getName() {
        if(!isFieldsCached()) {
            throw new IllegalStateException("Fields not available. Please cache from either DocFile or MediaStore");
        }
        return name;
    }

    public Uri getContentUri() {
        return itemUri;
    }

    /**
     * Get's the doc file - may be null if item not initialised.
     * @return
     */
    public DocumentFile getDocumentFile() {
        return itemDocFile;
    }

    /**
     *
     * @param context
     * @return may be null (pre lollipop always null! :-( )
     */
    private @Nullable
    DocumentFile getDocumentFile(Context context) {
        if(itemDocFile != null) {
            return itemDocFile;
        }
        if(rootUri != null) {
            // will be the case after loaded from parcel
            itemDocFile = IOUtils.getTreeLinkedDocFile(context, rootUri, itemUri);
        } else {
            itemDocFile = DocumentFile.fromSingleUri(context, itemUri); // this will occur if the file was shared with us by external app
        }
        return itemDocFile;
    }

    private boolean cacheDocFileFields(Context context) {
        if(null != getDocumentFile(context)) {
            isFolder = itemDocFile.isDirectory();
            isFile = itemDocFile.isFile();
            name = itemDocFile.getName();
            ext = IOUtils.getFileExt(name);
            if(ext != null) {
                mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
            } else {
                Logging.log(Log.WARN, TAG, "Unable to set mime type for file with no extension ("+name+")");
            }
            lastModified = itemDocFile.lastModified();
            fileLength = itemDocFile.length();
            fieldsLoadedFrom = DOCFILE;
            return true;
        }
        return false;
    }

    public long getFileLength() {
        if(!isFieldsCached()) {
            throw new IllegalStateException("Fields not available. Please cache from either DocFile or MediaStore");
        }
        return fileLength;
    }

    private boolean withLegacyCachedFields() {
        File f;
        try {
            f = LegacyIOUtils.getFile(itemUri);
        } catch (IOException e) {
            return false;
        }
        if(f == null) {
            return false;
        }
        itemDocFile = DocumentFile.fromFile(f);
        isFolder = f.isDirectory();
        isFile = f.isFile();
        name = f.getName();
        ext = IOUtils.getFileExt(name);
        mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        lastModified = f.lastModified();
        fileLength = f.length();
        fieldsLoadedFrom = FILE;
        return true;
    }

    public boolean isFieldsCached() {
        return fieldsLoadedFrom != NONE;
    }

    private boolean withMediaStoreCachedFields(Context context) {
        String[] projection = new String[]{MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED};
        try (Cursor c = context.getContentResolver().query(itemUri, projection, null,null, null)) {
            if (c != null) {
                c.moveToFirst();
                mime = c.getString(c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE));
                name = c.getString(c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME));
                ext = IOUtils.getFileExt(name, mime);
                fileLength = c.getLong(c.getColumnIndex(MediaStore.MediaColumns.SIZE));
                lastModified = c.getLong(c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED));
                isFile = true;
                isFolder = false;
                fieldsLoadedFrom = MEDIASTORE;
                return true;
            }
        }
        return false;
    }

    /**
     *
     * @param context
     * @return true if the fields were cached somehow.
     */
    public boolean cacheFields(Context context) {
        boolean cached = false;
        if("file".equals(itemUri.getScheme())) {
            cached = withLegacyCachedFields();
        }
        if(!cached) {
            cached = cacheDocFileFields(context);
        }
        if(!cached) {
            cached = withMediaStoreCachedFields(context);
        }
        return cached;
    }

    public int getUriPermissions() {
        return uriPermissions;
    }

    public static <T extends FolderItem> boolean  cacheDocumentInformation(@NonNull Context context, @NonNull List<T> items, ProgressListener taskListener) {
        if(items.size() == 0) {
            return true;
        }
        ThreadPoolExecutor executor = new ThreadPoolExecutor(4, 32, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<>(items.size()));

        TaskProgressTracker cachingTracker = new TaskProgressTracker("overall file field caching", items.size(), taskListener);
        for(FolderItem item : items) {
            executor.execute(() -> {
                try {
                    if(!item.isFieldsCached()) {
                        if (!item.cacheFields(context)) {
                            Logging.log(Log.ERROR, TAG, "Unable to cache fields for URI : " + item.getContentUri());
                        }
                    }
                } finally {
                    cachingTracker.incrementWorkDone(1);
                }
            });
        }
        boolean cancelled = false;

        // Wait for the tasks to finish.

        if(taskListener != null) {
            while (!cancelled && !cachingTracker.isComplete()) {
                try {
                    synchronized (cachingTracker) {
                        cachingTracker.wait();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    cancelled = true;
                }
            }
            int outstandingTasks = (int)cachingTracker.getRemainingWork();
            Logging.log(Log.INFO,TAG, "Finished waiting for executor to end (cancelled : "+cancelled+") while listening to progress. Outstanding Task Count : " + outstandingTasks);
        } else {
            try {
                executor.awaitTermination(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                cancelled = true;
                Logging.log(Log.ERROR, TAG, "Timeout (60sec!) while waiting for folder content fields to be cached");
            }
            int outstandingTasks = (int)cachingTracker.getRemainingWork();
            Logging.log(Log.INFO,TAG, "Finished waiting for executor to end (cancelled : "+cancelled+") . Outstanding Task Count : " + outstandingTasks);
        }
        executor.shutdown();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            cancelled = true;
            Logging.log(Log.ERROR, TAG, "Timeout while waiting for folder content field loading executor to end");
        }
        return !cancelled;
    }

    public void setPermissionsGranted(int permissions) {
        uriPermissions = permissions;
    }
}
