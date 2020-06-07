package delit.libs.ui.view.list;

import android.database.DataSetObserver;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ListAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.List;

public class NonFilteringAdapterWrapper<T extends BaseAdapter> implements ListAdapter, Filterable {
    protected T delegate;

    public NonFilteringAdapterWrapper(@NonNull T delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return delegate.areAllItemsEnabled();
    }

    @Override
    public boolean isEnabled(int position) {
        return delegate.isEnabled(position);
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        delegate.registerDataSetObserver(observer);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        delegate.unregisterDataSetObserver(observer);
    }

    @Override
    public int getCount() {
        return delegate.getCount();
    }

    @Override
    public Object getItem(int position) {
        return delegate.getItem(position);
    }

    @Override
    public long getItemId(int position) {
        return delegate.getItemId(position);
    }

    @Override
    public boolean hasStableIds() {
        return delegate.hasStableIds();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return delegate.getView(position, convertView, parent);
    }

    @Override
    public int getItemViewType(int position) {
        return delegate.getItemViewType(position);
    }

    @Override
    public int getViewTypeCount() {
        return delegate.getViewTypeCount();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Nullable
    @Override
    public CharSequence[] getAutofillOptions() {
        return delegate.getAutofillOptions();
    }

    @Override
    public Filter getFilter() {
        return new NonFilteringFilter();
    }


    private class NonFilteringFilter extends Filter {

        @Override
        public FilterResults performFiltering(CharSequence constraint) {

            List<Object> values = new ArrayList<>(getCount());
            for(int i = 0; i < getCount(); i++) {
                values.add(getItem(i));
            }

            FilterResults results = new FilterResults();
            results.count = getCount();
            results.values = values;
            return results;
        }

        @Override
        public void publishResults(CharSequence constraint, FilterResults results) {
            // use the one in the delegated filter
            delegate.notifyDataSetChanged();
        }
    }
}
