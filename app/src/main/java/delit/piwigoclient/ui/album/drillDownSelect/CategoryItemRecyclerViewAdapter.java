package delit.piwigoclient.ui.album.drillDownSelect;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.crashlytics.android.Crashlytics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import delit.piwigoclient.R;
import delit.piwigoclient.business.PicassoLoader;
import delit.piwigoclient.business.ResizingPicassoLoader;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.ui.common.button.AppCompatCheckboxTriState;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapter;
import delit.piwigoclient.ui.common.recyclerview.CustomClickListener;
import delit.piwigoclient.ui.common.recyclerview.CustomViewHolder;

public class CategoryItemRecyclerViewAdapter extends BaseRecyclerViewAdapter<CategoryItemViewAdapterPreferences, CategoryItem, CategoryItemRecyclerViewAdapter.CategoryItemViewHolder, BaseRecyclerViewAdapter.MultiSelectStatusListener<CategoryItem>> {

    public final static int VIEW_TYPE_FOLDER = 0;
    public final static int VIEW_TYPE_FILE = 1;
    private transient List<CategoryItem> currentDisplayContent;
    private CategoryItem overallRoot;
    private CategoryItem activeItem;
    private NavigationListener navigationListener;

    public CategoryItemRecyclerViewAdapter(CategoryItem root, NavigationListener navigationListener, MultiSelectStatusListener<CategoryItem> multiSelectStatusListener, CategoryItemViewAdapterPreferences viewPrefs) {
        super(multiSelectStatusListener, viewPrefs);
        this.navigationListener = navigationListener;
        overallRoot = root;
        long initialRootId = viewPrefs.getInitialRoot().getId();
        CategoryItem initiallyActiveItem = null;
        if(initialRootId != 0) {
            initiallyActiveItem = overallRoot.findChild(viewPrefs.getInitialRoot().getId());
            // get the parent (as we are using the selected item at the moment).... which is wrong incidentally.
            if(initiallyActiveItem != null) {
                // this item might be null if the gallery structure has altered and this album doesn't exist any longer.
                if(initiallyActiveItem.getParentId() != null) {
                    initiallyActiveItem = overallRoot.findChild(initiallyActiveItem.getParentId());
                } else {
                    initiallyActiveItem = overallRoot;
                }
            }
        }
        if(initiallyActiveItem == null) {
            initiallyActiveItem = overallRoot;
        }
        updateActiveContent(initiallyActiveItem, false);
    }

    public void setInitiallySelectedItems() {
        HashSet<Long> initialSelectionIds = getAdapterPrefs().getInitialSelection();
        if (initialSelectionIds != null) {
            // update the visible selection.
            setInitiallySelectedItems(initialSelectionIds);
            setSelectedItems(initialSelectionIds);
        }
    }

    protected void updateActiveContent(CategoryItem newDisplayRoot, boolean forceUpdate) {
        if (!forceUpdate && newDisplayRoot.equals(activeItem)) {
            return;
        }
        navigationListener.onCategoryOpened(activeItem, newDisplayRoot);
        activeItem = newDisplayRoot;
//        getSelectedItemIds().clear();
        List<CategoryItem> folderContent = activeItem.getChildAlbums();
        currentDisplayContent = folderContent != null ? new ArrayList<>(folderContent) : new ArrayList<CategoryItem>(0);
        notifyDataSetChanged();
    }

    public CategoryItem getActiveItem() {
        return activeItem;
    }

    public void setActiveItem(CategoryItem activeItem) {
        updateActiveContent(activeItem, true);
    }

    @Override
    protected CustomClickListener<CategoryItemViewAdapterPreferences, CategoryItem, CategoryItemViewHolder> buildCustomClickListener(CategoryItemViewHolder viewHolder) {
        return new CategoryItemCustomClickListener(viewHolder, this);
    }

    @Override
    public long getItemId(int position) {
        return getItemByPosition(position).getId();
    }

    @Override
    public int getItemViewType(int position) {
        return VIEW_TYPE_FOLDER; // folders are clickable :-)
//        CategoryItem f = getItemByPosition(position);
//        if (f.getChildAlbumCount() > 0) {
//            return VIEW_TYPE_FOLDER;
//        }
//        return VIEW_TYPE_FILE;
    }

    @NonNull
    protected View inflateView(@NonNull ViewGroup parent, int viewType) {
//        switch (viewType) {
//            default:
//            case VIEW_TYPE_FILE:
//            case VIEW_TYPE_FILE_IMAGE:
//                return LayoutInflater.from(parent.getContext())
//                        .inflate(R.layout.layout_actionable_triselect_list_item_large_icon, parent, false);
//            case VIEW_TYPE_FOLDER:
//                return LayoutInflater.from(parent.getContext())
//                        .inflate(R.layout.layout_actionable_triselect_list_item_icon, parent, false);
//        }
        // always use the same layout for now.
        return LayoutInflater.from(parent.getContext())
                .inflate(R.layout.layout_actionable_triselect_list_item_icon_and_detail, parent, false);

    }

    @NonNull
    @Override
    public SimpleCategoryItemViewHolder buildViewHolder(View view, int viewType) {
//        if (viewType == VIEW_TYPE_FOLDER) {
//            return new SimpleCategoryItemViewHolder(view);
//        } else {
//            return new CategoryItemFileViewHolder(view);
//        }
        // always use the same layout for now.
        return new SimpleCategoryItemViewHolder(view);
    }

    @Override
    protected CategoryItem getItemById(Long selectedId) {
        return overallRoot.findChild(selectedId);
    }

    @Override
    public int getItemPosition(CategoryItem item) {
        return currentDisplayContent.indexOf(item);
    }

    @Override
    protected void removeItemFromInternalStore(int idxRemoved) {
        CategoryItem catItem = currentDisplayContent.get(idxRemoved);
        activeItem.removeChildAlbum(catItem);
        currentDisplayContent.remove(idxRemoved);
    }

    @Override
    protected void replaceItemInInternalStore(int idxToReplace, CategoryItem newItem) {
        throw new UnsupportedOperationException("This makes no sense for a file structure traversal");
    }

    @Override
    protected CategoryItem getItemFromInternalStoreMatching(CategoryItem item) {
        // they'll always be the same
        return item;
    }

    @Override
    protected void addItemToInternalStore(CategoryItem item) {
        if (null == activeItem.findImmediateChild(item.getId())) {
            throw new IllegalArgumentException("CategoryItem is not a child of the currently displayed CategoryItem");
        }
        currentDisplayContent.add(item);
    }

    @Override
    public CategoryItem getItemByPosition(int position) {
        return currentDisplayContent.get(position);
    }

    @Override
    public boolean isHolderOutOfSync(CategoryItemViewHolder holder, CategoryItem newItem) {
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
        void onCategoryOpened(CategoryItem oldCategory, CategoryItem newCategory);
    }

    class CategoryItemCustomClickListener extends CustomClickListener<CategoryItemViewAdapterPreferences, CategoryItem, CategoryItemViewHolder> {
        public <Q extends BaseRecyclerViewAdapter<CategoryItemViewAdapterPreferences, CategoryItem, CategoryItemViewHolder, MultiSelectStatusListener<CategoryItem>>> CategoryItemCustomClickListener(CategoryItemViewHolder viewHolder, Q parentAdapter) {
            super(viewHolder, parentAdapter);
        }

        @Override
        public void onClick(View v) {
            if (getViewHolder().getItemViewType() == VIEW_TYPE_FOLDER) {
                updateActiveContent(getViewHolder().getItem(), false);
            } else if (getAdapterPrefs().isAllowItemSelection()) {
                super.onClick(v);
            }
        }

        @Override
        public boolean onLongClick(View v) {
            if (getViewHolder().getItemViewType() == VIEW_TYPE_FOLDER && getAdapterPrefs().isAllowItemSelection()) {
                super.onClick(v);
            }
            return super.onLongClick(v);
        }
    }

    protected class SimpleCategoryItemViewHolder extends CategoryItemViewHolder {

        public SimpleCategoryItemViewHolder(View view) {
            super(view);
        }

        @Override
        public void fillValues(Context context, CategoryItem newItem, boolean allowItemDeletion) {
            setItem(newItem);
//            getTxtTitle().setVisibility(View.VISIBLE);
            getTxtTitle().setText(newItem.getName());
            getDetailTxt().setVisibility(View.GONE);
            if(newItem.getChildAlbumCount() > 0) {
                getDetailTxt().setText(context.getString(R.string.subalbum_detail_txt_pattern, newItem.getChildAlbumCount()));
                getDetailTxt().setVisibility(View.VISIBLE);
            }

            if (!allowItemDeletion) {
                getDeleteButton().setVisibility(View.GONE);
            }
            getCheckBox().setVisibility(getAdapterPrefs().isAllowItemSelection() ? View.VISIBLE : View.GONE);
            getCheckBox().setChecked(getSelectedItems().contains(newItem));
            getCheckBox().setEnabled(isEnabled());

            if(newItem.getThumbnailUrl() != null) {
                ImageViewCompat.setImageTintMode(getIconView(), null);
                getIconViewLoader().setUriToLoad(newItem.getThumbnailUrl());
            } else {
                ImageViewCompat.setImageTintMode(getIconView(), PorterDuff.Mode.SRC_IN);
                getIconViewLoader().setResourceToLoad(R.drawable.ic_folder_black_24dp);
            }
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

            // default to setting a tint (we'll apply it if needed depending on image shown)
            ColorStateList colorList;
            if (!getAdapterPrefs().isEnabled()) {
                colorList = ColorStateList.valueOf(Color.GRAY);
            } else {
                colorList = ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.primary_text_default));
            }
            ImageViewCompat.setImageTintList(getIconView(), colorList);


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
        }
    }

    protected abstract class CategoryItemViewHolder extends CustomViewHolder<CategoryItemViewAdapterPreferences, CategoryItem> implements PicassoLoader.PictureItemImageLoaderListener {
        private TextView txtTitle;
        private TextView detailTxt;
        private View deleteButton;
        private AppCompatCheckboxTriState checkBox;
        private ImageView iconView;
        private ResizingPicassoLoader iconViewLoader;

        public CategoryItemViewHolder(View view) {
            super(view);
        }

        public TextView getTxtTitle() {
            return txtTitle;
        }

        public TextView getDetailTxt() {
            return detailTxt;
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

        public abstract void fillValues(Context context, CategoryItem newItem, boolean allowItemDeletion);

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

            detailTxt = itemView.findViewById(R.id.list_item_details);

            iconView = itemView.findViewById(R.id.list_item_icon_thumbnail);
            iconView.setContentDescription("cat thumb");
            iconViewLoader = new ResizingPicassoLoader(getIconView(), this, 0, 0);

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
