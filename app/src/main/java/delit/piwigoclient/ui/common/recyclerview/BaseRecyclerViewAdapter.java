package delit.piwigoclient.ui.common.recyclerview;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import java.util.HashSet;

import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.ui.common.Enableable;
import delit.piwigoclient.ui.common.SelectableItemsAdapter;

/**
 * {@link RecyclerView.Adapter} that can display a {@link GalleryItem}
 */
public abstract class BaseRecyclerViewAdapter<T, S extends CustomViewHolder<T>> extends RecyclerView.Adapter<S> implements Enableable, SelectableItemsAdapter<T> {

    private static final String TAG = "BaseRecyclerViewAdapter";
    private Context context;
    private boolean allowItemSelection;
    private final MultiSelectStatusListener<T> multiSelectStatusListener;
    private HashSet<Long> selectedResourceIds = new HashSet<>(0);
    private HashSet<Long> initialSelectedResourceIds = new HashSet<>(0);
    private boolean initialSelectionLocked;
    private boolean captureActionClicks;
    private boolean allowItemDeletion;
    private boolean enabled;


    public BaseRecyclerViewAdapter(MultiSelectStatusListener multiSelectStatusListener, boolean captureActionClicks) {
        this.setHasStableIds(true);
        this.multiSelectStatusListener = multiSelectStatusListener;
        this.captureActionClicks = captureActionClicks;
    }

    @Override
    public abstract long getItemId(int position);

    public abstract S buildViewHolder(View view);

    protected abstract T getItemById(Long selectedId);

    protected abstract int getItemPosition(T item);

    protected abstract void removeItemFromInternalStore(int idxRemoved);

    protected abstract void replaceItemInInternalStore(int idxToReplace, T newItem);

    protected abstract T getItemFromInternalStoreMatching(T item);

    protected abstract void addItemToInternalStore(T item);

    public abstract T getItemByPosition(int position);

    public abstract boolean isHolderOutOfSync(S holder, T newItem);

    public abstract HashSet<Long> getItemsSelectedButNotLoaded();

    @Override
    public abstract int getItemCount();

    public void setAllowItemDeletion(boolean allowItemDeletion) {
        this.allowItemDeletion = allowItemDeletion;
    }

    public void setCaptureActionClicks(boolean captureActionClicks) {

        this.captureActionClicks = captureActionClicks;
        if (!captureActionClicks) {
            if (allowItemSelection) {
                toggleItemSelection();
            }
        }
    }

    @Override
    public void setInitiallySelectedItems(HashSet<Long> initialSelection, boolean initialSelectionLocked) {
        this.initialSelectedResourceIds = initialSelection != null ? new HashSet<>(initialSelection) : new HashSet<Long>(0);
        this.initialSelectionLocked = initialSelectionLocked;
    }

    @NonNull
    @Override
    public S onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        setContext(parent.getContext());
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.actionable_triselect_list_item_layout, parent, false);

        final S viewHolder = buildViewHolder(view);
        viewHolder.internalCacheViewFieldsAndConfigure(buildCustomClickListener(viewHolder));

        return viewHolder;
    }

    protected CustomClickListener<T,? extends CustomViewHolder<T>> buildCustomClickListener(S viewHolder) {
        return new CustomClickListener<>(viewHolder, this);
    }

    @Override
    public int getItemViewType(int position) {
        //groups.getItemByIdx(position).getType();
        // override this method for multiple item types
        return super.getItemViewType(position);
    }

    @Override
    public void onBindViewHolder(@NonNull S holder, int position) {
        T newItem = getItemByPosition(position);
        if (isHolderOutOfSync(holder, newItem)) {
            // store the item in this recyclable holder.
            holder.fillValues(getContext(), newItem, allowItemDeletion);
        }
    }

    protected final void setContext(Context context) {
        this.context = context;
    }

    private Context getContext() {
        return context;
    }

    public void setSelectedItems(HashSet<Long> selectedResourceIds) {
        this.selectedResourceIds = selectedResourceIds;
    }

    public void toggleItemSelection() {
        this.allowItemSelection = !allowItemSelection;
        if (!allowItemSelection) {
            selectedResourceIds.clear();
        }
        notifyItemRangeChanged(0, getItemCount());
        multiSelectStatusListener.onMultiSelectStatusChanged(allowItemSelection);
        multiSelectStatusListener.onItemSelectionCountChanged(selectedResourceIds.size());
    }

    public boolean isItemSelectionAllowed() {
        return allowItemSelection;
    }

    @Override
    public HashSet<Long> getSelectedItemIds() {
        return selectedResourceIds;
    }

    /**
     * Note: May throw an IllegalStateException if not all items loaded yet
     * @return
     */
    @Override
    public HashSet<T> getSelectedItems() {
        try {
            HashSet<T> selectedItems = new HashSet<>();
            for (Long selectedItemId : selectedResourceIds) {
                selectedItems.add(getItemById(selectedItemId));
            }
            return selectedItems;
        } catch(RuntimeException e) {
            throw new IllegalStateException("Not all items are loaded yet. Unable to provide a list of them");
        }
    }

    @Override
    public void clearSelectedItemIds() {
        selectedResourceIds.clear();
        notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    public void selectAllItemIds() {

        notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    public void setItemSelected(Long selectedItemId) {
        selectedResourceIds.add(selectedItemId);
        try {
            T item = getItemById(selectedItemId);
            int idx = getItemPosition(item);
            notifyItemChanged(idx);
        } catch(IllegalArgumentException e) {
            if(BuildConfig.DEBUG) {
                Log.e(TAG, "Item not available to select (probably not loaded yet)", e);
            }
        }
    }

    public void remove(T group) {
        int idxRemoved = getItemPosition(group);
        if(idxRemoved >= 0) {
            removeItemFromInternalStore(idxRemoved);
            notifyItemRemoved(idxRemoved);
        }
    }

    public void replaceOrAddItem(T group) {
        T itemToBeReplaced = null;
        try {
            itemToBeReplaced = getItemFromInternalStoreMatching(group);
        } catch (IllegalArgumentException e) {
            // thrown if the group isn't present.
        }
        if (itemToBeReplaced != null) {
            int replaceIdx = getItemPosition(itemToBeReplaced);
            replaceItemInInternalStore(replaceIdx, group);
        } else {
            addItemToInternalStore(group);
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        boolean changed = false;
        if (this.enabled != enabled) {
            changed = true;
        }
        this.enabled = enabled;
        if (changed) {
            notifyDataSetChanged();
        }
    }


    public interface MultiSelectStatusListener<T> {
        void onMultiSelectStatusChanged(boolean multiSelectEnabled);

        void onItemSelectionCountChanged(int size);

        void onItemDeleteRequested(T g);

        void onItemClick(T item);

        void onItemLongClick(T item);

    }

    protected class ItemSelectionListener implements CompoundButton.OnCheckedChangeListener {

        private final S holder;

        public ItemSelectionListener(S holder) {
            this.holder = holder;
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            boolean changed = false;
            if (isChecked) {
                changed = selectedResourceIds.add(holder.getItemId());
            } else {
                if(isAllowItemDeselection(holder.getItemId())) {
                    changed = selectedResourceIds.remove(holder.getItemId());
                } else {
                    // re-check the button.
                    buttonView.setChecked(true);
                }
            }
            if (changed) {
                multiSelectStatusListener.onItemSelectionCountChanged(selectedResourceIds.size());
            }
        }
    }

    protected boolean isAllowItemDeselection(long itemId) {
        return !initialSelectionLocked || !initialSelectedResourceIds.contains(itemId);
    }

    public boolean isCaptureActionClicks() {
        return captureActionClicks;
    }

    protected void onDeleteItem(S viewHolder, View v) {
        multiSelectStatusListener.onItemDeleteRequested(viewHolder.getItem());
    }


    public MultiSelectStatusListener<T> getMultiSelectStatusListener() {
        return multiSelectStatusListener;
    }
}
