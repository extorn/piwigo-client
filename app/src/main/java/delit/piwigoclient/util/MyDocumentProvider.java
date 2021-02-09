package delit.piwigoclient.util;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.MimeTypeFilter;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.util.IOUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.ui.util.download.FileThumbnailGenerator;


public class MyDocumentProvider extends DocumentsProvider {

    private static final String[] DEFAULT_ROOT_PROJECTION =
            new String[]{Root.COLUMN_ROOT_ID, Root.COLUMN_MIME_TYPES,
                    Root.COLUMN_FLAGS, Root.COLUMN_ICON, Root.COLUMN_TITLE,
                    Root.COLUMN_SUMMARY, Root.COLUMN_DOCUMENT_ID,
                    Root.COLUMN_AVAILABLE_BYTES,};
    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new
            String[]{Document.COLUMN_DOCUMENT_ID, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME, Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS, Document.COLUMN_SIZE,};

    private static final String ROOT_ID = "0";
    private static final String ROOT_DOC_ID = "1";
    private static final String TAG = "MyDocsProvider";
    private static final int MAX_LAST_MODIFIED = 3;
    private static final String THUMBNAILS_DOC_ID = "Thumbnails";

    public static String getAuthority() {
        return BuildConfig.DOCUMENTS_PROVIDER_AUTHORITY;
    }

    public static boolean ownsUri(Context context, Uri uri) {
        if(getAuthority().equals(uri.getAuthority())) {
            return true;
        }
        String baseDir = getBaseDir(context).getPath();
        return uri.getPath().startsWith(baseDir);
    }

    public static Uri getRootDocUri() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return DocumentsContract.buildDocumentUriUsingTree(DocumentsContract.buildTreeDocumentUri(getAuthority(), ROOT_DOC_ID), ROOT_DOC_ID);
        } else {
            return DocumentsContract.buildDocumentUri(getAuthority(), ROOT_DOC_ID);
        }
    }
    public static Uri getRootsUri() {
        return DocumentsContract.buildRootUri(getAuthority(), ROOT_ID);
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        MatrixCursor result = new MatrixCursor(resolveRootProjection(projection));
        // It's possible to have multiple roots (e.g. for multiple accounts in the
        // same app) -- just add multiple cursor rows.
        final MatrixCursor.RowBuilder row = result.newRow();
        row.add(Root.COLUMN_ROOT_ID, ROOT_ID);

        // You can provide an optional summary, which helps distinguish roots
        // with the same title. You can also use this field for displaying an
        // user account name.
        row.add(Root.COLUMN_SUMMARY, Objects.requireNonNull(getContext()).getString(R.string.doc_provider_root_downloads_summary));

        // FLAG_SUPPORTS_CREATE means at least one directory under the root supports
        // creating documents. FLAG_SUPPORTS_RECENTS means your application's most
        // recently used documents will show up in the "Recents" category.
        // FLAG_SUPPORTS_SEARCH allows users to search all documents the application
        // shares.
        row.add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY|Root.FLAG_SUPPORTS_RECENTS|Root.FLAG_SUPPORTS_SEARCH/*Root.FLAG_SUPPORTS_CREATE*/);

        // COLUMN_TITLE is the root title (e.g. Gallery, Drive).
        row.add(Root.COLUMN_TITLE, getContext().getString(R.string.doc_provider_root_downloads_title));

        // This document id cannot change after it's shared.
        row.add(Root.COLUMN_DOCUMENT_ID, getDocIdForFile(getBaseDir(getContext())));

        // The child MIME types are used to filter the roots and only present to the
        // user those roots that contain the desired type somewhere in their file hierarchy.
//        row.add(Root.COLUMN_MIME_TYPES, getChildMimeTypes(baseDir));
//        row.add(Root.COLUMN_AVAILABLE_BYTES, baseDir.getFreeSpace());
        row.add(Root.COLUMN_ICON, R.drawable.ic_launcher_foreground);

        return result;
    }

    private String getDocIdForFile(File file) {
        if(file.equals(getBaseDir(getContext()))) {
            return ROOT_DOC_ID;
        } else {
            return file.getName();
        }
    }

    private static File getBaseDir(@NonNull Context context) {
        File f = Objects.requireNonNull(context).getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if(!f.exists()) {
            if(!f.mkdir()) {
                Logging.log(Log.ERROR, TAG, "Unable to create root folder");
            }
        }
        return f;
    }

    public static void notifySystemOfUpdatedRootUri(@NonNull Context context) {
        Uri rootsUri =
        DocumentsContract.buildRootsUri(MyDocumentProvider.getAuthority());
        context.getContentResolver().notifyChange(rootsUri, null);
    }

    public static void notifySystemOfUpdatedDocumentUri(@NonNull Context context, @NonNull String parentDocumentId) {
        Uri updatedUri =
                DocumentsContract.buildChildDocumentsUri(MyDocumentProvider.getAuthority(), parentDocumentId);
        context.getContentResolver().notifyChange(updatedUri, null);
    }

    private String[] resolveRootProjection(String[] projection) {
        return DEFAULT_ROOT_PROJECTION;
    }

    @Override
    public DocumentsContract.Path findDocumentPath(@Nullable String parentDocumentId, String childDocumentId) throws FileNotFoundException {
        if(parentDocumentId == null || ROOT_DOC_ID.equals(parentDocumentId)) {
            File f = getFileForDocId(childDocumentId);
            if(f.exists()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    return new DocumentsContract.Path(ROOT_ID, Collections.singletonList(childDocumentId));
                }
            }
        }
        return null;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        final MatrixCursor result = new
                MatrixCursor(resolveDocumentProjection(projection));
        final File file = getFileForDocId(documentId);
        includeFile(result, documentId, file);
        return result;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder) throws FileNotFoundException {
        final MatrixCursor result = new
                MatrixCursor(resolveDocumentProjection(projection));
        final File parent = getFileForDocId(parentDocumentId);
        if(parent.exists() && parent.isDirectory()) {
            File[] files = parent.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!isThumbnailsFolder(file)) {
                        // Adds the file's display name, MIME type, size, and so on.
                        includeFile(result, getDocIdForFile(file), file);
                    }
                }
            }
        }
        return result;
    }

    private boolean isThumbnailsFolder(File file) {
        return file.isDirectory() && THUMBNAILS_DOC_ID.equals(file.getName());
    }

    private void includeFile(MatrixCursor result, String documentId, File file) {
        String mimeType;
        if(file.isDirectory()) {
            mimeType = DocumentsContract.Document.MIME_TYPE_DIR;
        } else {
            mimeType = IOUtils.getMimeType(Objects.requireNonNull(getContext()), Uri.fromFile(file));
        }

        MatrixCursor.RowBuilder row = result.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, documentId);
        row.add(Document.COLUMN_MIME_TYPE, mimeType);
        row.add(Document.COLUMN_DISPLAY_NAME, file.getName());
        row.add(Document.COLUMN_LAST_MODIFIED, file.lastModified());
        row.add(Document.COLUMN_SIZE, file.length());
        if(!getBaseDir(getContext()).equals(file)) {
            row.add(Document.COLUMN_FLAGS, Document.FLAG_SUPPORTS_DELETE | Document.FLAG_SUPPORTS_WRITE|Document.FLAG_SUPPORTS_THUMBNAIL);
        }
    }

    @Override
    public AssetFileDescriptor openDocumentThumbnail(String documentId, Point sizeHint, CancellationSignal signal) throws FileNotFoundException {
        File thumbnailFile;
        try {
            thumbnailFile = getThumbnailForFile(documentId, sizeHint);
        } catch(FileNotFoundException e) {
            Bitmap thumbnail = createThumbnail(getFileForDocId(documentId), sizeHint, signal);
            thumbnailFile = writeThumbnailToFile(documentId, thumbnail, sizeHint);
        }

        ParcelFileDescriptor pfd = ParcelFileDescriptor.open(thumbnailFile, ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(pfd, 0, pfd.getStatSize());
    }

    private File writeThumbnailToFile(String documentId, Bitmap thumbnail, Point sizeHint) {
        File thumbnails = getThumbnailsFolder(sizeHint);
        File thumbnailFile = new File(thumbnails, documentId);
        if(thumbnailFile.exists()) {
            Logging.log(Log.DEBUG, TAG, "Overwriting thumbnail for doc %1$s", documentId);
        }
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(thumbnailFile))) {
            thumbnail.compress(Bitmap.CompressFormat.JPEG, 90, out); // bmp is your Bitmap instance
            // PNG is a lossless format, the compression factor (100) is ignored
        } catch (IOException e) {
            e.printStackTrace();
        }
        return thumbnailFile;
    }

    private File getThumbnailsFolder() {
        return new File(getBaseDir(getContext()), THUMBNAILS_DOC_ID);
    }

    private File getThumbnailsFolder(Point sizeHint) {
        File f = new File(getThumbnailsFolder(), ""+sizeHint.x+'x'+sizeHint.y);
        if(!f.exists()) {
            if(!f.mkdirs()) {
                Logging.log(Log.ERROR, TAG, "Unable to create thumbnail folder");
            }
        }
        return f;
    }

    private File getThumbnailForFile(String documentId, Point sizeHint) throws FileNotFoundException {
        Logging.log(Log.DEBUG, TAG, "Requesting thumbnail of size %1$s for file %2$s", sizeHint, documentId);
        File thumbnails = getThumbnailsFolder(sizeHint);
        return getFilesWithName(thumbnails, documentId);
    }

    private Bitmap createThumbnail(File doc, Point sizeHint, CancellationSignal signal) throws FileNotFoundException {
        Logging.log(Log.DEBUG, TAG, "Creating thumbnail of size %1$s for file %2$s", sizeHint, doc);
        String mime = IOUtils.getMimeType(Objects.requireNonNull(getContext()), Uri.fromFile(doc));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                if( MimeTypeFilter.matches(mime,"video/*")) {
                    return ThumbnailUtils.createVideoThumbnail(doc, new Size(sizeHint.x, sizeHint.y), signal);
                } else if( MimeTypeFilter.matches(mime,"image/*")) {
                    return ThumbnailUtils.createImageThumbnail(doc, new Size(sizeHint.x, sizeHint.y), signal);
                } else if( MimeTypeFilter.matches(mime,"audio/*")) {
                    return ThumbnailUtils.createAudioThumbnail(doc, new Size(sizeHint.x, sizeHint.y), signal);
                }
            } catch (IOException e) {
                throw new FileNotFoundException(e.getMessage());
            }
        } else {
            return new BlockingThumbnailGenerator(getContext(), Uri.fromFile(doc), sizeHint, signal).getBitmap();
        }
        return null;
    }

    private static class BlockingThumbnailGenerator extends FileThumbnailGenerator<BlockingThumbnailGenerator> {

        private final CancellationSignal signal;
        private Bitmap loadedBitmap;

        public BlockingThumbnailGenerator(@NonNull Context context, @NonNull Uri downloadedFile, Point thumbSize, CancellationSignal signal) {
            super(context, (generator, success) -> generator.awaken(), downloadedFile, thumbSize);
            this.signal = signal;
        }

        private synchronized void awaken() {
            notifyAll();
        }

        public Bitmap getBitmap() {
            execute();
            return getLoadedBitmap();
        }

        @Override
        public void execute() {
            super.execute();
            synchronized (this) {
                while(!signal.isCanceled() && loadedBitmap == null) {
                    try {
                        wait(500);
                    } catch (InterruptedException e) {
                        Logging.log(Log.DEBUG, TAG, "Interrupted waiting for thumbnail");
                    }
                }
            }
        }

        public Bitmap getLoadedBitmap() {
            return loadedBitmap;
        }

        @Override
        protected void withLoadedThumbnail(Bitmap bitmap) {
            loadedBitmap = bitmap;
        }

        @Override
        protected void withErrorThumbnail(Bitmap bitmap) {
            loadedBitmap = bitmap;
        }
    }

    private String[] resolveDocumentProjection(String[] projection) {
        return DEFAULT_DOCUMENT_PROJECTION;
    }

    private File getFileForDocId(String docId) throws FileNotFoundException {
        if(ROOT_DOC_ID.equals(docId)) {
            return getBaseDir(getContext());
        } else {
            File folder = getBaseDir(getContext());
            return getFilesWithName(folder, docId);
        }
    }

    public File getFilesWithName(File folder, String docId) throws FileNotFoundException {
        File[] f = folder.listFiles((dir, name) -> name.equals(docId));
        if(f == null) {
            Logging.log(Log.ERROR, TAG, "Unable to find file by id %1$s. Parent does not exist", docId);
            throw new FileNotFoundException("Parent does not exist");
        }
        if (f.length != 1) {
            Logging.log(Log.ERROR, TAG, "Unable to find file by id %1$s. Files returned: %1$d", f.length);
            throw new FileNotFoundException("Unable to find file with name " + docId);
        }
        return f[0];
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, @Nullable CancellationSignal signal) throws FileNotFoundException {
        final File file = getFileForDocId(documentId);
        final int accessMode = ParcelFileDescriptor.parseMode(mode);
        return ParcelFileDescriptor.open(file, accessMode);

        // the following is useful if the file isn't on this device.

        /*final boolean isWrite = (mode.indexOf('w') != -1);
        if(isWrite) {
            // Attach a close listener if the document is opened in write mode.
            try {
                Handler handler = new Handler(getContext().getMainLooper());
                return ParcelFileDescriptor.open(file, accessMode, handler,
                        new ParcelFileDescriptor.OnCloseListener() {
                            @Override
                            public void onClose(IOException e) {
                                // Update the file with the cloud server. The client is done
                                // writing.
                                Logging.log(Log.INFO, TAG, "A file with id " +
                                        documentId + " has been closed! Time to " +
                                        "update the server.");
                            }

                        });
            } catch (IOException e) {
                throw new FileNotFoundException("Failed to open document with id"
                        + documentId + " and mode " + mode);
            }
        } else {
            return ParcelFileDescriptor.open(file, accessMode);
        }*/
    }

    @Override
    public Cursor queryRecentDocuments(String rootId, String[] projection)
            throws FileNotFoundException {

        // This example implementation walks a
        // local file structure to find the most recently
        // modified files.  Other implementations might
        // include making a network call to query a
        // server.

        // Create a cursor with the requested projection, or the default projection.
        final MatrixCursor result =
                new MatrixCursor(resolveDocumentProjection(projection));

        final File parent = getFileForDocId(rootId);

        // Create a queue to store the most recent documents,
        // which orders by last modified.
        PriorityQueue<File> lastModifiedFiles =
                new PriorityQueue<>(5, (i, j) -> Long.compare(i.lastModified(), j.lastModified()));

        // Iterate through all files and directories
        // in the file structure under the root.  If
        // the file is more recent than the least
        // recently modified, add it to the queue,
        // limiting the number of results.
        final LinkedList<File> pending = new LinkedList<>();

        // Start by adding the parent to the list of files to be processed
        if(parent.exists() && parent.isDirectory()) {
            pending.add(parent);

            // Do while we still have unexamined files
            while (!pending.isEmpty()) {
                // Take a file from the list of unprocessed files
                final File file = pending.removeFirst();
                if (file.isDirectory()) {
                    // If it's a directory, add all its children to the unprocessed list
                    Collections.addAll(pending, Objects.requireNonNull(file.listFiles()));
                } else {
                    // If it's a file, add it to the ordered queue.
                    lastModifiedFiles.add(file);
                }
            }
        }

        // Add the most recent files to the cursor,
        // not exceeding the max number of results.
        for (int i = 0; i < Math.min(MAX_LAST_MODIFIED + 1, lastModifiedFiles.size()); i++) {
            final File file = lastModifiedFiles.remove();
            includeFile(result, getDocIdForFile(file), file);
        }
        return result;
    }

    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            revokeDocumentPermission(documentId);
        }
        File file = getFileForDocId(documentId);
        File parent = file.getParentFile();
        if(!file.delete()) {
            Logging.log(Log.ERROR,TAG, "Unable to delete document from provider");
        } else {
            deleteThumbnails(documentId);
        }
        notifySystemOfUpdatedDocumentUri(Objects.requireNonNull(getContext()), getDocIdForFile(Objects.requireNonNull(parent)));
    }

    private void deleteThumbnails(String documentId) {
        Set<File> thumbnails = getThumbnailsForFile(documentId);
        for(File f : thumbnails) {
            if(!f.delete()) {
                Logging.log(Log.ERROR,TAG, "Unable to delete document thumbnail from provider");
            }
        }
    }

    private Set<File> getThumbnailsForFile(String documentId) {
        File thumbnails = getThumbnailsFolder();
        if(!thumbnails.exists()) {
            return Collections.emptySet();
        }
        Set<File> thumbs = new HashSet<>();
        File[] thumbnailFiles = thumbnails.listFiles();
        if(thumbnailFiles != null) {
            for (File thumbFolder : thumbnailFiles) {
                try {
                    thumbs.add(getFilesWithName(thumbFolder, documentId));
                } catch (FileNotFoundException e) {
                    //ignore this. it is expected
                }
            }
        }
        return thumbs;
    }

    @Override
    public boolean onCreate() {
        return true;
    }
}
