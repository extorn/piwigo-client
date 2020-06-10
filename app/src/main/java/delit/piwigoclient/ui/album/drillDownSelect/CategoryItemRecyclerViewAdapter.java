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

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.crashlytics.android.Crashlytics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapter;
import delit.libs.ui.view.recycler.BaseViewHolder;
import delit.libs.ui.view.recycler.CustomClickListener;
import delit.piwigoclient.R;
import delit.piwigoclient.business.PicassoLoader;
import delit.piwigoclient.business.ResizingPicassoLoader;
import delit.piwigoclient.model.piwigo.CategoryItem;

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
        if (activeItem != null) {
            List<CategoryItem> folderContent = activeItem.getChildAlbums();
            currentDisplayContent = folderContent != null ? new ArrayList<>(folderContent) : new ArrayList<CategoryItem>(0);
        } else {
            currentDisplayContent = new ArrayList<>(0);
        }
        notifyDataSetChanged();
    }

    public CategoryItem getActiveItem() {
        return activeItem;
    }

    /**
     *
     * @param activeItem
     * @return true if the active item could be changed at this time
     */
    public boolean setActiveItem(CategoryItem activeItem) {
        updateActiveContent(activeItem, true);
        return true;
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
        return isDirtyItemViewHolder(holder, newItem);
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
            if (getViewHolder().getItemViewType() == VIEW_TYPE_FOLDER && getViewHolder().getItem().getChildAlbumCount() > 0) {
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

    private static class PredrawListener implements ViewTreeObserver.OnPreDrawListener {
        private final ResizingPicassoLoader iconViewLoader;
        private final ImageView iconView;

        public PredrawListener(ImageView iconView, ResizingPicassoLoader iconViewLoader) {
            this.iconView = iconView;
            this.iconViewLoader = iconViewLoader;
        }

        @Override
        public boolean onPreDraw() {
            try {
                if (!iconViewLoader.isImageLoaded() && !iconViewLoader.isImageLoading() && !iconViewLoader.isImageUnavailable()) {

                    int imgSize = iconView.getMeasuredWidth();
                    iconViewLoader.setResizeTo(imgSize, imgSize);
                    iconViewLoader.load();
                }
            } catch (IllegalStateException e) {
                Crashlytics.logException(e);
                // image loader not configured yet...
            }
            return true;
        }
    }

    protected class SimpleCategoryItemViewHolder extends CategoryItemViewHolder {

        public SimpleCategoryItemViewHolder(View view) {
            super(view);
        }

        @Override
        public void fillValues(CategoryItem newItem, boolean allowItemDeletion) {
            setItem(newItem);
//            getTxtTitle().setVisibility(View.VISIBLE);
            getTxtTitle().setText(newItem.getName());
            getDetailsTitle().setVisibility(View.GONE);
            if(newItem.getChildAlbumCount() > 0) {
                getDetailsTitle().setText(itemView.getContext().getString(R.string.subalbum_detail_txt_pattern, newItem.getChildAlbumCount()));
                getDetailsTitle().setVisibility(View.VISIBLE);
            }

            if (!allowItemDeletion) {
                getDeleteButton().setVisibility(View.GONE);
            }
            getCheckBox().setVisibility(getAdapterPrefs().isAllowItemSelection() ? View.VISIBLE : View.GONE);
            getCheckBox().setChecked(getSelectedItems().contains(newItem));
            getCheckBox().setEnabled(isEnabled());

            if(newItem.getThumbnailUrl() != null) {
                ImageViewCompat.setImageTintMode(getIconView(), PorterDuff.Mode.DST); //IGNORE THE TINT - Use the image as is.
                geticonViewLoader().setUriToLoad(newItem.getThumbnailUrl());
            } else {
                ImageViewCompat.setImageTintMode(getIconView(), PorterDuff.Mode.SRC_ATOP); // SRC_ATOP: use color of the tint to shade the non transparent parts of the image
                geticonViewLoader().setResourceToLoad(R.drawable.ic_folder_black_24dp);
            }
        }

        @Override
        public void cacheViewFieldsAndConfigure(CategoryItemViewAdapterPreferences adapterPrefs) {
            super.cacheViewFieldsAndConfigure(adapterPrefs);
            geticonViewLoader().withErrorDrawable(R.drawable.ic_file_gray_24dp);
            final ViewTreeObserver.OnPreDrawListener predrawListener = new PredrawListener(getIconView(), geticonViewLoader());

            // default to setting a tint (we'll apply it if needed depending on image shown)
            ColorStateList colorList;
            if (!getAdapterPrefs().isEnabled()) {
                @ColorInt int c = DisplayUtils.getColor(getIconView().getContext(), R.attr.scrimHeavy);
                colorList = ColorStateList.valueOf(c);
            } else {
                colorList = ColorStateList.valueOf(ContextCompat.getColor(itemView.getContext(), R.color.app_secondary));//ColorStateList.valueOf(DisplayUtils.getColor(getContext(), R.attr.colorPrimary));
            }
            ImageViewCompat.setImageTintList(getIconView(), colorList);
            ImageViewCompat.setImageTintMode(getIconView(), PorterDuff.Mode.SRC_ATOP); //SRC_ATOP: use color of the tint to shade the non transparent parts of the image


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

    protected abstract class CategoryItemViewHolder extends BaseViewHolder<CategoryItemViewAdapterPreferences, CategoryItem> implements PicassoLoader.PictureItemImageLoaderListener {
        private ImageView iconView;
        private ResizingPicassoLoader iconViewLoader;

        public CategoryItemViewHolder(View view) {
            super(view);
        }

        public ImageView getIconView() {
            return iconView;
        }

        public ResizingPicassoLoader geticonViewLoader() {
            return iconViewLoader;
        }

        public abstract void fillValues(CategoryItem newItem, boolean allowItemDeletion);

        @Override
        public void cacheViewFieldsAndConfigure(CategoryItemViewAdapterPreferences adapterPrefs) {

            super.cacheViewFieldsAndConfigure(adapterPrefs);

            iconView = itemView.findViewById(R.id.list_item_icon_thumbnail);
            iconView.setContentDescription("cat thumb");
            iconViewLoader = new ResizingPicassoLoader(getIconView(), this, 0, 0);
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


}
