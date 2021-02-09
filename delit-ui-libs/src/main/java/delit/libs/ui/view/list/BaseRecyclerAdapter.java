package delit.libs.ui.view.list;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public abstract class BaseRecyclerAdapter<T, S extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<S>{

    private final @LayoutRes int itemLayoutId;
    private LayoutInflater inflater;
//    private Context context;
    private List<T> items;

    public BaseRecyclerAdapter(Context context, List<T> items, @LayoutRes int itemLayoutId) {
        inflater = LayoutInflater.from(context);
//        this.context = context;
        this.items = items;
        this.itemLayoutId = itemLayoutId;
    }

    @NonNull
    @Override
    public S onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(itemLayoutId, parent, false);
        return buildViewHolder(view);
    }

    protected T getItemByPosition(int position) {
        return items.get(position);
    }

    protected abstract S buildViewHolder(View view);

    @Override
    public void onBindViewHolder(@NonNull S holder, int position) {
             onBindViewHolder(holder, items.get(position));
    }

    protected abstract void onBindViewHolder(S viewHolder, T item);

    @Override
    public int getItemCount() {
        return items.size();
    }
}