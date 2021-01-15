package delit.piwigoclient.ui.common.recyclerview;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashSet;

import delit.libs.ui.view.recycler.BaseRecyclerViewAdapter;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;
import delit.libs.ui.view.recycler.CustomViewHolder;
import delit.piwigoclient.model.piwigo.Identifiable;
import delit.piwigoclient.model.piwigo.IdentifiableItemStore;
import delit.piwigoclient.ui.model.ViewModelContainer;

/**
 * {@link RecyclerView.Adapter} that can display a {@link T}
 */
public abstract class IdentifiableListViewAdapter<LVA extends IdentifiableListViewAdapter<LVA,P,T, IS, VH, MSL>, P extends BaseRecyclerViewAdapterPreferences<P>, T extends Identifiable, IS extends IdentifiableItemStore<T>, VH extends CustomViewHolder<VH, LVA, P, T, MSL>, MSL extends BaseRecyclerViewAdapter.MultiSelectStatusListener<T>> extends BaseRecyclerViewAdapter<LVA, P, T, VH, MSL> {

    private final IS itemStore;
    private final Class<? extends ViewModelContainer> modelType;


    public IdentifiableListViewAdapter(Context context, final Class<? extends ViewModelContainer> modelType, final IS itemStore, MSL multiSelectStatusListener, P prefs) {
        super(multiSelectStatusListener, prefs);
        this.itemStore = itemStore;
        this.modelType = modelType;
    }

    public Class<ViewModelContainer> getModelType() {
        return (Class<ViewModelContainer>) modelType;
    }

    @Override
    public long getItemId(int position) {
        return itemStore.getItemByIdx(position).getId();
    }

    @NonNull
    @Override
    public abstract VH buildViewHolder(View view, int viewType);

    @Override
    protected void removeItemFromInternalStore(int idxRemoved) {
        if (idxRemoved >= 0 && idxRemoved < itemStore.getItemCount()) {
            itemStore.getItems().remove(idxRemoved);
        }
    }

    protected IS getItemStore() {
        return itemStore;
    }

    @Override
    public HashSet<Long> getItemsSelectedButNotLoaded() {
        HashSet<Long> loadedSelectedItemIds = new HashSet<>(getSelectedItemIds());
        for (T item : itemStore.getItems()) {
            loadedSelectedItemIds.remove(item.getId());
        }
        return loadedSelectedItemIds;
    }

    @Override
    protected void replaceItemInInternalStore(int idxToReplace, @NonNull T newItem) {
        itemStore.getItems().remove(idxToReplace);
        itemStore.getItems().add(idxToReplace, newItem);
    }

    @NonNull
    @Override
    protected T getItemFromInternalStoreMatching(@NonNull T item) {
        return itemStore.getItemById(item.getId());
    }

    @Override
    protected void addItemToInternalStore(@NonNull T item) {
        itemStore.addItem(item);
    }

    @NonNull
    @Override
    public T getItemByPosition(int position) {
        return itemStore.getItemByIdx(position);
    }

    @Override
    public boolean isHolderOutOfSync(VH holder, T newItem) {
        return isDirtyItemViewHolder(holder, newItem);
    }

    /**
     * @param holder
     * @return true if this holder has never been used before (or is totally clean)
     */
    @Override
    protected boolean isDirtyItemViewHolder(VH holder, T newItem) {
        return holder.getItem() == null || holder.getItem().getId() != newItem.getId();
    }

    @Override
    public int getItemCount() {
        return itemStore.getItemCount();
    }

    @Override
    public T getItemById(@NonNull Long selectedId) {
        return itemStore.getItemById(selectedId);
    }

    @Override
    public int getItemPosition(@NonNull T item) {
        return itemStore.getItemIdx(item);
    }


}
