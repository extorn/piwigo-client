package delit.piwigoclient.ui.file;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.MimeTypeFilter;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.exoplayer2.util.MimeTypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import delit.libs.core.util.Logging;
import delit.libs.ui.OwnedSafeAsyncTask;
import delit.libs.ui.SafeAsyncTask;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapter;
import delit.libs.ui.view.recycler.BaseViewHolder;
import delit.libs.ui.view.recycler.CustomClickListener;
import delit.libs.util.CollectionUtils;
import delit.libs.util.IOUtils;
import delit.libs.util.ObjectUtils;
import delit.libs.util.progress.TaskProgressListener;
import delit.piwigoclient.R;
import delit.piwigoclient.business.PicassoLoader;
import delit.piwigoclient.business.ResizingPicassoLoader;

import static delit.piwigoclient.ui.file.FolderItemViewAdapterPreferences.ALPHABETICAL;

public class FolderItemRecyclerViewAdapter<LVA extends FolderItemRecyclerViewAdapter<LVA,T,MSL,VH>, T extends FolderItem, MSL extends BaseRecyclerViewAdapter.MultiSelectStatusListener<MSL,LVA,FolderItemViewAdapterPreferences,T,VH>, VH extends FolderItemRecyclerViewAdapter.FolderItemViewHolder<VH, LVA, T, MSL>> extends BaseRecyclerViewAdapter<LVA, FolderItemViewAdapterPreferences, T, VH, MSL> {
    public final static int VIEW_TYPE_FOLDER = 0;
    public final static int VIEW_TYPE_FILE = 1;
    public final static int VIEW_TYPE_FILE_IMAGE = 2;
    private static final String TAG = "FolderItemRecAdap";
    private Comparator<? super FolderItem> fileComparator;
    final NavigationListener navigationListener;
    private TreeMap<String, String> currentVisibleDocumentFileExts;
    List<T> currentFullContent;
    List<T> currentDisplayContent;
    DocumentFile activeFolder;
    Uri activeRootUri;
    boolean isBusy;
    private SafeAsyncTask activeTask;
    TaskProgressListener taskListener;
    Set<String> currentFileTypesToShow = new HashSet<>();

    public FolderItemRecyclerViewAdapter(NavigationListener navigationListener, MSL multiSelectStatusListener, FolderItemViewAdapterPreferences folderViewPrefs) {
        super(multiSelectStatusListener, folderViewPrefs);
        this.navigationListener = navigationListener;
        setHasStableIds(true);
    }

    public boolean isBusy() {
        return isBusy;
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

    public void removeFileTypeToShow(String fileExt) {
        currentFileTypesToShow.remove(fileExt);
    }

    public void addFileTypeToShow(String fileExt) {
        currentFileTypesToShow.add(fileExt);
    }

    public void clearAndAddAllFileTypesToShow(SortedSet<String> visibleFileTypes) {
        currentFileTypesToShow.clear();
        currentFileTypesToShow.addAll(visibleFileTypes);
    }

    public static class SavedState {
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
            if(adapter.currentDisplayContent != null) {
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

    protected void updateContent(@NonNull Context context, DocumentFile newContent, boolean force) {
        if(isBusy) {
            activeTask.cancelSafely(true);
        }
        Logging.log(Log.DEBUG, TAG, "Invoking Background task - UpdateFolderContentTask");
//        Logging.recordException(new Exception().fillInStackTrace());
        activeTask = new UpdateFolderContentTask(this, newContent, force).withContext(context);
        activeTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    protected List<T> getNewDisplayContentInternal(@NonNull Context context, DocumentFile newContent) {

        List<T> currentDisplayContent;
        activeFolder = newContent;
        getSelectedItemIds().clear(); // need to clear selection since position in list is used as unique item id
        // load all the children.

        if(activeFolder !=  null) {
            List<DocumentFile> folderContent;
            folderContent = new ArrayList<>(activeFolder.listFiles().length);
            Collections.addAll(folderContent, activeFolder.listFiles());
            currentDisplayContent = buildDisplayContent(context, folderContent);
            if(currentDisplayContent == null) {
                Logging.log(Log.DEBUG, TAG, "Thread interrupted - returning null immediately");
                return null;
            }
        } else {
            currentDisplayContent = new ArrayList<>();
            // null used to mean no folder to show, but now means items without a folder shown so we leave the content intact.
        }

        boolean initialSetup = currentVisibleDocumentFileExts == null;
        currentVisibleDocumentFileExts = buildListOfFileExtsAndMimesInCurrentFolder(currentDisplayContent);
        if(initialSetup) {
            currentFileTypesToShow = getAdapterPrefs().getVisibleFileTypesForMimes(currentVisibleDocumentFileExts);
            currentFullContent = currentDisplayContent;
        }

        Collections.sort(currentDisplayContent, getFolderItemComparator());

        return currentDisplayContent;
    }

    protected List<FolderItem> getFilteredListOfContent(List<FolderItem> currentDisplayContent) {
        List<FolderItem> filteredContent = new ArrayList<>(currentDisplayContent.size());
        //String[] visibleMimes = CollectionUtils.asStringArray(getAdapterPrefs().getVisibleMimeTypes());
        boolean showFolderContainedFiles = getAdapterPrefs().isShowFolderContent();
        FolderItemFilter filter = new FolderItemFilter(showFolderContainedFiles, currentFileTypesToShow, null);
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

        private static final String TAG = "FolderItemFilter";
        private boolean showFolderContents;
        private Set<String> visibleFileTypes;
        private String[] visibleMimeTypes;

        public FolderItemFilter(boolean showFolderContents, Set<String> visibleFileTypes, String[] visibleMimeTypes) {
            this.showFolderContents = showFolderContents;
            this.visibleFileTypes = visibleFileTypes;
            this.visibleMimeTypes = visibleMimeTypes;
        }

        public boolean accept(FolderItem pathname) {
            return pathname.isFolder() || (showFolderContents && pathname.isFile() && filenameMatches(pathname)/*mimeTypeMatches(pathname)*/);
        }

        private boolean filenameMatches(FolderItem item) {
            if(item == null) {
                Logging.log(Log.WARN, TAG, "Folder adapter seems to contain a null item which is very odd");
                return false;
            }
            if (visibleFileTypes == null) {
                return true;
            }
            if(visibleFileTypes.isEmpty()) {
                return false;
            }
            if(item.getExt() == null) {
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

    public void refreshContentView(@NonNull Context context) {
        updateContent(context, activeFolder, false);
    }

    public void rebuildContentView(@NonNull Context context) {
        updateContent(context, activeFolder, true);
    }

    public void resetRoot(@NonNull Context context, DocumentFile activeRootFile) {
        if(isBusy) {
            activeTask.cancelSafely(true);
        }
        if(activeRootFile == null) {
            this.activeRootUri = null;
        } else {
            this.activeRootUri = IOUtils.getTreeUri(activeRootFile.getUri());
        }
        clear();
        changeFolderViewed(context, activeRootFile);
    }

    public void updateContentAndRoot(@NonNull Context context, @NonNull DocumentFile activeRootFile, @NonNull DocumentFile newFolder) {
        if(isBusy) {
            activeTask.cancelSafely(true);
        }
        this.activeRootUri = IOUtils.getTreeUri(activeRootFile.getUri());
        clear();
        changeFolderViewed(context, newFolder);
    }

    public boolean changeFolderViewed(@NonNull Context context, DocumentFile newContent) {
        updateContent(context, newContent, false);
        return true;
    }

    private List<T> buildDisplayContent(Context context, @NonNull List<DocumentFile> folderContent) {

        if(folderContent.isEmpty()) {
            return new ArrayList<>();
        }

        List<T> displayContent = new ArrayList<>(folderContent.size());
        int itemCount = folderContent.size();
//        String[] permissableMimeTypes = CollectionUtils.asStringArray(getAdapterPrefs().getVisibleMimeTypes());

        for (int i = 0; i < itemCount; i++) {
            DocumentFile f = folderContent.get(i);
            T folderItem = (T)new FolderItem(activeRootUri, f);
            displayContent.add(folderItem);
        }

        boolean success = FolderItem.cacheDocumentInformation(context, displayContent, taskListener);
        if(!success) {
            return null;
        }

        for(T item : displayContent) {
            if(!item.isFolder() && (item.getLastModified() == 0 /*&& MimeTypeFilter.matches(folderItem.getMime(), permissableMimeTypes) != null*/)) { // 0 length check hides system files.
                displayContent.remove(item); // this isn't a file we can do anything useful with.
            }
        }



        return displayContent;
    }



    @Override
    protected CustomClickListener<MSL,LVA, FolderItemViewAdapterPreferences, T, VH> buildCustomClickListener(VH viewHolder) {
        return new FolderItemCustomClickListener<>(viewHolder, (LVA)this);
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
                    if (o1.getLastModified() == o2.getLastModified()) {
                        return ObjectUtils.compare(o1.getName(), o2.getName());
                    } else {
                        // this is reversed order
                        return o1.getLastModified() < o2.getLastModified() ? -1 : 1;
                    }
                default:
                    return 0;
            }
        };
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
        return getItemByPosition(position).getUid();
    }

    @Override
    protected T getItemById(@NonNull Long selectedId) {
        if(selectedId == null) {
            return null;
        }
        List<T> content = currentFullContent;
        if(content == null || content.size() < currentDisplayContent.size()) {
            content = currentDisplayContent;
        }
        for(T item : content) {
            if(item.getUid() == selectedId) {
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
                        .inflate(delit.libs.R.layout.layout_actionable_triselect_list_item_large_icon, parent, false);
            case VIEW_TYPE_FOLDER:
                return LayoutInflater.from(parent.getContext())
                        .inflate(delit.libs.R.layout.layout_actionable_triselect_list_item_icon, parent, false);
        }

    }

    @NonNull
    @Override
    public VH buildViewHolder(View view, int viewType) {
        if (viewType == VIEW_TYPE_FOLDER) {
            return (VH)new FolderItemFolderViewHolder(view);
        } else {
            return (VH)new FolderItemDocumentFileViewHolder(view);
        }
        //TODO allow blank "folder" spacer items to correct the visual display.
    }

    private int getItemPositionForDocumentFile(DocumentFile f) {
        if(currentDisplayContent != null) {
            for (int i = 0; i < currentDisplayContent.size(); i++) {
                if (currentDisplayContent.get(i).getDocumentFile().getUri().equals(f.getUri())) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    public int getItemPosition(@NonNull T item) {
        if (currentDisplayContent == null) {
            throw new IllegalStateException("Please set the initial folder and initialise the list before attempting to access the items in the list");
        }
        return currentDisplayContent.indexOf(item);
    }

    @Override
    protected T removeItemFromInternalStore(int idxRemoved) {
        FolderItem f = currentDisplayContent.get(idxRemoved);
        if (f.getDocumentFile().exists()) {
            f.getDocumentFile().delete();
        }
        currentDisplayContent.remove(idxRemoved);
        return null;
    }

    @Override
    protected void replaceItemInInternalStore(int idxToReplace, @NonNull FolderItem newItem) {
        throw new UnsupportedOperationException("This makes no sense for a file structure traversal");
    }

    @NonNull
    @Override
    protected T getItemFromInternalStoreMatching(@NonNull FolderItem item) {
        // they'll always be the same
        return (T) item;
    }

    @Override
    protected void addItemToInternalStore(@NonNull FolderItem item) {
        addFolderItemToInternalStore((T) item);
    }


    private void afterAddingFolderItemsToInternalStore(@NonNull Context context) {
        if(activeFolder == null) {
            navigationListener.onPreFolderOpened(null, getItems());
            Logging.log(Log.DEBUG, TAG, "Invoking Background task (afterAddingFolderItemsToInternalStore) - UpdateContentListAndSortContentAfterAdd");
//            Logging.recordException(new Exception().fillInStackTrace());
            activeTask = new UpdateContentListAndSortContentAfterAdd(this).withContext(context);
            activeTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    private TreeMap<String, String> buildListOfFileExtsAndMimesInCurrentFolder(@Nullable List<T> currentDisplayContent) {
        TreeMap<String, String> currentVisibleDocumentFileExts = new TreeMap<>();
        if(currentDisplayContent == null) {
            return currentVisibleDocumentFileExts;
        }
        for (FolderItem f : currentDisplayContent) {
            if (f.isFolder() || !f.isFile()) {
                continue;
            }

            if(f.getExt() != null && f.getMime() != null) {
                currentVisibleDocumentFileExts.put(f.getExt(), f.getMime());
            }
        }
        return currentVisibleDocumentFileExts;
    }

    private void addFolderItemToInternalStore(T item) {
        DocumentFile docFile = item.getDocumentFile();
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
        if(currentFullContent == null) {
            currentFullContent = new ArrayList<>();
        }
        currentFullContent.add(item);
        currentDisplayContent.add(item);
    }

    @NonNull
    @Override
    public T getItemByPosition(int position) {
        if(currentDisplayContent.size() <= position) {
            return null;
        }
        return currentDisplayContent.get(position);
    }

    @Override
    public boolean isHolderOutOfSync(VH holder, T newItem) {
        return isDirtyItemViewHolder(holder, newItem);
    }

    public Uri getActiveRootUri() {
        return activeRootUri;
    }

    public void addItems(@NonNull Context context, List<T> folderItems) {
        if(folderItems != null) {
            for (T item : folderItems) {
                addFolderItemToInternalStore(item);
            }
            afterAddingFolderItemsToInternalStore(context);
        }
    }

    public DocumentFile[] getItems() {
        if(currentDisplayContent == null) {
            return null;
        }
        List<DocumentFile> docFiles = new ArrayList<>(currentDisplayContent.size());
        for(FolderItem item : currentDisplayContent) {
            docFiles.add(item.getDocumentFile());
        }
        return docFiles.toArray(new DocumentFile[0]);
    }

    public void clear() {
        if(currentDisplayContent != null) {
            currentDisplayContent.clear();
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

    static class FolderItemCustomClickListener<LVA extends FolderItemRecyclerViewAdapter<LVA,T,MSL,VH>, T extends FolderItem, MSL extends BaseRecyclerViewAdapter.MultiSelectStatusListener<MSL,LVA,FolderItemViewAdapterPreferences,T,VH>, VH extends FolderItemRecyclerViewAdapter.FolderItemViewHolder<VH, LVA, T, MSL>> extends CustomClickListener<MSL,LVA, FolderItemViewAdapterPreferences, T, VH> {
        public FolderItemCustomClickListener(VH viewHolder, LVA parentAdapter) {
            super(viewHolder, parentAdapter);
        }

        @Override
        public void onClick(View v) {
            if(getParentAdapter().isBusy()) {
                return;
            }
            if (getViewHolder().getItemViewType() == VIEW_TYPE_FOLDER) {
                FolderItem folderItem = getViewHolder().getItem();
                getParentAdapter().changeFolderViewed(v.getContext(), folderItem.getDocumentFile());
            } else if (getParentAdapter().getAdapterPrefs().isAllowFileSelection()) {
                super.onClick(v);
            }
        }

        @Override
        public boolean onLongClick(View v) {
            if(getParentAdapter().isBusy()) {
                return false;
            }
            if (getViewHolder().getItemViewType() == VIEW_TYPE_FOLDER && getParentAdapter().getAdapterPrefs().isAllowFolderSelection()) {
                super.onClick(v);
            }
            return super.onLongClick(v);
        }
    }

    protected class FolderItemFolderViewHolder extends FolderItemViewHolder<VH, LVA, T, MSL> {

        public FolderItemFolderViewHolder(View view) {
            super(view);
        }

        @Override
        public void fillValues(T newItem, boolean allowItemDeletion) {
            setItem((T) newItem);
            getTxtTitle().setVisibility(View.VISIBLE);
            getTxtTitle().setText(newItem.getName());
            if (!allowItemDeletion) {
                getDeleteButton().setVisibility(View.GONE);
            }
            getCheckBox().setVisibility(getAdapterPrefs().isAllowFolderSelection() ? View.VISIBLE : View.GONE);
            getCheckBox().setChecked(getSelectedItems().contains((T)newItem));
            getCheckBox().setEnabled(isEnabled());
            getIconViewLoader().load();
        }

        @Override
        public void cacheViewFieldsAndConfigure(FolderItemViewAdapterPreferences adapterPrefs) {
            super.cacheViewFieldsAndConfigure(adapterPrefs);
            getIconView().setColorFilter(ContextCompat.getColor(itemView.getContext(),R.color.app_secondary), PorterDuff.Mode.SRC_IN);
            getIconViewLoader().setResourceToLoad(R.drawable.ic_folder_black_24dp);
        }
    }

    protected class FolderItemDocumentFileViewHolder extends FolderItemViewHolder<VH, LVA, T, MSL> {

        private TextView itemHeading;
        private static final String TAG = "FolderItemDocFileVH";

        public FolderItemDocumentFileViewHolder(View view) {
            super(view);
        }

        @SuppressLint("UseCompatLoadingForDrawables")
        @Override
        public void fillValues(FolderItem newItem, boolean allowItemDeletion) {
            setItem((T) newItem);

            long bytes = newItem.getFileLength();
            itemHeading.setVisibility(View.VISIBLE);
            itemHeading.setText(IOUtils.bytesToNormalizedText(bytes));

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
            getCheckBox().setChecked(getSelectedItemIds().contains(newItem.getUid()));
            getCheckBox().setEnabled(isEnabled());
            Uri itemUri = newItem.getContentUri();
            if (itemUri != null) {
                getIconViewLoader().setUriToLoad(itemUri.toString());
            } else {
                getIconViewLoader().setResourceToLoad(R.drawable.ic_file_gray_24dp);
            }
            getIconViewLoader().load();

            ImageView mimeIndicator = getMimeTypeIndicatorView();
            if(MimeTypes.isVideo(newItem.getMime())) {
                mimeIndicator.setImageDrawable(itemView.getResources().getDrawable(R.drawable.ic_movie_filter_black_24px));
                mimeIndicator.setVisibility(View.VISIBLE);
            } else if(MimeTypes.isAudio(newItem.getMime())) {
                mimeIndicator.setImageDrawable(itemView.getResources().getDrawable(R.drawable.ic_audiotrack_black_24dp));
                mimeIndicator.setVisibility(View.VISIBLE);
            } else {
                mimeIndicator.setVisibility(View.GONE);
            }
        }

        @Override
        public void cacheViewFieldsAndConfigure(FolderItemViewAdapterPreferences adapterPrefs) {
            super.cacheViewFieldsAndConfigure(adapterPrefs);
            itemHeading = itemView.findViewById(delit.libs.R.id.list_item_heading);
            getIconViewLoader().withErrorDrawable(R.drawable.ic_file_gray_24dp);
        }
    }

    public abstract static class FolderItemViewHolder<VH extends FolderItemViewHolder<VH,LVA,T,MSL>, LVA extends FolderItemRecyclerViewAdapter<LVA,T,MSL,VH>, T extends FolderItem, MSL extends BaseRecyclerViewAdapter.MultiSelectStatusListener<MSL,LVA,FolderItemViewAdapterPreferences,T,VH>> extends BaseViewHolder<VH,FolderItemViewAdapterPreferences, T, LVA,MSL> implements PicassoLoader.PictureItemImageLoaderListener {

        private ImageView iconView;
        private ImageView typeIndicatorView;
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

        public ImageView getMimeTypeIndicatorView() {
            return typeIndicatorView;
        }

        public abstract void fillValues(T newItem, boolean allowItemDeletion);

        @Override
        public void cacheViewFieldsAndConfigure(FolderItemViewAdapterPreferences adapterPrefs) {

            super.cacheViewFieldsAndConfigure(adapterPrefs);
            typeIndicatorView = itemView.findViewById(delit.libs.R.id.type_indicator);
            iconView = itemView.findViewById(delit.libs.R.id.list_item_icon_thumbnail);
            iconView.setContentDescription("folder item thumb");
            iconView.setOnLongClickListener(v -> {
                iconViewLoader.cancelImageLoadIfRunning();
                iconViewLoader.loadNoCache();
                return true;
            });
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
            getIconView().setBackgroundColor(ContextCompat.getColor(getIconView().getContext(), R.color.color_scrim_heavy));
        }
    }

    private static class UpdateContentListAndSortContentAfterAdd extends OwnedSafeAsyncTask<FolderItemRecyclerViewAdapter, Void,Object,Object> {
        private static final String TAG = "UpdateContentListAndSort";

        public UpdateContentListAndSortContentAfterAdd(FolderItemRecyclerViewAdapter folderItemRecyclerViewAdapter) {
            super(folderItemRecyclerViewAdapter);
        }

        @Override
        protected void onPreExecuteSafely() {
            getOwner().isBusy = true;
        }

        @Override
        protected Object doInBackgroundSafely(Void... nothing) {
            getOwner().currentVisibleDocumentFileExts = getOwner().buildListOfFileExtsAndMimesInCurrentFolder(getOwner().currentDisplayContent);
            if(getOwner().currentDisplayContent != null) {
                Collections.sort(getOwner().currentDisplayContent, getOwner().getFolderItemComparator());
            }
            return null;
        }

        @Override
        protected void onPostExecuteSafely(Object o) {
            super.onPostExecuteSafely(o);
            getOwner().notifyDataSetChanged();
            // this will trigger rebuild of the file filters view and possibly post filter the files selected
            getOwner().isBusy = false;
            getOwner().navigationListener.onPostFolderOpened(null, null);
        }

        @Override
        protected void onCancelledSafely() {
            getOwner().isBusy = false;
            Logging.log(Log.WARN, TAG, "Async Task cancelled");
        }
    }

    private static class UpdateFolderContentTask<FIVA extends FolderItemRecyclerViewAdapter<FIVA,FolderItem,?,?>> extends OwnedSafeAsyncTask<FIVA, Void,Object,Pair<List<FolderItem>,List<FolderItem>>> {

        private static final String TAG = "UpdateFolderContentTask";
        private final DocumentFile newContent;
        private final boolean force;
        private boolean refreshingExistingFolder;
        private DocumentFile oldFolder;

        public UpdateFolderContentTask(FIVA folderItemRecyclerViewAdapter, DocumentFile newContent, boolean force) {
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
                refreshingExistingFolder = true;
            }

            oldFolder = getOwner().activeFolder;

            if (!refreshingExistingFolder) {
                getOwner().navigationListener.onPreFolderOpened(oldFolder, newContent != null ? newContent.listFiles() : null);
            }
        }

        @Override
        protected Pair<List<FolderItem>,List<FolderItem>> doInBackgroundSafely(Void... nothing) {
            Context context;
            try {
                context = getContext();
            } catch(NullPointerException e) {
                // the view has already been destroyed.
                return null;
            }
            List<FolderItem> fullContent = getOwner().currentFullContent;
            if(!refreshingExistingFolder || force) {
                fullContent = getOwner().getNewDisplayContentInternal(context, newContent);
            }
            List<FolderItem> filteredContent;
            if(fullContent != null) {
                filteredContent = getOwner().getFilteredListOfContent(fullContent);
                return new Pair<>(fullContent, filteredContent);
            }
            return null;
        }

        @Override
        protected void onPostExecuteSafely(Pair<List<FolderItem>,List<FolderItem>> result) {
            if(result != null) {
                // result only null if the task was cancelled.
                getOwner().currentFullContent = result.first;
                List<FolderItem> newFilteredContent = result.second;
                if(!CollectionUtils.equals(getOwner().currentDisplayContent, newFilteredContent)) {
                    getOwner().currentDisplayContent = newFilteredContent;
                    getOwner().notifyDataSetChanged();
                }
                getOwner().isBusy = false;
                if (getOwner().taskListener != null) {
                    getOwner().taskListener.onTaskFinished();
                }
                if (!refreshingExistingFolder) {
                    getOwner().navigationListener.onPostFolderOpened(oldFolder, newContent);
                }
            }
        }

        @Override
        protected void onCancelledSafely() {
            super.onCancelledSafely();
            getOwner().isBusy = false;
            if (getOwner().taskListener != null) {
                getOwner().taskListener.onTaskFinished();
            }
            Logging.log(Log.WARN, TAG, "Async Task cancelled");
        }
    }
}
