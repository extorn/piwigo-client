package delit.piwigoclient.ui.file;

import android.content.Context;
import android.graphics.PorterDuff;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.common.util.ArrayUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ResizingPicassoLoader;
import delit.piwigoclient.ui.common.button.AppCompatCheckboxTriState;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapter;
import delit.piwigoclient.ui.common.recyclerview.CustomClickListener;
import delit.piwigoclient.ui.common.recyclerview.CustomViewHolder;
import delit.piwigoclient.util.ObjectUtils;

public class FolderItemRecyclerViewAdapter extends BaseRecyclerViewAdapter<FolderItemViewAdapterPreferences, File, FolderItemRecyclerViewAdapter.FolderItemViewHolder> {

    public final static int VIEW_TYPE_FOLDER = 0;
    public final static int VIEW_TYPE_FILE = 1;
    public final static int VIEW_TYPE_FILE_IMAGE = 2;
    private transient List<File> currentDisplayContent;
    private File activeFolder;
    private Comparator<? super File> fileComparator;
    private NavigationListener navigationListener;

    public FolderItemRecyclerViewAdapter(NavigationListener navigationListener, MultiSelectStatusListener multiSelectStatusListener, FolderItemViewAdapterPreferences folderViewPrefs) {
        super(multiSelectStatusListener, folderViewPrefs);
        this.navigationListener = navigationListener;
        updateContent(folderViewPrefs.getInitialFolderAsFile());
    }

    public void setInitiallySelectedItems() {
        List<String> initialSelectionItems = getAdapterPrefs().getInitialSelection();
        if (initialSelectionItems != null) {
            HashSet<Long> initialSelectionIds = new HashSet<>(initialSelectionItems.size());
            for (String selectedItem : initialSelectionItems) {
                int pos = getItemPosition(new File(selectedItem));
                if (pos >= 0) {
                    initialSelectionIds.add(getItemId(pos));
                }
            }
            // update the visible selection.
            setInitiallySelectedItems(initialSelectionIds);
            setSelectedItems(initialSelectionIds);
        }
    }

    protected void updateContent(File newContent) {
        if (ObjectUtils.areEqual(activeFolder, newContent)) {
            return;
        }
        navigationListener.onFolderOpened(activeFolder, newContent);
        activeFolder = newContent;
        getSelectedItemIds().clear();
        File[] folderContent = activeFolder.listFiles(getAdapterPrefs().getFileFilter());
        currentDisplayContent = folderContent != null ? ArrayUtils.toArrayList(folderContent) : new ArrayList(0);
        Collections.sort(currentDisplayContent, getFileComparator());
        notifyDataSetChanged();
    }

    public File getActiveFolder() {
        return activeFolder;
    }

    public void setActiveFolder(File activeFolder) {
        this.activeFolder = activeFolder;
    }

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
                        .inflate(R.layout.actionable_triselect_list_item_large_icon_layout, parent, false);
            case VIEW_TYPE_FOLDER:
                return LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.actionable_triselect_list_item_icon_layout, parent, false);
        }

    }

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
        return currentDisplayContent.size();
    }

    public interface NavigationListener {
        void onFolderOpened(File oldFolder, File newFolder);
    }

    class FolderItemCustomClickListener extends CustomClickListener<FolderItemViewAdapterPreferences, File, FolderItemViewHolder> {
        public <Q extends BaseRecyclerViewAdapter<FolderItemViewAdapterPreferences, File, FolderItemViewHolder>> FolderItemCustomClickListener(FolderItemViewHolder viewHolder, Q parentAdapter) {
            super(viewHolder, parentAdapter);
        }

        @Override
        public void onClick(View v) {
            if (getViewHolder().getItemViewType() == VIEW_TYPE_FOLDER) {
                updateContent(getViewHolder().getItem());
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
            getIconView().setColorFilter(ContextCompat.getColor(getContext(),R.color.colorSecondary), PorterDuff.Mode.SRC_IN);
            getIconViewLoader().setResourceToLoad(R.drawable.ic_folder_black_24dp);
            getIconViewLoader().load();
        }
    }

    protected class FolderItemFileViewHolder extends FolderItemViewHolder {

        public FolderItemFileViewHolder(View view) {
            super(view);
        }

        @Override
        public void fillValues(Context context, File newItem, boolean allowItemDeletion) {
            setItem(newItem);
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
            getIconViewLoader().setFileToLoad(getItem());
        }

        @Override
        public void cacheViewFieldsAndConfigure() {
            super.cacheViewFieldsAndConfigure();
            getIconViewLoader().withErrorDrawable(R.drawable.ic_file_gray_24dp);
            final ViewTreeObserver.OnPreDrawListener predrawListener = new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    try {
                        if (!getIconViewLoader().isImageLoaded() && !getIconViewLoader().isImageLoading() && !getIconViewLoader().isImageUnavailable()) {

                            int imgSize = getIconView().getMeasuredWidth();
                            getIconViewLoader().setResizeTo(imgSize, imgSize);
                            getIconViewLoader().load();
                        }
                    } catch (IllegalStateException e) {
                        Crashlytics.logException(e);
                        // image loader not configured yet...
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

    protected abstract class FolderItemViewHolder extends CustomViewHolder<FolderItemViewAdapterPreferences, File> {
        private TextView txtTitle;
        private View deleteButton;
        private AppCompatCheckboxTriState checkBox;
        private ImageView iconView;
        private ResizingPicassoLoader iconViewLoader;

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
                checkBox.setButtonDrawable(R.drawable.always_clear_checkbox);
            } else {
                checkBox.setButtonDrawable(R.drawable.always_clear_radio);
            }

            txtTitle = itemView.findViewById(R.id.list_item_name);

            iconView = itemView.findViewById(R.id.list_item_icon_thumbnail);
            iconViewLoader = new ResizingPicassoLoader(getIconView(), 0, 0);

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
    }


}
