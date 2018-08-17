package delit.piwigoclient.ui.slideshow;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import delit.piwigoclient.model.piwigo.ExifDataItem;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.common.recyclerview.CustomViewHolder;

public class ExifDataViewHolder extends CustomViewHolder<BaseRecyclerViewAdapterPreferences, ExifDataItem> {
    public ExifDataViewHolder(View view) {
        super(view);
    }

    @Override
    public void fillValues(Context context, ExifDataItem item, boolean allowItemDeletion) {
        setItem(item);
            ((TextView)itemView).setText(item.getValue());
    }

    @Override
    public void cacheViewFieldsAndConfigure() {
        // no need as is just a text field
    }

    @Override
    public void setChecked(boolean checked) {
        throw new UnsupportedOperationException("add checkbox etc for this");
    }
}
