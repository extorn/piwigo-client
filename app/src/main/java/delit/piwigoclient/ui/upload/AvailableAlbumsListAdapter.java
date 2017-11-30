package delit.piwigoclient.ui.upload;

import android.content.Context;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.ui.common.CustomSelectListAdapter;

/**
 * Created by gareth on 13/06/17.
 */

public class AvailableAlbumsListAdapter extends CustomSelectListAdapter<CategoryItemStub> {

    private final CategoryItem parentAlbum;

    public AvailableAlbumsListAdapter(CategoryItem parentAlbum, @NonNull Context context, @LayoutRes int resource) {
        super(context, resource);
        this.parentAlbum = parentAlbum;
    }

    @NonNull
    @Override
    public View getView(int position, View view, @NonNull ViewGroup parent) {
        TextView v = (TextView) super.getView(position, view, parent);
        v.setPadding(0,0,0,0);
        return v;
    }

    @Override
    protected void setViewData(int position, View aView, boolean isDropdown) {
        try {
            TextView text = (TextView) aView;
            final CategoryItemStub item = getItem(position);
            if (parentAlbum.getId() != item.getId() || position > 0) {
                // only display items that are not the root.
                String prefix = "";
                if(isDropdown) {
                    prefix = getDepthPrefix(item);
                }
                text.setText(prefix + item.getName());
            }
        } catch (ClassCastException e) {
            Log.e("ArrayAdapter", "You must supply a resource ID for a TextView");
            throw new IllegalStateException(
                    "ArrayAdapter requires the resource ID to be a TextView", e);
        }
    }

    @Override
    protected Long getItemId(CategoryItemStub item) {
        return item.getId();
    }


    private String getDepthPrefix(CategoryItemStub item) {

        int depth = getDepth(item);
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < depth; i++) {
            sb.append("    ");
        }
        return sb.toString();
    }

    private int getDepth(CategoryItemStub item) {
        CategoryItemStub thisItem = item;
        int pos = getPosition(thisItem.getId());
        int depth = 0;
        while(pos >= 0) {
            pos = getPosition(thisItem.getParentId());
            if(pos >= 0) {
                depth++;
                thisItem = getItem(pos);
            }
        }
        return depth;
    }
}
