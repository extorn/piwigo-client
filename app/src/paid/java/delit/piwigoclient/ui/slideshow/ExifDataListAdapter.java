package delit.piwigoclient.ui.slideshow;

import android.support.annotation.NonNull;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

import delit.piwigoclient.model.piwigo.ExifDataItem;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapter;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.common.recyclerview.CustomViewHolder;

public class ExifDataListAdapter extends BaseRecyclerViewAdapter<BaseRecyclerViewAdapterPreferences, ExifDataItem, CustomViewHolder<BaseRecyclerViewAdapterPreferences, ExifDataItem>> {

    private ArrayList<ExifDataItem> data  = new ArrayList<>();

    public ExifDataListAdapter(MultiSelectStatusListener multiSelectStatusListener, BaseRecyclerViewAdapterPreferences prefs) {
        super(multiSelectStatusListener, prefs);
    }

    public void setData(Collection<ExifDataItem> data) {
        this.data.clear();
        this.data.addAll(data);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        return position % 2 == 0 ? 0 : 1;
    }

    @Override
    public CustomViewHolder<BaseRecyclerViewAdapterPreferences, ExifDataItem> buildViewHolder(View view, int viewType) {
        switch(viewType) {
            case 0:
                return new ExifDataLabelViewHolder(view);
            case 1:
                return new ExifDataViewHolder(view);
        }
        throw new IllegalArgumentException("Unrecognised view Type " + viewType);
    }

    @NonNull
    @Override
    protected View inflateView(@NonNull ViewGroup parent, int viewType) {
        switch(viewType) {
            case 0:
                return new TextView(getContext());
            case 1:
                TextView tv = new TextView(getContext());
                //TODO sort multiline layout etc etc
                return tv;
        }
        throw new IllegalArgumentException("Unrecognised view Type " + viewType);
    }

    @Override
    protected ExifDataItem getItemById(Long selectedId) {
        if(selectedId == null) {
            return null;
        }
        return data.get(selectedId.intValue());
    }

    @Override
    public int getItemPosition(ExifDataItem item) {
        return getPositionFromDataIdx(data.indexOf(item));
    }

    @Override
    protected void removeItemFromInternalStore(int idxToRemove) {
        data.remove(getDataIdxFromPosition(idxToRemove));
    }

    private int getPositionFromDataIdx(int dataIdx) {
        return dataIdx * 2;
    }

    private int getDataIdxFromPosition(int position) {
        return position / 2;
    }

    @Override
    protected void replaceItemInInternalStore(int idxToReplace, ExifDataItem newItem) {
        int dataIdx = getDataIdxFromPosition(idxToReplace);
        data.remove(dataIdx);
        data.add(dataIdx, newItem);
    }

    @Override
    protected ExifDataItem getItemFromInternalStoreMatching(ExifDataItem item) {
        return item;
    }

    @Override
    protected void addItemToInternalStore(ExifDataItem item) {
        data.add(item);
    }

    @Override
    public ExifDataItem getItemByPosition(int position) {
        return data.get(getDataIdxFromPosition(position));
    }

    @Override
    public boolean isHolderOutOfSync(CustomViewHolder<BaseRecyclerViewAdapterPreferences, ExifDataItem> holder, ExifDataItem newItem) {
        return isDirtyItemViewHolder(holder) || !(getItemPosition(holder.getItem()) == getItemPosition(newItem));
    }

    @Override
    public HashSet<Long> getItemsSelectedButNotLoaded() {
        return null;
    }

    @Override
    public int getItemCount() {
        return getPositionFromDataIdx(data.size());
    }
}
