package delit.piwigoclient.ui.common;

import android.content.Context;
import android.support.annotation.IdRes;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.LongSparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;

/**
 * Created by gareth on 13/06/17.
 */

public abstract class CustomSelectListAdapter<P extends BaseRecyclerViewAdapterPreferences, T> extends ArrayAdapter<T> implements Enableable {

    private final P prefs;
    private LongSparseArray<Integer> idPositionMap;
    private boolean enabled = true;

    protected CustomSelectListAdapter(@NonNull Context context, P prefs, @LayoutRes int resource) {
        this(context, prefs, resource, 0);
    }

    protected CustomSelectListAdapter(@NonNull Context context, P prefs, @LayoutRes int resource, @IdRes int txtViewId) {
        super(context, resource, txtViewId);
        this.prefs = prefs;
    }

    public P getPrefs() {
        return prefs;
    }

    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View aView = super.getDropDownView(position, convertView, parent);
        if(idPositionMap == null) {
            idPositionMap = buildIdPositionMap();
        }
        setViewData(position, aView, true);
        return aView;
    }

    @NonNull
    @Override
    public View getView(final int position, View view, @NonNull ViewGroup parent) {
        View aView = super.getView(position, view, parent);
        if(idPositionMap == null) {
            idPositionMap = buildIdPositionMap();
        }
//        aView.setEnabled(prefs.isAllowItemSelection());
//        aView.setClickable(prefs.isAllowItemSelection());
        setViewData(position, aView, false);
        return aView;
    }

    protected abstract void setViewData(int position, View aView, boolean isDropdown);

    protected abstract Long getItemId(T item);

    @Override
    public void notifyDataSetChanged() {
        idPositionMap = null;
        super.notifyDataSetChanged();
    }

    private LongSparseArray<Integer> buildIdPositionMap() {
        idPositionMap = new LongSparseArray<>();
        for (int i = 0; i < getCount(); i++) {
            T thisItem = getItem(i);
            idPositionMap.put(getItemId(thisItem), i);
        }
        return idPositionMap;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public long getItemId(int position) {
        int itemCount = getCount();
        if(itemCount > position) {
            T item = getItem(position);
            return getItemId(item);
        } else {
            throw new IllegalStateException("The adapter is out of sync with the screen for some reason. Wanted item at position " + position + " but there are only " + getCount() + " items in the adapter. The local id map contains " + (idPositionMap!=null?idPositionMap.size():0) + " items");
        }
    }

    public int getPosition(Long itemId) {
        if(itemId == null) {
            return -1;
        }
        if(idPositionMap == null) {
            idPositionMap = buildIdPositionMap();
        }
        Integer pos = idPositionMap.get(itemId);
        if(pos != null) {
            return pos;
        }
        return -1;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return enabled;
    }

    @Override
    public boolean isEnabled(int position) {
        return enabled;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public T getItemById(Long selectedId) {
        int pos = getPosition(selectedId);
        if(pos < 0) {
            return null;
        }
        return getItem(pos);
    }

}
