package delit.piwigoclient.ui.dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

import java.util.ArrayList;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.ui.album.AvailableAlbumsListAdapter;

/**
 * Created by gareth on 31/08/17.
 */

public class SelectAlbumDialog {

    private final Context context;
    private final long defaultSelectedAlbumId;
    private AvailableAlbumsListAdapter availableGalleries;
    private CategoryItemStub selectedAlbumId;

    public SelectAlbumDialog(Context context, long defaultSelectedAlbumId) {
        this.context = context;
        this.defaultSelectedAlbumId = defaultSelectedAlbumId;
    }

    public AlertDialog buildDialog(ArrayList<CategoryItemStub> albumNames, CategoryItem parentAlbum, final DialogInterface.OnClickListener positiveActionListener) {
        AvailableAlbumsListAdapter.AvailableAlbumsListAdapterPreferences viewPrefs = new AvailableAlbumsListAdapter.AvailableAlbumsListAdapterPreferences();
        viewPrefs.selectable(false, false);
        viewPrefs.withShowHierachy();
        availableGalleries = new AvailableAlbumsListAdapter(viewPrefs, parentAlbum, context);

        this.availableGalleries.clear();
        this.availableGalleries.addAll(albumNames);

        AlertDialog.Builder builder1 = new AlertDialog.Builder(context);
        builder1.setCancelable(true);
        builder1.setTitle(R.string.alert_title_select_album);
//        builder1.setMessage(R.string.alert_message_select_album);
        int selectedPosition = availableGalleries.getPosition(defaultSelectedAlbumId);
        if(selectedPosition >= 0) {
            selectedAlbumId = availableGalleries.getItem(selectedPosition);
        }
        builder1.setSingleChoiceItems(availableGalleries, selectedPosition, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                selectedAlbumId = availableGalleries.getItem(which);
            }
        });
        builder1.setPositiveButton(
                R.string.button_ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        if(selectedAlbumId != null) {
                            dialog.cancel();
                            positiveActionListener.onClick(dialog, id);
                        }
                    }
                });
        return builder1.create();
    }

    public long getSelectedAlbumId() {
        return selectedAlbumId.getId();
    }

    public Long getSelectedAlbumParentId() {
        return selectedAlbumId.getParentId();
    }
}
