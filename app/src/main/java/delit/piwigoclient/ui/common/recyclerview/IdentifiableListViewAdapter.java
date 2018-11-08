package delit.piwigoclient.ui.common.recyclerview;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;

import java.util.HashSet;

import delit.piwigoclient.model.piwigo.Identifiable;
import delit.piwigoclient.model.piwigo.IdentifiableItemStore;

/**
 * {@link RecyclerView.Adapter} that can display a {@link T}
 */
public abstract class IdentifiableListViewAdapter<P extends BaseRecyclerViewAdapterPreferences, T extends Identifiable, V extends IdentifiableItemStore<T>, S extends CustomViewHolder<P, T>> extends BaseRecyclerViewAdapter<P, T, S> {

    private final V itemStore;


    public IdentifiableListViewAdapter(final V itemStore, MultiSelectStatusListener multiSelectStatusListener, P prefs) {
        super(multiSelectStatusListener, prefs);
        this.itemStore = itemStore;
    }

    @Override
    public long getItemId(int position) {
        return itemStore.getItemByIdx(position).getId();
    }

    @NonNull
    @Override
    public abstract S buildViewHolder(View view, int viewType);

    @Override
    protected void removeItemFromInternalStore(int idxRemoved) {
        if (idxRemoved >= 0 && idxRemoved < itemStore.getItemCount()) {
            itemStore.getItems().remove(idxRemoved);
        }
    }

    protected V getItemStore() {
        return itemStore;
    }

    @Override
    public HashSet<Long> getItemsSelectedButNotLoaded() {
        HashSet<Long> loadedSelectedItemIds = new HashSet<>(getSelectedItemIds());
        for (T group : itemStore.getItems()) {
            loadedSelectedItemIds.remove(group.getId());
        }
        return loadedSelectedItemIds;
    }

    @Override
    protected void replaceItemInInternalStore(int idxToReplace, T newItem) {
        itemStore.getItems().remove(idxToReplace);
        itemStore.getItems().add(idxToReplace, newItem);
    }

    @Override
    protected T getItemFromInternalStoreMatching(T item) {
        return itemStore.getItemById(item.getId());
    }

    @Override
    protected void addItemToInternalStore(T item) {
        itemStore.addItem(item);
    }

    @Override
    public T getItemByPosition(int position) {
        return itemStore.getItemByIdx(position);
    }

    @Override
    public boolean isHolderOutOfSync(S holder, T newItem) {
        return !(holder.getOldPosition() < 0 && holder.getItem() != null && holder.getItem().getId() == newItem.getId());
    }

    @Override
    public int getItemCount() {
        return itemStore.getItemCount();
    }

    @Override
    public T getItemById(Long selectedId) {
        return itemStore.getItemById(selectedId);
    }

    @Override
    public int getItemPosition(T item) {
        return itemStore.getItemIdx(item);
    }

}
