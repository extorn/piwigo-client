package delit.piwigoclient.util;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Objects;
import java.util.PriorityQueue;

import delit.libs.core.util.Logging;
import delit.libs.util.IOUtils;
import delit.libs.util.LegacyIOUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;


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

    public static String getAuthority() {
        return BuildConfig.APPLICATION_ID + ".provider.docs";
    }

    public static boolean ownsUri(Uri uri) {
        return getAuthority().equals(uri.getAuthority());
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
        row.add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY|Root.FLAG_SUPPORTS_RECENTS/*Root.FLAG_SUPPORTS_CREATE |
                Root.FLAG_SUPPORTS_RECENTS |
                Root.FLAG_SUPPORTS_SEARCH*/);

        // COLUMN_TITLE is the root title (e.g. Gallery, Drive).
        row.add(Root.COLUMN_TITLE, getContext().getString(R.string.doc_provider_root_downloads_title));

        // This document id cannot change after it's shared.
        row.add(Root.COLUMN_DOCUMENT_ID, getDocIdForFile(getBaseDir()));

        // The child MIME types are used to filter the roots and only present to the
        // user those roots that contain the desired type somewhere in their file hierarchy.
//        row.add(Root.COLUMN_MIME_TYPES, getChildMimeTypes(baseDir));
//        row.add(Root.COLUMN_AVAILABLE_BYTES, baseDir.getFreeSpace());
        row.add(Root.COLUMN_ICON, R.drawable.ic_launcher_foreground);

        return result;
    }

    private String getDocIdForFile(File file) {
        if(file.equals(getBaseDir())) {
            return ROOT_DOC_ID;
        } else {
            return file.getName();
        }
    }

    private File getBaseDir() {
        return Objects.requireNonNull(getContext()).getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
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
        File[] files = parent.listFiles();
        if(files != null) {
            for (File file : files) {
                // Adds the file's display name, MIME type, size, and so on.
                includeFile(result, getDocIdForFile(file), file);
            }
        }
        return result;
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
        if(!getBaseDir().equals(file)) {
            row.add(Document.COLUMN_FLAGS, Document.FLAG_SUPPORTS_DELETE | Document.FLAG_SUPPORTS_WRITE);
        }
    }

    private String[] resolveDocumentProjection(String[] projection) {
        return DEFAULT_DOCUMENT_PROJECTION;
    }

    private File getFileForDocId(String docId) throws FileNotFoundException {
        if(ROOT_DOC_ID.equals(docId)) {
            return getBaseDir();
        } else {
            File[] f = getBaseDir().listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.equals(docId);
                }
            });
            if(f == null) {
                Logging.log(Log.ERROR, TAG, "Unable to find file by id. Parent does not exist");
                throw new FileNotFoundException("Parent does not exist");
            }
            if (f.length != 1) {
                Logging.log(Log.ERROR, TAG, "Unable to find file by id. Files returned: %1$d", f.length);
                throw new FileNotFoundException("Unable to find file with name " + docId);
            }
            return f[0];
        }
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
                new PriorityQueue<>(5, new Comparator<File>() {

                    public int compare(File i, File j) {
                        return Long.compare(i.lastModified(), j.lastModified());
                    }
                });

        // Iterate through all files and directories
        // in the file structure under the root.  If
        // the file is more recent than the least
        // recently modified, add it to the queue,
        // limiting the number of results.
        final LinkedList<File> pending = new LinkedList<>();

        // Start by adding the parent to the list of files to be processed
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
        }
        notifySystemOfUpdatedDocumentUri(Objects.requireNonNull(getContext()), getDocIdForFile(Objects.requireNonNull(parent)));
    }

    @Override
    public boolean onCreate() {
        return true;
    }
}
