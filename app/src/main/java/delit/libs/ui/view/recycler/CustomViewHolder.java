package delit.libs.ui.view.recycler;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

public abstract class CustomViewHolder<V extends BaseRecyclerViewAdapterPreferences, T> extends RecyclerView.ViewHolder {

    private T item;
    private CustomClickListener itemActionListener;

    public CustomViewHolder(View view) {
        super(view);
    }

    public abstract void fillValues(Context context, T item, boolean allowItemDeletion);

    public abstract void cacheViewFieldsAndConfigure();

    public abstract void setChecked(boolean checked);

    public T getItem() {
        return item;
    }

    public void setItem(T item) {
        this.item = item;
    }

    public void internalCacheViewFieldsAndConfigure(CustomClickListener<V, T, ? extends CustomViewHolder<V, T>> itemActionListener) {
        this.itemActionListener = itemActionListener;
        itemView.setOnClickListener(itemActionListener);
        itemView.setOnLongClickListener(itemActionListener);
        cacheViewFieldsAndConfigure();
    }

    public CustomClickListener getItemActionListener() {
        return itemActionListener;
    }

    public <S> void redisplayOldValues(Context context, S newItem, boolean allowItemDeletion) {
    }
}