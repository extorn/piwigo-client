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

import com.crashlytics.android.Crashlytics;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.io.File;
import java.io.IOException;
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


public class LegacyFolderItemRecyclerViewAdapter extends BaseRecyclerViewAdapter<FolderItemViewAdapterPreferences, LegacyFolderItemRecyclerViewAdapter.LegacyFolderItem, LegacyFolderItemRecyclerViewAdapter.LegacyFolderItemViewHolder, BaseRecyclerViewAdapter.MultiSelectStatusListener<LegacyFolderItemRecyclerViewAdapter.LegacyFolderItem>> {

    public final static int VIEW_TYPE_FOLDER = 0;
    public final static int VIEW_TYPE_FILE = 1;
    public final static int VIEW_TYPE_FILE_IMAGE = 2;
    private static final String TAG = "LegacyFolderItemRVA";
    private final MediaScanner mediaScanner;
    private transient List<LegacyFolderItem> currentDisplayContent;
    private File activeFolder;
    private Comparator<? super LegacyFolderItem> fileComparator;
    private NavigationListener navigationListener;
    private SortedSet<String> currentVisibleFileExts;

    public LegacyFolderItemRecyclerViewAdapter(Context context, NavigationListener navigationListener, MediaScanner mediaScanner, MultiSelectStatusListener<LegacyFolderItem> multiSelectStatusListener, FolderItemViewAdapterPreferences folderViewPrefs) {
        super(multiSelectStatusListener, folderViewPrefs);
        this.navigationListener = navigationListener;
        this.mediaScanner = mediaScanner;
    }

    public void setInitiallySelectedItems() {
        SortedSet<Uri> initialSelectionItems = getAdapterPrefs().getInitialSelection();
        HashSet<Long> initialSelectionIds = null;
        if (initialSelectionItems != null) {
            initialSelectionIds = new HashSet<>(initialSelectionItems.size());
            for (Uri selectedItem : initialSelectionItems) {
                int pos = 0;
                try {
                    pos = getItemPositionForFile(IOUtils.getFile(selectedItem));
                } catch (IOException e) {
                    Crashlytics.log(Log.ERROR,TAG, "Non file Uri got in somehow : " + selectedItem);
                    throw new IllegalStateException("Non file Uri got in somehow");
                }
                if (pos >= 0) {
                    initialSelectionIds.add(getItemId(pos));
                }
            }
        }
        // update the visible selection.
        setInitiallySelectedItems(initialSelectionIds);
        setSelectedItems(initialSelectionIds);
    }

    protected void updateContent(Context context, File newContent, boolean force) {
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
        File[] folderContent = activeFolder.listFiles(new LegacyFileFilter(getAdapterPrefs().isShowFolderContent(), getAdapterPrefs().getVisibleFileTypes()));
        if(folderContent == null) {
            FirebaseAnalytics.getInstance(context).logEvent("no_folder_access", null);
        }

        currentDisplayContent = buildDisplayContent(folderContent);
        currentVisibleFileExts = getUniqueFileExtsInFolder(currentDisplayContent);
        Collections.sort(currentDisplayContent, getFileComparator());

        notifyDataSetChanged();
        mediaScanner.invokeScan(new MediaScanner.MediaScannerScanTask(activeFolder.getAbsolutePath(), getDisplayedFiles(), 15) {

            @Override
            public void onScanComplete(Map<File, Uri> batchResults, int firstResultIdx, int lastResultIdx, boolean jobFinished) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "processing icons from  " + firstResultIdx + " to " + lastResultIdx + "   " + System.currentTimeMillis());
                }
                for (Map.Entry<File, Uri> entry : batchResults.entrySet()) {
                    LegacyFolderItem item = getItemByFile(entry.getKey());
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

    public void rebuildContentView(Context context) {
        updateContent(context, activeFolder, true);
    }

    public void changeFolderViewed(Context context, File newContent) {
        updateContent(context, newContent, false);
    }

    private List<File> getDisplayedFiles() {
        ArrayList<File> files = new ArrayList<>(currentDisplayContent.size());
        for (LegacyFolderItem item : currentDisplayContent) {
            files.add(item.getFile());
        }
        return files;
    }

    private LegacyFolderItem getItemByFile(File key) {
        for (LegacyFolderItem item : currentDisplayContent) {
            if (item.getFile().equals(key)) {
                return item;
            }
        }
        return null;
    }

    private List<LegacyFolderItem> buildDisplayContent(File[] folderContent) {
        if (folderContent == null) {
            return new ArrayList<>();
        }
        ArrayList<LegacyFolderItem> displayContent = new ArrayList<>();
        for (File f : folderContent) {
            displayContent.add(new LegacyFolderItem(f));
        }
        return displayContent;
    }

    private SortedSet<String> getUniqueFileExtsInFolder(List<LegacyFolderItem> currentDisplayContent) {
        SortedSet<String> currentVisibleFileExts = new TreeSet<>();
        for (LegacyFolderItem f : currentDisplayContent) {
            if (f.getFile().isDirectory()) {
                continue;
            }
            currentVisibleFileExts.add(IOUtils.getFileExt(f.getFile().getName()).toLowerCase());
        }
        return currentVisibleFileExts;
    }

    @Override
    protected CustomClickListener<FolderItemViewAdapterPreferences, LegacyFolderItem, LegacyFolderItemViewHolder> buildCustomClickListener(LegacyFolderItemViewHolder viewHolder) {
        return new LegacyFolderItemCustomClickListener(viewHolder, this);
    }

    public File getActiveFolder() {
        return activeFolder;
    }

    public Comparator<? super LegacyFolderItem> getFileComparator() {
        if (fileComparator == null) {
            fileComparator = buildFileComparator();
        }
        return fileComparator;
    }

    protected Comparator<? super LegacyFolderItem> buildFileComparator() {
        return new Comparator<LegacyFolderItem>() {

            @Override
            public int compare(LegacyFolderItem o1, LegacyFolderItem o2) {
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
        LegacyFolderItem f = getItemByPosition(position);
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
    protected LegacyFolderItem getItemById(Long selectedId) {
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
    public LegacyFolderItemViewHolder buildViewHolder(View view, int viewType) {
        if (viewType == VIEW_TYPE_FOLDER) {
            return new LegacyFolderItemFolderViewHolder(view);
        } else {
            return new LegacyFolderItemFileViewHolder(view);
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

    public int getItemPositionForFilename(String filename) {
        for (int i = 0; i < currentDisplayContent.size(); i++) {
            if (currentDisplayContent.get(i).getFile().getName().equals(filename)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getItemPosition(LegacyFolderItem item) {
        if (currentDisplayContent == null) {
            throw new IllegalStateException("Please set the initial folder and initialise the list before attempting to access the items in the list");
        }
        return currentDisplayContent.indexOf(item);
    }

    @Override
    protected void removeItemFromInternalStore(int idxRemoved) {
        LegacyFolderItem f = currentDisplayContent.get(idxRemoved);
        if (f.getFile().exists()) {
            f.getFile().delete();
        }
        currentDisplayContent.remove(idxRemoved);
    }

    @Override
    protected void replaceItemInInternalStore(int idxToReplace, LegacyFolderItem newItem) {
        throw new UnsupportedOperationException("This makes no sense for a file structure traversal");
    }

    @Override
    protected LegacyFolderItem getItemFromInternalStoreMatching(LegacyFolderItem item) {
        // they'll always be the same
        return item;
    }

    @Override
    protected void addItemToInternalStore(LegacyFolderItem item) {
        if (!item.getFile().exists()) {
            throw new IllegalStateException("Cannot add File to display that does not yet exist");
        }
        if (!item.getFile().getParentFile().equals(activeFolder)) {
            throw new IllegalArgumentException("File is not a child of the currently displayed folder");
        }
        currentDisplayContent.add(item);
    }

    @Override
    public LegacyFolderItem getItemByPosition(int position) {
        return currentDisplayContent.get(position);
    }

    @Override
    public boolean isHolderOutOfSync(LegacyFolderItemViewHolder holder, LegacyFolderItem newItem) {
        return isDirtyItemViewHolder(holder, newItem);
    }

    public void cancelAnyActiveFolderMediaScan(Context context) {
        MediaScanner.instance(context).cancelActiveScan(getActiveFolder().getAbsolutePath());
    }

    public static class LegacyFolderItem implements Parcelable {
        public static final Parcelable.Creator<LegacyFolderItem> CREATOR
                = new Parcelable.Creator<LegacyFolderItem>() {
            public LegacyFolderItem createFromParcel(Parcel in) {
                return new LegacyFolderItem(in);
            }

            public LegacyFolderItem[] newArray(int size) {
                return new LegacyFolderItem[size];
            }
        };
        private File file;
        private Uri contentUri;

        public LegacyFolderItem(File file) {
            this.file = file;
        }

        public LegacyFolderItem(Parcel in) {
            file = ParcelUtils.readFile(in);
            contentUri = ParcelUtils.readParcelable(in, Uri.class);
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
            ParcelUtils.writeParcelable(dest, contentUri);
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

    class LegacyFolderItemCustomClickListener extends CustomClickListener<FolderItemViewAdapterPreferences, LegacyFolderItem, LegacyFolderItemViewHolder> {
        public LegacyFolderItemCustomClickListener(LegacyFolderItemViewHolder viewHolder, LegacyFolderItemRecyclerViewAdapter parentAdapter) {
            super(viewHolder, parentAdapter);
        }

        @Override
        public void onClick(View v) {
            if (getViewHolder().getItemViewType() == VIEW_TYPE_FOLDER) {
                changeFolderViewed(v.getContext(), getViewHolder().getItem().getFile());
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

    protected class LegacyFolderItemFolderViewHolder extends LegacyFolderItemViewHolder {

        public LegacyFolderItemFolderViewHolder(View view) {
            super(view);
        }

        @Override
        public void fillValues(LegacyFolderItem newItem, boolean allowItemDeletion) {
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
            getIconView().setColorFilter(ContextCompat.getColor(itemView.getContext(),R.color.app_secondary), PorterDuff.Mode.SRC_IN);
            getIconViewLoader().setResourceToLoad(R.drawable.ic_folder_black_24dp);
            getIconViewLoader().load();
        }
    }

    protected class LegacyFolderItemFileViewHolder extends LegacyFolderItemViewHolder {

        private TextView itemHeading;

        public LegacyFolderItemFileViewHolder(View view) {
            super(view);
        }

        @Override
        public void fillValues(LegacyFolderItem newItem, boolean allowItemDeletion) {
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

    protected abstract class LegacyFolderItemViewHolder extends BaseViewHolder<FolderItemViewAdapterPreferences, LegacyFolderItem> implements PicassoLoader.PictureItemImageLoaderListener {

        private ImageView iconView;
        private ResizingPicassoLoader<ImageView> iconViewLoader;

        public LegacyFolderItemViewHolder(View view) {
            super(view);
        }

        public ImageView getIconView() {
            return iconView;
        }

        public ResizingPicassoLoader getIconViewLoader() {
            return iconViewLoader;
        }

        public abstract void fillValues(LegacyFolderItem newItem, boolean allowItemDeletion);

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
