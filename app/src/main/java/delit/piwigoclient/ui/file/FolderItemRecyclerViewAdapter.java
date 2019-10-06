package delit.piwigoclient.ui.file;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import delit.libs.ui.util.MediaScanner;
import delit.libs.ui.util.ParcelUtils;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapter;
import delit.libs.ui.view.recycler.BaseViewHolder;
import delit.libs.ui.view.recycler.CustomClickListener;
import delit.libs.util.IOUtils;
import delit.libs.util.ObjectUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.PicassoLoader;
import delit.piwigoclient.business.ResizingPicassoLoader;


public class FolderItemRecyclerViewAdapter extends BaseRecyclerViewAdapter<FolderItemViewAdapterPreferences, FolderItemRecyclerViewAdapter.FolderItem, FolderItemRecyclerViewAdapter.FolderItemViewHolder, BaseRecyclerViewAdapter.MultiSelectStatusListener<FolderItemRecyclerViewAdapter.FolderItem>> {

    public final static int VIEW_TYPE_FOLDER = 0;
    public final static int VIEW_TYPE_FILE = 1;
    public final static int VIEW_TYPE_FILE_IMAGE = 2;
    private final MediaScanner mediaScanner;
    private transient List<FolderItem> currentDisplayContent;
    private File activeFolder;
    private Comparator<? super FolderItem> fileComparator;
    private NavigationListener navigationListener;
    private SortedSet<String> currentVisibleFileExts;

    public FolderItemRecyclerViewAdapter(NavigationListener navigationListener, MediaScanner mediaScanner, MultiSelectStatusListener<FolderItem> multiSelectStatusListener, FolderItemViewAdapterPreferences folderViewPrefs) {
        super(multiSelectStatusListener, folderViewPrefs);
        this.navigationListener = navigationListener;
        this.mediaScanner = mediaScanner;
    }

    public void setInitiallySelectedItems() {
        SortedSet<String> initialSelectionItems = getAdapterPrefs().getInitialSelection();
        HashSet<Long> initialSelectionIds = null;
        if (initialSelectionItems != null) {
            initialSelectionIds = new HashSet<>(initialSelectionItems.size());
            for (String selectedItem : initialSelectionItems) {
                int pos = getItemPositionForFile(new File(selectedItem));
                if (pos >= 0) {
                    initialSelectionIds.add(getItemId(pos));
                }
            }
        }
        // update the visible selection.
        setInitiallySelectedItems(initialSelectionIds);
        setSelectedItems(initialSelectionIds);
    }

    protected void updateContent(File newContent, boolean force) {
        boolean refreshingExistingFolder = false;
        if (ObjectUtils.areEqual(activeFolder, newContent)) {
            if (!force && currentDisplayContent != null) {
                return;
            } else {
                refreshingExistingFolder = true;
            }
        }

        File oldFolder = activeFolder;

        if (!refreshingExistingFolder) {
            navigationListener.onPreFolderOpened(oldFolder, newContent);
        }

        activeFolder = newContent;
        getSelectedItemIds().clear(); // need to clear selection since position in list is used as unique item id
        File[] folderContent = activeFolder.listFiles(getAdapterPrefs().getFileFilter());
        currentDisplayContent = buildDisplayContent(folderContent);
        currentVisibleFileExts = getUniqueFileExtsInFolder(currentDisplayContent);
        Collections.sort(currentDisplayContent, getFileComparator());

        notifyDataSetChanged();
        mediaScanner.invokeScan(new MediaScanner.MediaScannerScanTask(getDisplayedFiles(), 15) {

            @Override
            public void onScanComplete(Map<File, Uri> batchResults, int firstResultIdx, int lastResultIdx, boolean jobFinished) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "processing icons from  " + firstResultIdx + " to " + lastResultIdx + "   " + System.currentTimeMillis());
                }
                for (Map.Entry<File, Uri> entry : batchResults.entrySet()) {
                    FolderItem item = getItemByFile(entry.getKey());
                    if (item != null) {
                        item.setContentUri(entry.getValue());
                    }
                }
                notifyItemRangeChanged(firstResultIdx, batchResults.size());
            }
        });

        if (!refreshingExistingFolder) {
            navigationListener.onPostFolderOpened(oldFolder, newContent);
        }
    }

    public void rebuildContentView() {
        updateContent(activeFolder, true);
    }

    public void changeFolderViewed(File newContent) {
        updateContent(newContent, false);
    }

    private List<File> getDisplayedFiles() {
        ArrayList<File> files = new ArrayList<>(currentDisplayContent.size());
        for (FolderItem item : currentDisplayContent) {
            files.add(item.getFile());
        }
        return files;
    }

    private FolderItem getItemByFile(File key) {
        for (FolderItem item : currentDisplayContent) {
            if (item.getFile().equals(key)) {
                return item;
            }
        }
        return null;
    }

    private List<FolderItem> buildDisplayContent(File[] folderContent) {
        if (folderContent == null) {
            return new ArrayList<>();
        }
        ArrayList<FolderItem> displayContent = new ArrayList<>();
        for (File f : folderContent) {
            displayContent.add(new FolderItem(f));
        }
        return displayContent;
    }

    private SortedSet<String> getUniqueFileExtsInFolder(List<FolderItem> currentDisplayContent) {
        SortedSet<String> currentVisibleFileExts = new TreeSet<>();
        for (FolderItem f : currentDisplayContent) {
            if (f.getFile().isDirectory()) {
                continue;
            }
            currentVisibleFileExts.add(IOUtils.getFileExt(f.getFile().getName()).toLowerCase());
        }
        return currentVisibleFileExts;
    }

    @Override
    protected CustomClickListener<FolderItemViewAdapterPreferences, FolderItem, FolderItemViewHolder> buildCustomClickListener(FolderItemViewHolder viewHolder) {
        return new FolderItemCustomClickListener(viewHolder, this);
    }

    public File getActiveFolder() {
        return activeFolder;
    }

    public void setActiveFolder(File activeFolder) {
        this.activeFolder = activeFolder;
    }

    public Comparator<? super FolderItem> getFileComparator() {
        if (fileComparator == null) {
            fileComparator = buildFileComparator();
        }
        return fileComparator;
    }

    protected Comparator<? super FolderItem> buildFileComparator() {
        return new Comparator<FolderItem>() {

            @Override
            public int compare(FolderItem o1, FolderItem o2) {
                File file1 = o1.getFile();
                File file2 = o2.getFile();
                if (file1.isDirectory() && !file2.isDirectory()) {
                    return -1;
                }
                if (!file1.isDirectory() && file2.isDirectory()) {
                    return 1;
                }
                switch (getAdapterPrefs().getFileSortOrder()) {
                    case FolderItemViewAdapterPreferences.ALPHABETICAL:
                        return file1.getName().compareTo(file2.getName());
                    case FolderItemViewAdapterPreferences.LAST_MODIFIED_DATE:
                        if (file1.lastModified() == file2.lastModified()) {
                            return file1.getName().compareTo(file2.getName());
                        } else {
                            // this is reversed order
                            return file1.lastModified() > file2.lastModified() ? -1 : 1;
                        }
                    default:
                        return 0;
                }
            }
        };
    }

    @Override
    public int getItemViewType(int position) {
        FolderItem f = getItemByPosition(position);
        if (f.getFile().isDirectory()) {
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
            return new FolderItemFileViewHolder(view);
        }
        //TODO allow blank "folder" spacer items to correct the visual display.
    }

    private int getItemPositionForFile(File f) {
        for (int i = 0; i < currentDisplayContent.size(); i++) {
            if (currentDisplayContent.get(i).getFile().equals(f)) {
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
        if (f.getFile().exists()) {
            f.getFile().delete();
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
        if (!item.getFile().exists()) {
            throw new IllegalStateException("Cannot add File to display that does not yet exist");
        }
        if (!item.getFile().getParentFile().equals(activeFolder)) {
            throw new IllegalArgumentException("File is not a child of the currently displayed folder");
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
        private File file;
        private Uri contentUri;

        public FolderItem(File file) {
            this.file = file;
        }

        public FolderItem(Parcel in) {
            file = ParcelUtils.readFile(in);
            contentUri = ParcelUtils.readUri(in);
        }

        public Uri getContentUri() {
            return contentUri;
        }

        public void setContentUri(Uri contentUri) {
            this.contentUri = contentUri;
        }

        public File getFile() {
            return file;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            ParcelUtils.writeFile(dest, file);
            ParcelUtils.writeUri(dest, contentUri);
        }

        @Override
        public int describeContents() {
            return 0;
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
        return currentVisibleFileExts;
    }

    public interface NavigationListener {
        void onPreFolderOpened(File oldFolder, File newFolder);

        void onPostFolderOpened(File oldFolder, File newFolder);
    }

    class FolderItemCustomClickListener extends CustomClickListener<FolderItemViewAdapterPreferences, FolderItem, FolderItemViewHolder> {
        public FolderItemCustomClickListener(FolderItemViewHolder viewHolder, FolderItemRecyclerViewAdapter parentAdapter) {
            super(viewHolder, parentAdapter);
        }

        @Override
        public void onClick(View v) {
            if (getViewHolder().getItemViewType() == VIEW_TYPE_FOLDER) {
                changeFolderViewed(getViewHolder().getItem().getFile());
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
            getTxtTitle().setText(newItem.getFile().getName());
            if (!allowItemDeletion) {
                getDeleteButton().setVisibility(View.GONE);
            }
            getCheckBox().setVisibility(getAdapterPrefs().isAllowFolderSelection() ? View.VISIBLE : View.GONE);
            getCheckBox().setChecked(getSelectedItems().contains(newItem.getFile()));
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

    protected class FolderItemFileViewHolder extends FolderItemViewHolder {

        private TextView itemHeading;

        public FolderItemFileViewHolder(View view) {
            super(view);
        }

        @Override
        public void fillValues(Context context, FolderItem newItem, boolean allowItemDeletion) {
            setItem(newItem);

            long bytes = newItem.getFile().length();
            double sizeMb = ((double)bytes)/1024/1024;
            itemHeading.setVisibility(View.VISIBLE);
            itemHeading.setText(String.format("%1$.2fMB", sizeMb));

            if (getAdapterPrefs().isShowFilenames()) {
                getTxtTitle().setVisibility(View.VISIBLE);
                getTxtTitle().setText(newItem.getFile().getName());
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
                getIconViewLoader().setFileToLoad(newItem.getFile());
            }/*TODO why was this else statement needed?
             else {
                getIconViewLoader().setResourceToLoad(R.drawable.ic_file_gray_24dp);
            }*/
        }

        @Override
        public void cacheViewFieldsAndConfigure(FolderItemViewAdapterPreferences adapterPrefs) {
            super.cacheViewFieldsAndConfigure(adapterPrefs);
            itemHeading = itemView.findViewById(R.id.list_item_heading);
            getIconViewLoader().withErrorDrawable(R.drawable.ic_file_gray_24dp);
            final ViewTreeObserver.OnPreDrawListener predrawListener = new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    if (!getIconViewLoader().isImageLoaded() && !getIconViewLoader().isImageLoading() && !getIconViewLoader().isImageUnavailable()) {

                        int imgSize = getIconView().getMeasuredWidth();
                        getIconViewLoader().setResizeTo(imgSize, imgSize);
                        getIconViewLoader().load();
                    }
                    return true;
                }
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

    protected abstract class FolderItemViewHolder extends BaseViewHolder<FolderItemViewAdapterPreferences, FolderItem> implements PicassoLoader.PictureItemImageLoaderListener {

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
