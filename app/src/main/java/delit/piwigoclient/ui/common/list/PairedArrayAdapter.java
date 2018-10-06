package delit.piwigoclient.ui.common.list;

import android.content.Context;
import android.support.annotation.ArrayRes;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

public abstract class PairedArrayAdapter<T> extends BaseAdapter {

    private final Context context;
    private final T[] data;
    private final int itemLayout;

    public PairedArrayAdapter(@NonNull Context context, @LayoutRes int itemLayout, @ArrayRes int dataResource) {
        this(context, itemLayout, (T[])context.getResources().getStringArray(dataResource));
    }

    public PairedArrayAdapter(@NonNull Context context, @LayoutRes int itemLayout, @NonNull T[] data) {
        this.context = context;
        this.data = data;
        this.itemLayout = itemLayout;
    }

    @Override
    public int getCount() {
        return data.length / 2;
    }

    /**
     * Will retrieve the headings
     *
     * @param position
     * @return
     */
    @Override
    public T getItem(int position) {
        return data[position * 2];
    }

    public T getItemData(int position) {
        return data[1 + (position * 2)];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        View view = convertView; // re-use an existing view, if one is supplied
        if (view == null) {
            view = inflateView(context, position, parent);
        }
        // set view properties to reflect data for the given row

        T heading = getItem(position);
        T data = getItemData(position);

        populateView(view, heading, data);

        // return the view, populated with data, for display
        return view;
    }

    protected View inflateView(Context context, int position, ViewGroup parent) {
        return LayoutInflater.from(context).inflate(itemLayout, parent, false);
    }

    public abstract void populateView(View view, T heading, T data);
}
