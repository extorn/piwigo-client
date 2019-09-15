package delit.piwigoclient.ui.file;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.util.ArrayUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import delit.libs.ui.util.MediaScanner;
import delit.libs.ui.view.button.AppCompatCheckboxTriState;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapter;
import delit.libs.ui.view.recycler.CustomClickListener;
import delit.libs.ui.view.recycler.CustomViewHolder;
import delit.libs.util.IOUtils;
import delit.libs.util.ObjectUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.PicassoLoader;
import delit.piwigoclient.business.ResizingPicassoLoader;


public class FolderItemRecyclerViewAdapter extends BaseRecyclerViewAdapter<FolderItemViewAdapterPreferences, File, FolderItemRecyclerViewAdapter.FolderItemViewHolder, BaseRecyclerViewAdapter.MultiSelectStatusListener<File>> {

    public final static int VIEW_TYPE_FOLDER = 0;
    public final static int VIEW_TYPE_FILE = 1;
    public final static int VIEW_TYPE_FILE_IMAGE = 2;
    private final MediaScanner mediaScanner;
    private transient List<File> currentDisplayContent;
    private File activeFolder;
    private Comparator<? super File> fileComparator;
    private NavigationListener navigationListener;
    private SortedSet<String> currentVisibleFileExts;
    private HashMap<File, Uri> currentDisplayContentUris = new HashMap<>();

    public FolderItemRecyclerViewAdapter(NavigationListener navigationListener, MediaScanner mediaScanner, MultiSelectStatusListener<File> multiSelectStatusListener, FolderItemViewAdapterPreferences folderViewPrefs) {
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
                int pos = getItemPosition(new File(selectedItem));
                if (pos >= 0) {
                    initialSelectionIds.add(getItemId(pos));
                }
            }
        }
        // update the visible selection.
        setInitiallySelectedItems(initialSelectionIds);
        setSelectedItems(initialSelectionIds);
    }

    public void rebuildContentView() {
        updateContent(activeFolder, true);
    }

    public void changeFolderViewed(File newContent) {
        updateContent(newContent, false);
    }

    protected void updateContent(File newContent, boolean force) {
        boolean refreshingExistingFolder = false;
        if (ObjectUtils.areEqual(activeFolder, newContent)) {
            if (!force) {
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
        currentDisplayContent = folderContent != null ? ArrayUtils.toArrayList(folderContent) : new ArrayList<File>(0);
        currentVisibleFileExts = getUniqueFileExtsInFolder(currentDisplayContent);
        Collections.sort(currentDisplayContent, getFileComparator());
        currentDisplayContentUris.clear();

        notifyDataSetChanged();
        mediaScanner.invokeScan(new MediaScanner.MediaScannerScanTask(currentDisplayContent, 15) {

            @Override
            public void onScanComplete(Map<File, Uri> batchResults, int firstResultIdx, int lastResultIdx, boolean jobFinished) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "processing icons from  " + firstResultIdx + " to " + lastResultIdx + "   " + System.currentTimeMillis());
                }
                currentDisplayContentUris.putAll(batchResults);
                notifyItemRangeChanged(firstResultIdx, batchResults.size());
            }
        });

        if (!refreshingExistingFolder) {
            navigationListener.onPostFolderOpened(oldFolder, newContent);
        }
    }

    private SortedSet<String> getUniqueFileExtsInFolder(List<File> currentDisplayContent) {
        SortedSet<String> currentVisibleFileExts = new TreeSet<>();
        for (File f : currentDisplayContent) {
            if (f.isDirectory()) {
                continue;
            }
            currentVisibleFileExts.add(IOUtils.getFileExt(f.getName()).toLowerCase());
        }
        return currentVisibleFileExts;
    }

    public File getActiveFolder() {
        return activeFolder;
    }

    public void setActiveFolder(File activeFolder) {
        this.activeFolder = activeFolder;
    }

    @Override
    protected CustomClickListener<FolderItemViewAdapterPreferences, File, FolderItemViewHolder> buildCustomClickListener(FolderItemViewHolder viewHolder) {
        return new FolderItemCustomClickListener(viewHolder, this);
    }

    public Comparator<? super File> getFileComparator() {
        if (fileComparator == null) {
            fileComparator = buildFileComparator();
        }
        return fileComparator;
    }

    protected Comparator<? super File> buildFileComparator() {
        return new Comparator<File>() {

            @Override
            public int compare(File o1, File o2) {
                if (o1.isDirectory() && !o2.isDirectory()) {
                    return -1;
                }
                if (!o1.isDirectory() && o2.isDirectory()) {
                    return 1;
                }
                switch (getAdapterPrefs().getFileSortOrder()) {
                    case FolderItemViewAdapterPreferences.ALPHABETICAL:
                        return o1.getName().compareTo(o2.getName());
                    case FolderItemViewAdapterPreferences.LAST_MODIFIED_DATE:
                        if (o1.lastModified() == o2.lastModified()) {
                            return o1.getName().compareTo(o2.getName());
                        } else {
                            // this is reversed order
                            return o1.lastModified() > o2.lastModified() ? -1 : 1;
                        }
                    default:
                        return 0;
                }
            }
        };
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        File f = getItemByPosition(position);
        if (f.isDirectory()) {
            return VIEW_TYPE_FOLDER;
        }
        return VIEW_TYPE_FILE;
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

    @Override
    protected File getItemById(Long selectedId) {
        return getItemByPosition(selectedId.intValue());
    }

    @Override
    public int getItemPosition(File item) {
        if (currentDisplayContent == null) {
            throw new IllegalStateException("Please set the initial folder and initialise the list before attempting to access the items in the list");
        }
        return currentDisplayContent.indexOf(item);
    }

    @Override
    protected void removeItemFromInternalStore(int idxRemoved) {
        File f = currentDisplayContent.get(idxRemoved);
        if (f.exists()) {
            f.delete();
        }
        currentDisplayContent.remove(idxRemoved);
    }

    @Override
    protected void replaceItemInInternalStore(int idxToReplace, File newItem) {
        throw new UnsupportedOperationException("This makes no sense for a file structure traversal");
    }

    @Override
    protected File getItemFromInternalStoreMatching(File item) {
        // they'll always be the same
        return item;
    }

    @Override
    protected void addItemToInternalStore(File item) {
        if (!item.exists()) {
            throw new IllegalStateException("Cannot add File to display that does not yet exist");
        }
        if (!item.getParentFile().equals(activeFolder)) {
            throw new IllegalArgumentException("File is not a child of the currently displayed folder");
        }
        currentDisplayContent.add(item);
    }

    @Override
    public File getItemByPosition(int position) {
        return currentDisplayContent.get(position);
    }

    @Override
    public boolean isHolderOutOfSync(FolderItemViewHolder holder, File newItem) {
        return isDirtyItemViewHolder(holder) || !(getItemPosition(holder.getItem()) == getItemPosition(newItem));
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


    class FolderItemCustomClickListener extends CustomClickListener<FolderItemViewAdapterPreferences, File, FolderItemViewHolder> {
        public FolderItemCustomClickListener(FolderItemViewHolder viewHolder, FolderItemRecyclerViewAdapter parentAdapter) {
            super(viewHolder, parentAdapter);
        }

        @Override
        public void onClick(View v) {
            if (getViewHolder().getItemViewType() == VIEW_TYPE_FOLDER) {
                changeFolderViewed(getViewHolder().getItem());
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
        public void fillValues(Context context, File newItem, boolean allowItemDeletion) {
            setItem(newItem);
            getTxtTitle().setVisibility(View.VISIBLE);
            getTxtTitle().setText(newItem.getName());
            if (!allowItemDeletion) {
                getDeleteButton().setVisibility(View.GONE);
            }
            getCheckBox().setVisibility(getAdapterPrefs().isAllowFolderSelection() ? View.VISIBLE : View.GONE);
            getCheckBox().setChecked(getSelectedItems().contains(newItem));
            getCheckBox().setEnabled(isEnabled());
        }

        @Override
        public void cacheViewFieldsAndConfigure() {
            super.cacheViewFieldsAndConfigure();
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
        public void fillValues(Context context, File newItem, boolean allowItemDeletion) {
            setItem(newItem);

            long bytes = newItem.length();
            double sizeMb = ((double)bytes)/1024/1024;
            itemHeading.setVisibility(View.VISIBLE);
            itemHeading.setText(String.format("%1$.2fMB", sizeMb));

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
            Uri itemUri = currentDisplayContentUris.get(newItem);
            if (itemUri != null) {
                getIconViewLoader().setUriToLoad(itemUri.toString());
            } else if (currentDisplayContentUris.size() > 0) {
                getIconViewLoader().setFileToLoad(newItem);
            } else {
                getIconViewLoader().setResourceToLoad(R.drawable.ic_file_gray_24dp);
            }
        }

        @Override
        public void cacheViewFieldsAndConfigure() {
            super.cacheViewFieldsAndConfigure();
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

    protected abstract class FolderItemViewHolder extends CustomViewHolder<FolderItemViewAdapterPreferences, File> implements PicassoLoader.PictureItemImageLoaderListener {
        private TextView txtTitle;
        private View deleteButton;
        private AppCompatCheckboxTriState checkBox;
        private ImageView iconView;
        private ResizingPicassoLoader<ImageView> iconViewLoader;

        public FolderItemViewHolder(View view) {
            super(view);
        }

        public TextView getTxtTitle() {
            return txtTitle;
        }

        public AppCompatCheckboxTriState getCheckBox() {
            return checkBox;
        }

        public ImageView getIconView() {
            return iconView;
        }

        public ResizingPicassoLoader getIconViewLoader() {
            return iconViewLoader;
        }

        public View getDeleteButton() {
            return deleteButton;
        }

        @Override
        public String toString() {
            return super.toString() + " '" + txtTitle.getText() + "'";
        }

        public abstract void fillValues(Context context, File newItem, boolean allowItemDeletion);

        @Override
        public void setChecked(boolean checked) {
            checkBox.setChecked(checked);
        }

        @Override
        public void cacheViewFieldsAndConfigure() {

            checkBox = itemView.findViewById(R.id.list_item_checked);
            checkBox.setClickable(getItemActionListener().getParentAdapter().isItemSelectionAllowed());
            checkBox.setOnCheckedChangeListener(getItemActionListener().getParentAdapter().new ItemSelectionListener(getItemActionListener().getParentAdapter(), this));
            if (isMultiSelectionAllowed()) {
                checkBox.setButtonDrawable(R.drawable.checkbox);
            } else {
                checkBox.setButtonDrawable(R.drawable.radio_button);
            }

            txtTitle = itemView.findViewById(R.id.list_item_name);

            iconView = itemView.findViewById(R.id.list_item_icon_thumbnail);
            iconView.setContentDescription("folder item thumb");
            iconViewLoader = new ResizingPicassoLoader<>(getIconView(), this, 0, 0);

            deleteButton = itemView.findViewById(R.id.list_item_delete_button);
            deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onDeleteItemButtonClick(v);
                }
            });
        }

        private void onDeleteItemButtonClick(View v) {
            getItemActionListener().getParentAdapter().onDeleteItem(this, v);
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
