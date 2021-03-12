package delit.libs.ui.view.recycler;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashSet;
import java.util.Iterator;

import delit.libs.BuildConfig;
import delit.libs.R;
import delit.libs.core.util.Logging;
import delit.libs.ui.view.Enableable;
import delit.libs.ui.view.list.SelectableItemsAdapter;

/**
 * {@link RecyclerView.Adapter} that can display items
 * NOTE: MUST override getItemId(int position) if using stable IDs and it MUST NOT return item position if sort order changes!
 *
 * @param <T> Item
 * @param <VH> ViewHolder for each Item
 */
public abstract class BaseRecyclerViewAdapter<LVA extends BaseRecyclerViewAdapter<LVA, P,T, VH, MSL>, P extends BaseRecyclerViewAdapterPreferences<P>, T, VH extends CustomViewHolder<VH, LVA, P, T,MSL>, MSL extends BaseRecyclerViewAdapter.MultiSelectStatusListener<MSL,LVA,P,T,VH>> extends RecyclerView.Adapter<VH> implements Enableable, SelectableItemsAdapter<T> {

    private static final String TAG = "BaseRecyclerViewAdapter";
    final MSL multiSelectStatusListener;
    private final P prefs;
    HashSet<Long> selectedResourceIds = new HashSet<>(0);
    private HashSet<Long> initialSelectedResourceIds = new HashSet<>(0);

    public BaseRecyclerViewAdapter(MSL multiSelectStatusListener, P prefs) {
        this.multiSelectStatusListener = multiSelectStatusListener;
        this.prefs = prefs;
    }

    public P getAdapterPrefs() {
        return prefs;
    }

    public abstract @NonNull
    VH buildViewHolder(View view, int viewType);

    protected abstract @Nullable T getItemById(@NonNull Long selectedId);

    public abstract int getItemPosition(@NonNull T item);

    protected abstract T removeItemFromInternalStore(int idxToRemove);

    protected abstract void replaceItemInInternalStore(int idxToReplace, @NonNull T newItem);

    protected abstract @NonNull T getItemFromInternalStoreMatching(@NonNull T item);

    protected abstract void addItemToInternalStore(@NonNull T item);

    public abstract @NonNull T getItemByPosition(int position);

    public abstract boolean isHolderOutOfSync(VH holder, T newItem);

    public abstract HashSet<Long> getItemsSelectedButNotLoaded();

    @Override
    public abstract int getItemCount();

    public boolean isItemSelected(Long itemId) {
        return selectedResourceIds.contains(itemId);
    }

    @NonNull
    protected View inflateView(@NonNull ViewGroup parent, int viewType) {
        return LayoutInflater.from(parent.getContext())
                .inflate(R.layout.layout_actionable_triselect_list_item, parent, false);
    }

    /**
     * @param holder
     * @return true if this holder has never been used before (or is totally clean)
     */
    protected boolean isDirtyItemViewHolder(VH holder, T newItem) {
        return holder.isDirty(newItem);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflateView(parent, viewType);

        final VH viewHolder = buildViewHolder(view, viewType);
        CustomClickListener<MSL,LVA, P, T, VH> clickListener = buildCustomClickListener(viewHolder);
        viewHolder.internalCacheViewFieldsAndConfigure(clickListener, getAdapterPrefs());
        return viewHolder;
    }

    protected CustomClickListener<MSL,LVA, P, T, VH> buildCustomClickListener(VH viewHolder) {
        return new CustomClickListener<>(viewHolder, (LVA) this);
    }

    @Override
    public int getItemViewType(int position) {
        //items.getItemByIdx(position).getType();
        // override this method for multiple item types
        return super.getItemViewType(position);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        try {
            T newItem = getItemByPosition(position);
            if (isHolderOutOfSync(holder, newItem)) {
                // store the item in this recyclable holder.
                holder.fillValues(newItem, prefs.isAllowItemDeletion());
            } else {
                holder.redisplayOldValues(newItem, prefs.isAllowItemDeletion());
            }
            try {
                holder.setChecked(isItemSelected(getItemId(position)));
            } catch (UnsupportedOperationException e) {
                // this is fine. Clearly selection is not wished for.
            }
        } catch (IndexOutOfBoundsException e) {
            Logging.log(Log.DEBUG, TAG, "Unable to bind item in viewAdapter - IndexOutOfBoundsException");
        }

    }

    @Override
    public void setInitiallySelectedItems(HashSet<Long> initialSelection) {
        this.initialSelectedResourceIds = initialSelection != null ? new HashSet<>(initialSelection) : new HashSet<>(0);
    }

    public void toggleItemSelection() {
        prefs.setAllowItemSelection(!prefs.isAllowItemSelection());
        if (!prefs.isAllowItemSelection()) {
            selectedResourceIds.clear();
        }
        notifyItemRangeChanged(0, getItemCount());
        multiSelectStatusListener.onMultiSelectStatusChanged((LVA) this, prefs.isAllowItemSelection());
        multiSelectStatusListener.onItemSelectionCountChanged((LVA) this, selectedResourceIds.size());
    }

    public boolean isItemSelectionAllowed() {
        return prefs.isAllowItemSelection();
    }

    @Override
    public HashSet<Long> getSelectedItemIds() {
        return selectedResourceIds;
    }

    /**
     * Note: May throw an IllegalStateException if not all items loaded yet
     *
     * @return
     */
    public <IT extends T> HashSet<IT> getSelectedItemsOfType(Class<IT> type) {
        HashSet<IT> selectedItems = new HashSet<>();
        for(T item : getSelectedItems()) {
            if(type.isInstance(item)) {
                selectedItems.add(type.cast(item));
            }
        }
        return selectedItems;
    }

    /**
     * Note: May throw an IllegalStateException if not all items loaded yet
     *
     * @return
     */
    @Override
    public HashSet<T> getSelectedItems() {
        try {
            HashSet<T> selectedItems = new HashSet<>();
            for (Long selectedItemId : selectedResourceIds) {
                T selectedItem = getItemById(selectedItemId);
                if(selectedItem != null) {
                    selectedItems.add(selectedItem);
                } else {
                    throw new IllegalStateException("Not all items are loaded yet. Unable to provide a list of them: Item not found");
                }
            }
            return selectedItems;
        } catch (RuntimeException e) {
            Logging.recordException(e);
            throw new IllegalStateException("Not all items are loaded yet. Unable to provide a list of them", e);
        }
    }

    @Override
    public void setSelectedItems(HashSet<Long> selectedResourceIds) {
        if (initialSelectedResourceIds == null) {
            throw new IllegalStateException("initially selected items should never be null at this point");
        }
        this.selectedResourceIds = selectedResourceIds != null ? new HashSet<>(selectedResourceIds) : new HashSet<>(initialSelectedResourceIds);
    }

    @Override
    public void clearSelectedItemIds() {
        selectedResourceIds.clear();
        multiSelectStatusListener.onItemSelectionCountChanged((LVA) this,selectedResourceIds.size());
        notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    public void selectAllItemIds() {
        for (int i = 0; i < getItemCount(); i++) {
            selectedResourceIds.add(getItemId(i));
        }
        multiSelectStatusListener.onItemSelectionCountChanged((LVA) this,selectedResourceIds.size());
        notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    public void setItemSelected(Long selectedItemId) {
        selectedResourceIds.add(selectedItemId);
        try {
            T item = getItemById(selectedItemId);
            int idx = getItemPosition(item);
            notifyItemChanged(idx);
        } catch (IllegalArgumentException e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Item not available to select (probably not loaded yet)", e);
            }
        }
    }

    public void deselectItem(Long itemId, boolean force) {
        if (force || isAllowItemDeselection(itemId)) {
            selectedResourceIds.remove(itemId);
            try {
                T item = getItemById(itemId);
                int idx = getItemPosition(item);
                notifyItemChanged(idx);
            } catch (IllegalArgumentException e) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Item not available to deselect (probably not loaded yet)", e);
                }
            }
        }
    }

    public void removeItemByPosition(int idxRemoved) {
        if (idxRemoved >= 0) {
            removeItemFromInternalStore(idxRemoved);
            if(idxRemoved > 0) {
                notifyItemChanged(idxRemoved -1, Boolean.FALSE);
            }
            notifyItemRemoved(idxRemoved);
            if(idxRemoved < getItemCount() -1) {
                notifyItemChanged(idxRemoved +1, Boolean.FALSE);
            }
        }
    }

    public void remove(T item) {
        int idxRemoved = getItemPosition(item);
        removeItemByPosition(idxRemoved);
    }

    public final void replaceOrAddItem(T item) {
        T itemToBeReplaced = null;
        try {
            itemToBeReplaced = getItemFromInternalStoreMatching(item);
        } catch (IllegalArgumentException e) {
            Logging.recordException(e);
            // thrown if the item isn't present.
        }
        if (itemToBeReplaced != null) {
            int replaceIdx = getItemPosition(itemToBeReplaced);
            replaceItemInInternalStore(replaceIdx, item);
            if(replaceIdx > 0) {
                notifyItemChanged(replaceIdx -1, Boolean.FALSE);
            }
            notifyItemChanged(replaceIdx);
            if(replaceIdx < getItemCount() -1) {
                notifyItemChanged(replaceIdx +1, Boolean.FALSE);
            }
        } else {
            addItem(item);
        }
    }

    public final void addItem(T item) {
        addItemToInternalStore(item);
        int itemPos = getItemPosition(item);
        if(itemPos > 0) {
            notifyItemChanged(itemPos -1, Boolean.FALSE);
        }
        notifyItemInserted(itemPos);
        if(itemPos < getItemCount() -1) {
            notifyItemChanged(itemPos +1, Boolean.FALSE);
        }
    }

    @Override
    public boolean isEnabled() {
        return prefs.isEnabled();
    }

    @Override
    public void setEnabled(boolean enabled) {
        boolean changed = false;
        if (enabled != prefs.isEnabled()) {
            changed = true;
        }
        prefs.setEnabled(enabled);
        if (changed) {
            notifyDataSetChanged();
        }
    }


    public ItemSelectionListener<LVA, VH,P,T,MSL> buildItemSelectionListener(VH viewHolder) {
        if(!hasStableIds()) {
            throw new IllegalStateException("Adapter must have stable ids to use the item selection listener. If item IDs are stable (getItemId), then mark the adapter with adapter.setHasStableIds(true)");
        }
        return new ItemSelectionListener<>((LVA)this, viewHolder);
    }

    @Override
    public boolean isAllowItemDeselection(long itemId) {
        return (!prefs.isInitialSelectionLocked() || !initialSelectedResourceIds.contains(itemId)) && (isMultiSelectionAllowed() || selectedResourceIds.size() > 1);
    }

    public boolean isMultiSelectionAllowed() {
        return prefs.isMultiSelectionEnabled();
    }

    public void onDeleteItem(VH viewHolder, View v) {
        multiSelectStatusListener.onItemDeleteRequested((LVA) this, viewHolder.getItem());
    }

    public MSL getMultiSelectStatusListener() {
        return multiSelectStatusListener;
    }

    public interface MultiSelectStatusListener<MSL extends MultiSelectStatusListener<MSL,LVA,P,T,VH>, LVA extends BaseRecyclerViewAdapter<LVA, P, T, VH, MSL>, P extends BaseRecyclerViewAdapterPreferences<P>, T, VH extends CustomViewHolder<VH, LVA, P, T,MSL>> {
        void onMultiSelectStatusChanged(LVA adapter, boolean multiSelectEnabled);

        void onItemSelectionCountChanged(LVA adapter, int size);

        void onItemDeleteRequested(LVA adapter, T g);

        void onItemClick(LVA adapter, T item);

        void onItemLongClick(LVA adapter, T item);

        void onDisabledItemClick(LVA adapter, T item);
    }

    public static class MultiSelectStatusAdapter<MSL extends MultiSelectStatusAdapter<MSL,LVA,P,T,VH>, LVA extends BaseRecyclerViewAdapter<LVA, P, T, VH, MSL>, P extends BaseRecyclerViewAdapterPreferences<P>, T, VH extends CustomViewHolder<VH, LVA, P, T, MSL>> implements MultiSelectStatusListener<MSL,LVA, P,T,VH> {

        @Override
        public void onMultiSelectStatusChanged(LVA adapter, boolean multiSelectEnabled) {

        }

        @Override
        public void onItemSelectionCountChanged(LVA adapter, int size) {

        }

        @Override
        public void onItemDeleteRequested(LVA adapter, T g) {

        }

        @Override
        public void onItemClick(LVA adapter, T item) {

        }

        @Override
        public void onItemLongClick(LVA adapter, T item) {

        }

        @Override
        public void onDisabledItemClick(LVA adapter, T item) {

        }
    }

    public static class ItemSelectionListener<LVA extends BaseRecyclerViewAdapter<LVA, P, T, VH, MSL>, VH extends CustomViewHolder<VH, LVA, P, T, MSL>, P extends BaseRecyclerViewAdapterPreferences<P>, T, MSL extends BaseRecyclerViewAdapter.MultiSelectStatusListener<MSL,LVA,P,T,VH>> implements CompoundButton.OnCheckedChangeListener {

        private final VH holder;
        private final LVA adapter;

        public ItemSelectionListener(LVA adapter, VH holder) {
            this.adapter = adapter;
            this.holder = holder;
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            boolean changed = false;
            if (isChecked) {
                changed = adapter.selectedResourceIds.add(holder.getItemId());
                if (adapter.selectedResourceIds.size() > 1 && !adapter.isMultiSelectionAllowed()) {
                    changed |= deselectAllOtherItems();
                }
            } else {
                if (adapter.selectedResourceIds.contains(holder.getItemId())) {
                    if (adapter.isAllowItemDeselection(holder.getItemId())) {
                        changed = adapter.selectedResourceIds.remove(holder.getItemId());
                    } else {
                        // re-check the button.
                        buttonView.setChecked(true);
                    }
                }
            }
            if (changed) {
                adapter.multiSelectStatusListener.onItemSelectionCountChanged(adapter, adapter.selectedResourceIds.size());
            }
        }

        private boolean deselectAllOtherItems() {
            boolean changed = false;
            Iterator<Long> iter = adapter.selectedResourceIds.iterator();
            while (iter.hasNext()) {
                Long selectedId = iter.next();
                if (!selectedId.equals(holder.getItemId()) && adapter.isAllowItemDeselection(selectedId)) {
                    iter.remove();
                    adapter.notifyItemChanged(adapter.getItemPosition(adapter.getItemById(selectedId)));
                    changed = true;
                }
            }
            return changed;
        }
    }

}
