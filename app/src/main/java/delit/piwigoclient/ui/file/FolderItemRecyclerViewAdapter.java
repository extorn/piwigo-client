package delit.piwigoclient.ui.file;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.MimeTypeFilter;
import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import delit.libs.ui.util.ParcelUtils;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapter;
import delit.libs.ui.view.recycler.BaseViewHolder;
import delit.libs.ui.view.recycler.CustomClickListener;
import delit.libs.util.CollectionUtils;
import delit.libs.util.IOUtils;
import delit.libs.util.ObjectUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.PicassoLoader;
import delit.piwigoclient.business.ResizingPicassoLoader;


public class FolderItemRecyclerViewAdapter extends BaseRecyclerViewAdapter<FolderItemViewAdapterPreferences, FolderItemRecyclerViewAdapter.FolderItem, FolderItemRecyclerViewAdapter.FolderItemViewHolder, BaseRecyclerViewAdapter.MultiSelectStatusListener<FolderItemRecyclerViewAdapter.FolderItem>> {

    public final static int VIEW_TYPE_FOLDER = 0;
    public final static int VIEW_TYPE_FILE = 1;
    public final static int VIEW_TYPE_FILE_IMAGE = 2;
    private transient List<FolderItem> currentDisplayContent;
    private DocumentFile activeFolder;
    private Comparator<? super FolderItem> fileComparator;
    private NavigationListener navigationListener;
    private SortedSet<String> currentVisibleDocumentFileExts;
    private Uri activeRootUri;

    public FolderItemRecyclerViewAdapter(Context context, NavigationListener navigationListener, MultiSelectStatusListener<FolderItem> multiSelectStatusListener, FolderItemViewAdapterPreferences folderViewPrefs) {
        super(context, multiSelectStatusListener, folderViewPrefs);
        this.navigationListener = navigationListener;
    }

    public void setInitiallySelectedItems() {
        SortedSet<Uri> initialSelectionItems = getAdapterPrefs().getInitialSelection();
        HashSet<Long> initialSelectionIds = null;
        if (initialSelectionItems != null) {
            initialSelectionIds = new HashSet<>(initialSelectionItems.size());
            for (Uri selectedItem : initialSelectionItems) {
                if(!"file".equals(selectedItem.getScheme())) {
                    int pos = getItemPositionForDocumentFile(DocumentFile.fromTreeUri(getContext(), selectedItem));
                    if (pos >= 0) {
                        initialSelectionIds.add(getItemId(pos));
                    }
                }
            }
        }
        // update the visible selection.
        setInitiallySelectedItems(initialSelectionIds);
        setSelectedItems(initialSelectionIds);
    }

    protected void updateContent(DocumentFile newContent, boolean force) {
        if(activeRootUri == null) {
            activeRootUri = newContent != null ? IOUtils.getTreeUri(newContent.getUri()) : null;
        }
        boolean refreshingExistingFolder = false;
        Uri activeUri = activeFolder != null ? activeFolder.getUri() : null;
        Uri newUri = newContent != null ? newContent.getUri() : null;
        if (ObjectUtils.areEqual(activeUri, newUri)) {
            if (!force && currentDisplayContent != null && activeFolder != null) {
                return;
            } else {
                refreshingExistingFolder = true;
            }
        }

        DocumentFile oldFolder = activeFolder;

        if (!refreshingExistingFolder) {
            navigationListener.onPreFolderOpened(oldFolder, newContent != null ? newContent.listFiles() : null);
        }

        activeFolder = newContent;
        getSelectedItemIds().clear(); // need to clear selection since position in list is used as unique item id
        // load all the children.

        if(activeFolder !=  null) {
            List<DocumentFile> folderContent;
            folderContent = new ArrayList<>(activeFolder.listFiles().length);
            Collections.addAll(folderContent, activeFolder.listFiles());
            currentDisplayContent = buildDisplayContent(folderContent);
        } else {
            if(currentDisplayContent == null) {
                currentDisplayContent = new ArrayList<>();
            }
            // null used to mean no folder to show, but now means items without a folder shown so we leave the content intact.
        }

        FolderItemFilter filter = new FolderItemFilter(getAdapterPrefs().isAllowFileSelection(), getAdapterPrefs().getVisibleFileTypes(), CollectionUtils.asStringArray(getAdapterPrefs().getVisibleMimeTypes()));
        for(int i = currentDisplayContent.size() - 1; i >= 0; i--) {
            FolderItem folderItem = currentDisplayContent.get(i);
            if(!filter.accept(folderItem)) {
                currentDisplayContent.remove(i);
            }
        }

        currentVisibleDocumentFileExts = getUniqueDocumentFileExtsInFolder(currentDisplayContent);
        Collections.sort(currentDisplayContent, getFolderItemComparator());

        notifyDataSetChanged();

        if (!refreshingExistingFolder) {
            navigationListener.onPostFolderOpened(oldFolder, newContent);
        }
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
            return !showFolderContents || (pathname.isFolder() || filenameMatches(pathname)/*mimeTypeMatches(pathname)*/);
        }

        private boolean filenameMatches(FolderItem item) {
            if (visibleFileTypes == null) {
                return true;
            }
            return visibleFileTypes.contains(item.getExt());
        }

        private boolean mimeTypeMatches(DocumentFile pathname) {
            if (visibleMimeTypes == null) {
                return true;
            }
            String mimeType = pathname.getType();
            return null != MimeTypeFilter.matches(mimeType, visibleMimeTypes);
        }
    }

    public void rebuildContentView() {
        updateContent(activeFolder, true);
    }

    public void resetRoot(DocumentFile activeRootFile) {
        if(activeRootFile == null) {
            this.activeRootUri = null;
        } else {
            this.activeRootUri = IOUtils.getTreeUri(activeRootFile.getUri());
        }
        clear();
        changeFolderViewed(activeRootFile);
    }

    public void changeFolderViewed(DocumentFile newContent) {
        updateContent(newContent, false);
    }

    private List<FolderItem> buildDisplayContent(@NonNull List<DocumentFile> folderContent) {
        ArrayList<FolderItem> displayContent = new ArrayList<>();
        for (DocumentFile f : folderContent) {
            displayContent.add(new FolderItem(activeRootUri, f));
        }
        return displayContent;
    }

    private SortedSet<String> getUniqueDocumentFileExtsInFolder(List<FolderItem> currentDisplayContent) {
        SortedSet<String> currentVisibleDocumentFileExts = new TreeSet<>();
        for (FolderItem f : currentDisplayContent) {
            if (f.isFolder()) {
                continue;
            }
            currentVisibleDocumentFileExts.add(f.getExt());
        }
        return currentVisibleDocumentFileExts;
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
                return -1;
            }
            if (!o1.isFolder() && o2.isFolder()) {
                return 1;
            }
            if (o1.isFolder() && o2.isFolder()) {
                return ObjectUtils.compare(o1.getName(), o2.getName());
            }
            switch (getAdapterPrefs().getFileSortOrder()) {
                case FolderItemViewAdapterPreferences.ALPHABETICAL:
                    return ObjectUtils.compare(o1.getName(), o2.getName());
                case FolderItemViewAdapterPreferences.LAST_MODIFIED_DATE:
                    if (o1.lastModified() == o2.lastModified()) {
                        return ObjectUtils.compare(o1.getName(), o2.getName());
                    } else {
                        // this is reversed order
                        return o1.lastModified() > o2.lastModified() ? -1 : 1;
                    }
                default:
                    return 0;
            }
        };
    }

    @Override
    public int getItemViewType(int position) {
        FolderItem f = getItemByPosition(position);
        f.cacheDocFileFields(getContext());
        if (f.isFolder()) {
            return VIEW_TYPE_FOLDER;
        }
        return VIEW_TYPE_FILE;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    protected FolderItem getItemById(Long selectedId) {
        return getItemByPosition(selectedId.intValue());
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
        for (int i = 0; i < currentDisplayContent.size(); i++) {
            if (currentDisplayContent.get(i).getDocumentFile(getContext()).getUri().equals(f.getUri())) {
                return i;
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
            currentVisibleDocumentFileExts = getUniqueDocumentFileExtsInFolder(currentDisplayContent);
            Collections.sort(currentDisplayContent, getFolderItemComparator());

            notifyDataSetChanged();
            // this will trigger rebuild of the file filters view and possibly post filter the files selected
            navigationListener.onPostFolderOpened(null, null);
        }
    }

    private void addFolderItemToInternalStore(FolderItem item) {
        if (!item.getDocumentFile(getContext()).exists()) {
            throw new IllegalStateException("Cannot add DocumentFile to display that does not yet exist");
        }
        DocumentFile parentFile = item.getDocumentFile(getContext()).getParentFile();
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
        public static final Parcelable.Creator<FolderItem> CREATOR
                = new Parcelable.Creator<FolderItem>() {
            public FolderItem createFromParcel(Parcel in) {
                return new FolderItem(in);
            }

            public FolderItem[] newArray(int size) {
                return new FolderItem[size];
            }
        };


        private Uri rootUri;
        private Uri itemUri;
        private DocumentFile itemDocFile;
        private boolean isFolder;
        private long lastModified;
        private String name;
        private String ext;
        private String mime;

        public FolderItem(Uri itemUri) {
            this.itemUri = itemUri;
        }

        public boolean isFolder() {
            return isFolder;
        }

        public FolderItem(Uri rootUri, DocumentFile itemDocFile) {
            this.itemDocFile = itemDocFile;
            this.rootUri = rootUri;
            this.itemUri = itemDocFile.getUri(); // used for persistence
            this.isFolder = itemDocFile.isDirectory();
            this.lastModified = itemDocFile.lastModified();
            this.name = IOUtils.getFilename(itemDocFile);
            this.mime = itemDocFile.getType();
            this.ext = IOUtils.getFileExt(this.name, mime);
        }

        public FolderItem(Parcel in) {
            rootUri = ParcelUtils.readParcelable(in, Uri.class);
            itemUri = ParcelUtils.readParcelable(in, Uri.class);
        }

        public String getMime() {
            return mime;
        }

        public String getExt() {
            return ext;
        }

        public String getName() {
            return name;
        }

        public Uri getContentUri() {
            return itemUri;
        }

        public @NonNull DocumentFile getDocumentFile(Context context) {
            return cacheDocFileFields(context);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            ParcelUtils.writeParcelable(dest, rootUri);
            ParcelUtils.writeParcelable(dest, itemUri);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public long lastModified() {
            return lastModified;
        }

        public DocumentFile cacheDocFileFields(Context context) {
            if(itemDocFile == null) {
                if(rootUri != null) {
                    // will be the case after loaded from parcel
                    itemDocFile = IOUtils.getTreeLinkedDocFile(context, rootUri, itemUri);
                } else {
                    itemDocFile = DocumentFile.fromSingleUri(context, itemUri); // this will occur if the file was shared with us by external app
                }
                if(itemDocFile != null) {
                    this.isFolder = itemDocFile.isDirectory();
                    this.lastModified = itemDocFile.lastModified();
                    this.name = IOUtils.getFilename(itemDocFile);
                    this.mime = itemDocFile.getType();
                    this.ext = IOUtils.getFileExt(this.name, mime);
                }
            }
            return itemDocFile;
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
            if (getViewHolder().getItemViewType() == VIEW_TYPE_FOLDER) {
                FolderItem folderItem = getViewHolder().getItem();

                changeFolderViewed(folderItem.getDocumentFile(getContext()));
            } else if (getAdapterPrefs().isAllowFileSelection()) {
                super.onClick(v);
            }
        }

        @Override
        public boolean onLongClick(View v) {
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
        public void fillValues(Context context, FolderItem newItem, boolean allowItemDeletion) {
            setItem(newItem);
            getTxtTitle().setVisibility(View.VISIBLE);
            getTxtTitle().setText(IOUtils.getFilename(newItem.getDocumentFile(context)));
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
            getIconView().setColorFilter(ContextCompat.getColor(getContext(),R.color.accent), PorterDuff.Mode.SRC_IN);
            getIconViewLoader().setResourceToLoad(R.drawable.ic_folder_black_24dp);
            getIconViewLoader().load();
        }
    }

    protected class FolderItemDocumentFileViewHolder extends FolderItemViewHolder {

        private TextView itemHeading;

        public FolderItemDocumentFileViewHolder(View view) {
            super(view);
        }

        @Override
        public void fillValues(Context context, FolderItem newItem, boolean allowItemDeletion) {
            setItem(newItem);

            long bytes = newItem.getDocumentFile(getContext()).length();
            double sizeMb = ((double)bytes)/1024/1024;
            itemHeading.setVisibility(View.VISIBLE);
            itemHeading.setText(String.format(context.getString(R.string.File_size_pattern), sizeMb));

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
            getCheckBox().setChecked(getSelectedItems().contains(newItem));
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

        public abstract void fillValues(Context context, FolderItem newItem, boolean allowItemDeletion);

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
}
