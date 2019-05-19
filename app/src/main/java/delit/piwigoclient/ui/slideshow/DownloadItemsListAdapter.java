package delit.piwigoclient.ui.slideshow;

import android.content.Context;
import android.widget.ArrayAdapter;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;

import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.model.piwigo.ResourceItem;

/**
 * Created by gareth on 14/06/17.
 */

public class DownloadItemsListAdapter extends ArrayAdapter<ResourceItem.ResourceFile> {
    private final PictureResourceItem item;

    public DownloadItemsListAdapter(@NonNull Context context, @LayoutRes int resource, @NonNull PictureResourceItem item) {
        super(context, resource, item.getAvailableFiles());
        this.item = item;
    }

    public int getPosition(@NonNull String urlSought) {
        for (int i = 0; i < getCount(); i++) {
            if (urlSought.equals(item.getFileUrl(getItem(i).getName()))) {
                return i;
            }
        }
        return -1;
    }
}
