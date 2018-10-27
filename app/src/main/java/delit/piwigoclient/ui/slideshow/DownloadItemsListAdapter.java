package delit.piwigoclient.ui.slideshow;

import android.content.Context;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.widget.ArrayAdapter;

import java.util.List;

import delit.piwigoclient.model.piwigo.ResourceItem;

/**
 * Created by gareth on 14/06/17.
 */

public class DownloadItemsListAdapter extends ArrayAdapter<ResourceItem.ResourceFile> {
    public DownloadItemsListAdapter(@NonNull Context context, @LayoutRes int resource, @NonNull List<ResourceItem.ResourceFile> objects) {
        super(context, resource, objects);
    }

    public int getPosition(@NonNull String urlSought) {
        for (int i = 0; i < getCount(); i++) {
            if (urlSought.equals(getItem(i).getUrl())) {
                return i;
            }
        }
        return -1;
    }
}
