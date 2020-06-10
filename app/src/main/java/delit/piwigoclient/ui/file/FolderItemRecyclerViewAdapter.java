package delit.piwigoclient.ui.file;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.MimeTypeFilter;
import androidx.documentfile.provider.DocumentFile;

import com.crashlytics.android.Crashlytics;
import com.drew.lang.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;

import delit.libs.ui.OwnedSafeAsyncTask;
import delit.libs.ui.SafeAsyncTask;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.util.ParcelUtils;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapter;
import delit.libs.ui.view.recycler.BaseViewHolder;
import delit.libs.ui.view.recycler.CustomClickListener;
import delit.libs.util.IOUtils;
import delit.libs.util.LegacyIOUtils;
import delit.libs.util.ObjectUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.PicassoLoader;
import delit.piwigoclient.business.ResizingPicassoLoader;

import static delit.piwigoclient.ui.file.FolderItemViewAdapterPreferences.ALPHABETICAL;


public class FolderItemRecyclerViewAdapter extends BaseRecyclerViewAdapter<FolderItemViewAdapterPreferences, FolderItemRecyclerViewAdapter.FolderItem, FolderItemRecyclerViewAdapter.FolderItemViewHolder, BaseRecyclerViewAdapter.MultiSelectStatusListener<FolderItemRecyclerViewAdapter.FolderItem>> {

    public final static int VIEW_TYPE_FOLDER = 0;
    public final static int VIEW_TYPE_FILE = 1;
    public final static int VIEW_TYPE_FILE_IMAGE = 2;
    private Comparator<? super FolderItem> fileComparator;
    private NavigationListener navigationListener;
    private TreeMap<String, String> currentVisibleDocumentFileExts;
    private List<FolderItem> currentFullContent;
    private List<FolderItem> currentDisplayContent;
    private DocumentFile activeFolder;
    private Uri activeRootUri;
    private boolean isBusy;
    private AsyncTask activeTask;
    private WeakReference<Context> contextRef;//TODO get rid of this somehow!
    private TaskProgressListener taskListener;

    public FolderItemRecyclerViewAdapter(Context context, NavigationListener navigationListener, MultiSelectStatusListener<FolderItem> multiSelectStatusListener, FolderItemViewAdapterPreferences folderViewPrefs) {
        super(multiSelectStatusListener, folderViewPrefs);
        this.contextRef = new WeakReference<>(context);
        this.navigationListener = navigationListener;
    }

    protected SavedState saveState() {
        return new SavedState(this);
    }

    public void restoreState(SavedState state) {
        // don't call pre folder opened because that saves the state
        // navigationListener.onPreFolderOpened();
        DocumentFile oldFolder = activeFolder;
        state.restoreToAdapter(this);
        navigationListener.onPostFolderOpened(oldFolder, activeFolder);
    }

    public class SavedState {
        private boolean restored = false;
        private TreeMap<String, String> currentVisibleDocumentFileExts;
        private List<FolderItem> currentFullContent;
        private List<FolderItem> currentDisplayContent;
        private DocumentFile activeFolder;
        private Uri activeRootUri;

        private SavedState(FolderItemRecyclerViewAdapter adapter) {
            if(adapter.currentVisibleDocumentFileExts != null) {
                currentVisibleDocumentFileExts = new TreeMap<>(adapter.currentVisibleDocumentFileExts);
            }
            if(adapter.currentFullContent != null) {
                currentFullContent = new ArrayList<>(adapter.currentFullContent);
            }
            if(currentDisplayContent != null) {
                currentDisplayContent = new ArrayList<>(adapter.currentDisplayContent);
            }
            activeFolder = adapter.activeFolder;
            activeRootUri = adapter.activeRootUri;
        }

        protected void restoreToAdapter(FolderItemRecyclerViewAdapter adapter) {
            if(restored) {
                throw new IllegalStateException("already restored - this object cannot be reused as it doesn't take a copy");
            }
            adapter.currentVisibleDocumentFileExts = currentVisibleDocumentFileExts;
            adapter.currentFullContent = currentFullContent;
            adapter.currentDisplayContent = currentDisplayContent;
            adapter.activeFolder = activeFolder;
            adapter.activeRootUri = activeRootUri;
            restored = true;
        }


    }

    public void setInitiallySelectedItems(Context context) {
        SortedSet<Uri> initialSelectionItems = getAdapterPrefs().getInitialSelection();
        HashSet<Long> initialSelectionIds = null;
        if (initialSelectionItems != null) {
            initialSelectionIds = new HashSet<>(initialSelectionItems.size());
            for (Uri selectedItem : initialSelectionItems) {
                if(!"file".equals(selectedItem.getScheme())) {
                    int pos = getItemPositionForDocumentFile(DocumentFile.fromTreeUri(context, selectedItem));
                    if (pos >= 0) {
                        initialSelectionIds.add(getItemId(pos));
                    }
                }
            }
        }
        // update the visible selection.
        setInitiallySelectedItems(initialSelectionIds);
        if(initialSelectionIds.size() > 0) {
            setSelectedItems(initialSelectionIds);
        }
    }

    public void setTaskListener(TaskProgressListener taskListener) {
        this.taskListener = taskListener;
    }

    protected void updateContent(DocumentFile newContent, boolean force) {
        if(isBusy) {
            activeTask.cancel(true);
        }
        activeTask = new UpdateFolderContentTask(this, newContent, force).execute();
    }

    protected List<FolderItem> getNewDisplayContentInternal(DocumentFile newContent) {

        List<FolderItem> currentDisplayContent = null;
        activeFolder = newContent;
        getSelectedItemIds().clear(); // need to clear selection since position in list is used as unique item id
        // load all the children.

        if(activeFolder !=  null) {
            List<DocumentFile> folderContent;
            folderContent = new ArrayList<>(activeFolder.listFiles().length);
            Collections.addAll(folderContent, activeFolder.listFiles());
            currentDisplayContent = buildDisplayContent(folderContent);
        } else {
            currentDisplayContent = new ArrayList<>();
            // null used to mean no folder to show, but now means items without a folder shown so we leave the content intact.
        }
        if(Thread.currentThread().isInterrupted()) {
            return null;
        }

        boolean initialSetup = currentVisibleDocumentFileExts == null;
        currentVisibleDocumentFileExts = buildListOfFileExtsAndMimesInCurrentFolder(currentDisplayContent);
        if(initialSetup) {
            Set<String> desiredExts = getAdapterPrefs().getVisibleFileTypesForMimes(currentVisibleDocumentFileExts);
            getAdapterPrefs().withVisibleContent(desiredExts, getAdapterPrefs().getFileSortOrder());
        }

        Collections.sort(currentDisplayContent, getFolderItemComparator());

        return currentDisplayContent;
    }

    private List<FolderItem> getFilteredListOfContent(List<FolderItem> currentDisplayContent) {
        List<FolderItem> filteredContent = new ArrayList<>(currentDisplayContent.size());
        SortedSet<String> visibleFileExts = getAdapterPrefs().getVisibleFileTypes();
        //String[] visibleMimes = CollectionUtils.asStringArray(getAdapterPrefs().getVisibleMimeTypes());
        boolean showFolderContainedFiles = getAdapterPrefs().isAllowFileSelection();
        FolderItemFilter filter = new FolderItemFilter(showFolderContainedFiles, visibleFileExts, null);
        for(int i = currentDisplayContent.size() - 1; i >= 0; i--) {
            FolderItem folderItem = currentDisplayContent.get(i);
            if(filter.accept(folderItem)) {
                filteredContent.add(folderItem);
            }
            if(Thread.currentThread().isInterrupted()) {
                return null;
            }
        }
        return filteredContent;
    }

    private static class FolderItemFilter {

        private boolean showFolderContents;
        private Set<String> visibleFileTypes;
        private String[] visibleMimeTypes;

        public FolderItemFilter(boolean showFolderContents, Set<String> visibleFileTypes, String[] visibleMimeTypes) {
            this.showFolderContents = showFolderContents;
            this.visibleFileTypes = visibleFileTypes;
            this.visibleMimeTypes = visibleMimeTypes;
        }

        public boolean accept(FolderItem pathname) {
            return !showFolderContents || (pathname.isFolder() || (pathname.isFile() && filenameMatches(pathname))/*mimeTypeMatches(pathname)*/);
        }

        private boolean filenameMatches(FolderItem item) {
            if (visibleFileTypes == null) {
                return true;
            }
            if(visibleFileTypes.isEmpty()) {
                return false;
            }
            return visibleFileTypes.contains(item.getExt());
        }

        private boolean mimeTypeMatches(FolderItem pathname) {
            if (visibleMimeTypes == null) {
                return true;
            }
            String mimeType = pathname.getMime();
            return null != MimeTypeFilter.matches(mimeType, visibleMimeTypes);
        }
    }

    public void refreshContentView() {
        updateContent(activeFolder, false);
    }

    public void rebuildContentView() {
        updateContent(activeFolder, true);
    }

    public void resetRoot(DocumentFile activeRootFile) {
        if(isBusy) {
            activeTask.cancel(true);
        }
        if(activeRootFile == null) {
            this.activeRootUri = null;
        } else {
            this.activeRootUri = IOUtils.getTreeUri(activeRootFile.getUri());
        }
        clear();
        changeFolderViewed(activeRootFile);
    }

    public void updateContentAndRoot(@NonNull DocumentFile activeRootFile, @NonNull DocumentFile newFolder) {
        if(isBusy) {
            activeTask.cancel(true);
        }
        this.activeRootUri = IOUtils.getTreeUri(activeRootFile.getUri());
        clear();
        changeFolderViewed(newFolder);
    }

    public boolean changeFolderViewed(DocumentFile newContent) {
        updateContent(newContent, false);
        return true;
    }

    private List<FolderItem> buildDisplayContent(@NonNull List<DocumentFile> folderContent) {

        ArrayList<FolderItem> displayContent = new ArrayList<>();
        boolean preCacheName = getAdapterPrefs().getFileSortOrder() == ALPHABETICAL;
        boolean preCacheLastModified = !preCacheName;
        int itemCount = folderContent.size();
        for (int i = 0; i < itemCount; i++) {
            DocumentFile f = folderContent.get(i);
            displayContent.add(new FolderItem(activeRootUri, f, preCacheName, preCacheLastModified));
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }
            if(taskListener != null) {
                double progress = ((double)i)/itemCount;
                DisplayUtils.postOnUiThread(() -> taskListener.onTaskProgress(progress));
            }
        }
        return displayContent;
    }

    @Override
    protected CustomClickListener<FolderItemViewAdapterPreferences, FolderItem, FolderItemViewHolder> buildCustomClickListener(FolderItemViewHolder viewHolder) {
        return new FolderItemCustomClickListener(viewHolder, this);
    }

    public DocumentFile getActiveFolder() {
        return activeFolder;
    }

    public Comparator<? super FolderItem> getFolderItemComparator() {
        if (fileComparator == null) {
            fileComparator = buildFolderItemComparator();
        }
        return fileComparator;
    }

    protected Comparator<? super FolderItem> buildFolderItemComparator() {
        return (Comparator<FolderItem>) (o1, o2) -> {

            if (o1.isFolder() && !o2.isFolder()) {
                return 1;
            }
            if (!o1.isFolder() && o2.isFolder()) {
                return -1;
            }
            if (o1.isFolder() && o2.isFolder()) {
                return ObjectUtils.compare(o1.getName(), o2.getName());
            }
            switch (getAdapterPrefs().getFileSortOrder()) {
                case ALPHABETICAL:
                    return ObjectUtils.compare(o1.getName(), o2.getName());
                case FolderItemViewAdapterPreferences.LAST_MODIFIED_DATE:
                    if (o1.lastModified() == o2.lastModified()) {
                        return ObjectUtils.compare(o1.getName(), o2.getName());
                    } else {
                        // this is reversed order
                        return o1.lastModified() < o2.lastModified() ? -1 : 1;
                    }
                default:
                    return 0;
            }
        };
    }

    private @NonNull Context getContext() {
        return Objects.requireNonNull(contextRef.get());
    }

    @Override
    public int getItemViewType(int position) {
        FolderItem f = getItemByPosition(position);
        if (f.isFolder()) {
            return VIEW_TYPE_FOLDER;
        }
        return VIEW_TYPE_FILE;
    }

    @Override
    public long getItemId(int position) {
        return getItemByPosition(position).uid;
    }

    @Override
    protected FolderItem getItemById(Long selectedId) {
        if(selectedId == null) {
            return null;
        }
        List<FolderItem> content = currentFullContent;
        if(content == null || content.size() < currentDisplayContent.size()) {
            content = currentDisplayContent;
        }
        for(FolderItem item : content) {
            if(item.uid == selectedId) {
                return item;
            }
        }
        return null;
    }

    @NonNull
    protected View inflateView(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            default:
            case VIEW_TYPE_FILE:
            case VIEW_TYPE_FILE_IMAGE:
                return LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.layout_actionable_triselect_list_item_large_icon, parent, false);
            case VIEW_TYPE_FOLDER:
                return LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.layout_actionable_triselect_list_item_icon, parent, false);
        }

    }

    @NonNull
    @Override
    public FolderItemViewHolder buildViewHolder(View view, int viewType) {
        if (viewType == VIEW_TYPE_FOLDER) {
            return new FolderItemFolderViewHolder(view);
        } else {
            return new FolderItemDocumentFileViewHolder(view);
        }
        //TODO allow blank "folder" spacer items to correct the visual display.
    }

    private int getItemPositionForDocumentFile(DocumentFile f) {
        if(currentDisplayContent != null) {
            for (int i = 0; i < currentDisplayContent.size(); i++) {
                if (currentDisplayContent.get(i).getDocumentFile(getContext()).getUri().equals(f.getUri())) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    public int getItemPosition(FolderItem item) {
        if (currentDisplayContent == null) {
            throw new IllegalStateException("Please set the initial folder and initialise the list before attempting to access the items in the list");
        }
        return currentDisplayContent.indexOf(item);
    }

    @Override
    protected void removeItemFromInternalStore(int idxRemoved) {
        FolderItem f = currentDisplayContent.get(idxRemoved);
        if (f.getDocumentFile(getContext()).exists()) {
            f.getDocumentFile(getContext()).delete();
        }
        currentDisplayContent.remove(idxRemoved);
    }

    @Override
    protected void replaceItemInInternalStore(int idxToReplace, FolderItem newItem) {
        throw new UnsupportedOperationException("This makes no sense for a file structure traversal");
    }

    @Override
    protected FolderItem getItemFromInternalStoreMatching(FolderItem item) {
        // they'll always be the same
        return item;
    }

    @Override
    protected void addItemToInternalStore(FolderItem item) {
        addFolderItemToInternalStore(item);
        afterAddingFolderItemsToInternalStore();
    }


    private void afterAddingFolderItemsToInternalStore() {
        if(activeFolder == null) {
            navigationListener.onPreFolderOpened(null, getItems());
            activeTask = new UpdateContentListAndSortContentAfterAdd(this).execute();
        }
    }

    private TreeMap<String, String> buildListOfFileExtsAndMimesInCurrentFolder(List<FolderItem> currentDisplayContent) {
        TreeMap<String, String> currentVisibleDocumentFileExts = new TreeMap<>();
        for (FolderItem f : currentDisplayContent) {
            if (f.isFolder() || !f.isFile()) {
                continue;
            }

            if(f.getExt() != null && f.getMime() != null) {
                currentVisibleDocumentFileExts.put(f.getExt(), f.getMime());
            }
            if(Thread.currentThread().isInterrupted()) {
                return null;
            }
        }
        return currentVisibleDocumentFileExts;
    }

    private void addFolderItemToInternalStore(FolderItem item) {
        DocumentFile docFile = item.getDocumentFile(getContext());
        if (!docFile.exists()) {
            throw new IllegalStateException("Cannot add DocumentFile to display that does not yet exist");
        }
        DocumentFile parentFile = docFile.getParentFile();
        if (!ObjectUtils.areEqual(parentFile,activeFolder)) { // object equals works as they're referencing same objects
            throw new IllegalArgumentException("DocumentFile is not a child of the currently displayed folder");
        }
        if(activeFolder == null && currentDisplayContent == null) {
            // this is used when loading items from the system picker into this, using this as a bucket.
            currentDisplayContent = new ArrayList<>();
        }

        currentDisplayContent.add(item);
    }

    @Override
    public FolderItem getItemByPosition(int position) {
        if(currentDisplayContent.size() <= position) {
            return null;
        }
        return currentDisplayContent.get(position);
    }

    @Override
    public boolean isHolderOutOfSync(FolderItemViewHolder holder, FolderItem newItem) {
        return isDirtyItemViewHolder(holder, newItem);
    }

    public Uri getActiveRootUri() {
        return activeRootUri;
    }

    public void addItems(List<FolderItem> folderItems) {
        if(folderItems != null) {
            for (FolderItem item : folderItems) {
                addFolderItemToInternalStore(item);
            }
            afterAddingFolderItemsToInternalStore();
        }
    }

    public DocumentFile[] getItems() {
        if(currentDisplayContent == null) {
            return null;
        }
        List<DocumentFile> docFiles = new ArrayList<>(currentDisplayContent.size());
        for(FolderItem item : currentDisplayContent) {
            docFiles.add(item.getDocumentFile(getContext()));
        }
        return docFiles.toArray(new DocumentFile[0]);
    }

    public void clear() {
        if(currentDisplayContent != null) {
            currentDisplayContent.clear();
        }
    }

    public static class FolderItem implements Parcelable {
        private static final AtomicLong uidGen = new AtomicLong();
        private long uid;
        private Uri rootUri;
        private Uri itemUri;
        private DocumentFile itemDocFile;
        private Boolean isFolder;
        private Boolean isFile; // items are not files or folders if they are special system files
        private long lastModified = -1;
        private String name;
        private String ext;
        private String mime;
        private long fileLength;

        public FolderItem(Uri itemUri) {
            this.itemUri = itemUri;
            uid = uidGen.getAndIncrement();
        }

        public FolderItem(Uri rootUri, DocumentFile itemDocFile, boolean preCacheName, boolean preCacheLastModified) {
            this.itemDocFile = itemDocFile;
            this.rootUri = rootUri;
            this.itemUri = itemDocFile.getUri(); // used for persistence
            if(preCacheLastModified) {
                this.lastModified = itemDocFile.lastModified();
            }
            if(preCacheName) {
                this.name = IOUtils.getFilename(itemDocFile);
            }
            this.isFolder = itemDocFile.isDirectory();
            this.isFile = itemDocFile.isFile();
            this.mime = (isFolder||!isFile)?null:itemDocFile.getType();
            //Note: We're kind of stuck - if we use filename or docFile, we need to retrieve the filename first....
            // best to guess from the uri path
            this.ext = (isFolder||!isFile)?null:IOUtils.getFileExt(this.name != null ? this.name : this.itemUri.getPath(), mime);
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
            if(isFolder == null) {
                if(itemDocFile == null) {
                    throw new IllegalStateException("document file not available to allow field retrieval");
                } else {
                    this.isFolder = itemDocFile.isDirectory();
                }
            }
            return isFolder;
        }

        public boolean isFile() {
            if(isFile == null) {
                if(itemDocFile == null) {
                    throw new IllegalStateException("document file not available to allow field retrieval");
                } else {
                    this.isFile = itemDocFile.isFile();
                }
            }
            return isFile;
        }

        public long lastModified() {
            if(lastModified < 0) {
                if(itemDocFile == null) {
                    throw new IllegalStateException("document file not available to allow field retrieval");
                } else {
                    this.lastModified = itemDocFile.lastModified();
                }
            }
            return lastModified;
        }

        public String getMime() {
            if(mime == null) {
                if(itemDocFile == null) {
                    throw new IllegalStateException("document file not available to allow field retrieval");
                } else {
                    this.mime = itemDocFile.getType();
                }
            }
            return mime;
        }

        public String getExt() {
            if(ext == null) {
                if(itemDocFile == null) {
                    throw new IllegalStateException("document file not available to allow field retrieval");
                } else {
                    if(isFolder()||!isFile()) {
                        return ext;
                    } else {
                        ext = IOUtils.getFileExt(this.name != null ? this.name : this.itemUri.getPath(), mime);
                    }
                }
            }
            return ext;
        }

        public String getName() {
            if(name == null) {
                if(itemDocFile == null) {
                    throw new IllegalStateException("document file not available to allow field retrieval");
                } else {
                    this.name = itemDocFile.getName();
                }
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
        public @Nullable DocumentFile getDocumentFile(Context context) {
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

        public @Nullable DocumentFile cacheDocFileFields(Context context) {
            if(null != getDocumentFile(context)) {
                isFile();
                isFolder();
                lastModified();
                getMime();
                getName();
                getExt();
                getFileLength();
            }
            return itemDocFile;
        }

        public long getFileLength() {
            if(itemDocFile == null) {
                throw new IllegalStateException("document file not available to allow field retrieval");
            } else {
                this.fileLength = itemDocFile.length();
            }
            return fileLength;
        }

        public void withLegacyCachedFields() throws IOException {
            File f = LegacyIOUtils.getFile(itemUri);
            if(f == null) {
                throw new IOException("Uri is not a file");
            }
            isFolder = f.isDirectory();
            isFile = f.isFile();
            name = f.getName();
            ext = IOUtils.getFileExt(name);
            mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            lastModified = f.lastModified();
            fileLength = f.length();
        }

        public void withMediaStoreCachedFields(Context context) {
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
                }
            }
        }
    }

    @Override
    public HashSet<Long> getItemsSelectedButNotLoaded() {
        return new HashSet<>(0);
    }

    @Override
    public int getItemCount() {
        return currentDisplayContent == null ? 0 : currentDisplayContent.size();
    }

    public SortedSet<String> getFileExtsInCurrentFolder() {
        return new TreeSet<>(currentVisibleDocumentFileExts.keySet());
    }

    public TreeMap<String, String> getFileExtsAndMimesInCurrentFolder() {
        return currentVisibleDocumentFileExts;
    }

    public interface NavigationListener {
        void onPreFolderOpened(DocumentFile oldFolder, DocumentFile[] newFolderContent);

        void onPostFolderOpened(DocumentFile oldFolder, DocumentFile newFolder);
    }

    class FolderItemCustomClickListener extends CustomClickListener<FolderItemViewAdapterPreferences, FolderItem, FolderItemViewHolder> {
        public FolderItemCustomClickListener(FolderItemViewHolder viewHolder, FolderItemRecyclerViewAdapter parentAdapter) {
            super(viewHolder, parentAdapter);
        }

        @Override
        public void onClick(View v) {
            if(isBusy) {
                return;
            }
            if (getViewHolder().getItemViewType() == VIEW_TYPE_FOLDER) {
                FolderItem folderItem = getViewHolder().getItem();
                changeFolderViewed(folderItem.getDocumentFile(v.getContext()));
            } else if (getAdapterPrefs().isAllowFileSelection()) {
                super.onClick(v);
            }
        }

        @Override
        public boolean onLongClick(View v) {
            if(isBusy) {
                return false;
            }
            if (getViewHolder().getItemViewType() == VIEW_TYPE_FOLDER && getAdapterPrefs().isAllowFolderSelection()) {
                super.onClick(v);
            }
            return super.onLongClick(v);
        }
    }

    protected class FolderItemFolderViewHolder extends FolderItemViewHolder {

        public FolderItemFolderViewHolder(View view) {
            super(view);
        }

        @Override
        public void fillValues(FolderItem newItem, boolean allowItemDeletion) {
            setItem(newItem);
            getTxtTitle().setVisibility(View.VISIBLE);
            getTxtTitle().setText(IOUtils.getFilename(newItem.getDocumentFile(itemView.getContext())));
            if (!allowItemDeletion) {
                getDeleteButton().setVisibility(View.GONE);
            }
            getCheckBox().setVisibility(getAdapterPrefs().isAllowFolderSelection() ? View.VISIBLE : View.GONE);
            getCheckBox().setChecked(getSelectedItems().contains(newItem));
            getCheckBox().setEnabled(isEnabled());
        }

        @Override
        public void cacheViewFieldsAndConfigure(FolderItemViewAdapterPreferences adapterPrefs) {
            super.cacheViewFieldsAndConfigure(adapterPrefs);
            getIconView().setColorFilter(ContextCompat.getColor(itemView.getContext(),R.color.app_secondary), PorterDuff.Mode.SRC_IN);
            getIconViewLoader().setResourceToLoad(R.drawable.ic_folder_black_24dp);
            getIconViewLoader().load();
        }
    }

    protected class FolderItemDocumentFileViewHolder extends FolderItemViewHolder {

        private TextView itemHeading;
        private static final String TAG = "FolderItemDocFileVH";

        public FolderItemDocumentFileViewHolder(View view) {
            super(view);
        }

        @Override
        public void fillValues(FolderItem newItem, boolean allowItemDeletion) {
            setItem(newItem);

            long bytes = newItem.getFileLength();

            double sizeMb = IOUtils.bytesToMb(bytes);
            itemHeading.setVisibility(View.VISIBLE);
            //IOUtils.toNormalizedText(bytes);
            itemHeading.setText(String.format(itemView.getContext().getString(R.string.File_size_pattern), sizeMb));

            if (getAdapterPrefs().isShowFilenames()) {
                getTxtTitle().setVisibility(View.VISIBLE);
                getTxtTitle().setText(newItem.getName());
            } else {
                getTxtTitle().setVisibility(View.GONE);
            }
            if (!allowItemDeletion) {
                getDeleteButton().setVisibility(View.GONE);
            }
            getCheckBox().setVisibility(getAdapterPrefs().isAllowFileSelection() ? View.VISIBLE : View.GONE);
            getCheckBox().setChecked(getSelectedItemIds().contains(newItem.uid));
            getCheckBox().setEnabled(isEnabled());
            Uri itemUri = newItem.getContentUri();
            if (itemUri != null) {
                getIconViewLoader().setUriToLoad(itemUri.toString());
            } else {
                getIconViewLoader().setResourceToLoad(R.drawable.ic_file_gray_24dp);
            }
        }

        @Override
        public void cacheViewFieldsAndConfigure(FolderItemViewAdapterPreferences adapterPrefs) {
            super.cacheViewFieldsAndConfigure(adapterPrefs);
            itemHeading = itemView.findViewById(R.id.list_item_heading);
            getIconViewLoader().withErrorDrawable(R.drawable.ic_file_gray_24dp);
            final ViewTreeObserver.OnPreDrawListener predrawListener = () -> {
                if (!getIconViewLoader().isImageLoaded() && !getIconViewLoader().isImageLoading() && !getIconViewLoader().isImageUnavailable()) {

                    int imgSize = getIconView().getMeasuredWidth();
                    getIconViewLoader().setResizeTo(imgSize, imgSize);
                    getIconViewLoader().load();
                }
                return true;
            };
            getIconView().addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    getIconView().getViewTreeObserver().addOnPreDrawListener(predrawListener);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    getIconView().getViewTreeObserver().removeOnPreDrawListener(predrawListener);
                }
            });
//            getIconViewLoader().setResourceToLoad(R.drawable.ic_file_black_24dp);
//            getIconViewLoader().load();
        }
    }

    protected abstract static class FolderItemViewHolder extends BaseViewHolder<FolderItemViewAdapterPreferences, FolderItem> implements PicassoLoader.PictureItemImageLoaderListener {

        private ImageView iconView;
        private ResizingPicassoLoader<ImageView> iconViewLoader;

        public FolderItemViewHolder(View view) {
            super(view);
        }

        public ImageView getIconView() {
            return iconView;
        }

        public ResizingPicassoLoader getIconViewLoader() {
            return iconViewLoader;
        }

        public abstract void fillValues(FolderItem newItem, boolean allowItemDeletion);

        @Override
        public void cacheViewFieldsAndConfigure(FolderItemViewAdapterPreferences adapterPrefs) {

            super.cacheViewFieldsAndConfigure(adapterPrefs);

            iconView = itemView.findViewById(R.id.list_item_icon_thumbnail);
            iconView.setContentDescription("folder item thumb");
            iconViewLoader = new ResizingPicassoLoader<>(getIconView(), this, 0, 0);
        }

        @Override
        public void onBeforeImageLoad(PicassoLoader loader) {
            getIconView().setBackgroundColor(Color.TRANSPARENT);
        }

        @Override
        public void onImageLoaded(PicassoLoader loader, boolean success) {
            getIconView().setBackgroundColor(Color.TRANSPARENT);
        }

        @Override
        public void onImageUnavailable(PicassoLoader loader, String lastLoadError) {
            getIconView().setBackgroundColor(Color.DKGRAY);
        }
    }

    private static class UpdateContentListAndSortContentAfterAdd extends OwnedSafeAsyncTask<FolderItemRecyclerViewAdapter, Object,Object,Object> {
        private static final String TAG = "UpdateContentListAndSort";

        public UpdateContentListAndSortContentAfterAdd(FolderItemRecyclerViewAdapter folderItemRecyclerViewAdapter) {
            super(folderItemRecyclerViewAdapter);
        }

        @Override
        protected void onPreExecuteSafely() {
            getOwner().isBusy = true;
        }

        @Override
        protected Object doInBackgroundSafely(Object[] objects) {
            getOwner().currentVisibleDocumentFileExts = getOwner().buildListOfFileExtsAndMimesInCurrentFolder(getOwner().currentDisplayContent);
            if(Thread.currentThread().isInterrupted()) {
                return null;
            }
            Collections.sort(getOwner().currentDisplayContent, getOwner().getFolderItemComparator());
            return null;
        }

        @Override
        protected void onPostExecuteSafely(Object o) {
            super.onPostExecuteSafely(o);
            getOwner().notifyDataSetChanged();
            // this will trigger rebuild of the file filters view and possibly post filter the files selected
            getOwner().navigationListener.onPostFolderOpened(null, null);
            getOwner().isBusy = false;
        }

        @Override
        protected void onCancelledSafely() {
            getOwner().isBusy = false;
            Crashlytics.log(Log.WARN, TAG, "Async Task cancelled");
        }
    }

    private static class UpdateFolderContentTask extends OwnedSafeAsyncTask<FolderItemRecyclerViewAdapter, Object,Object,List<FolderItem>[]> {

        private static final String TAG = "UpdateFolderContentTask";
        private final DocumentFile newContent;
        private final boolean force;
        private boolean refreshingExistingFolder;
        private DocumentFile oldFolder;

        public UpdateFolderContentTask(FolderItemRecyclerViewAdapter folderItemRecyclerViewAdapter, DocumentFile newContent, boolean force) {
            super(folderItemRecyclerViewAdapter);
            this.newContent = newContent;
            this.force = force;
        }

        @Override
        protected void onPreExecuteSafely() {
            super.onPreExecuteSafely();
            if(getOwner().taskListener != null) {
                getOwner().taskListener.onTaskStarted();
            }
            getOwner().isBusy = true;
            if(getOwner().activeRootUri == null) {
                getOwner().activeRootUri = newContent != null ? IOUtils.getTreeUri(newContent.getUri()) : null;
            }
            refreshingExistingFolder = false;
            Uri activeUri = getOwner().activeFolder != null ? getOwner().activeFolder.getUri() : null;
            Uri newUri = newContent != null ? newContent.getUri() : null;
            if (ObjectUtils.areEqual(activeUri, newUri)) {
                /*if (!force && getOwner().currentDisplayContent != null && getOwner().activeFolder != null) {
                    return;
                } else {
                    refreshingExistingFolder = true;
                }*/
                refreshingExistingFolder = true;
            }

            oldFolder = getOwner().activeFolder;

            if (!refreshingExistingFolder) {
                getOwner().navigationListener.onPreFolderOpened(oldFolder, newContent != null ? newContent.listFiles() : null);
            }
        }

        @Override
        protected List<FolderItem>[] doInBackgroundSafely(Object[] objects) {
            List<FolderItem> fullContent = getOwner().currentFullContent;
            if(!refreshingExistingFolder || force) {
                fullContent = getOwner().getNewDisplayContentInternal(newContent);
            }
            List<FolderItem> filteredContent = fullContent;
            if(fullContent != null) {
                filteredContent = getOwner().getFilteredListOfContent(fullContent);
            }
            return new List[]{fullContent, filteredContent};
        }

        @Override
        protected void onPostExecuteSafely(List<FolderItem>[] result) {
            if(result != null) {
                // result only null if the task was cancelled.
                getOwner().currentFullContent = result[0];
                getOwner().currentDisplayContent = result[1];
                getOwner().notifyDataSetChanged();
                if (!refreshingExistingFolder) {
                    getOwner().navigationListener.onPostFolderOpened(oldFolder, newContent);
                }
                getOwner().isBusy = false;
                if (getOwner().taskListener != null) {
                    getOwner().taskListener.onTaskFinished();
                }
            }
        }

        @Override
        protected void onCancelledSafely() {
            super.onCancelledSafely();
            getOwner().isBusy = false;
            Crashlytics.log(Log.WARN, TAG, "Async Task cancelled");
        }
    }
}
