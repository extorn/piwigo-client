package delit.piwigoclient.ui.dialogs;

import android.content.Context;
import android.content.DialogInterface;
import android.view.ContextThemeWrapper;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.ui.album.listSelect.AvailableAlbumsListAdapter;
import delit.piwigoclient.ui.permissions.AlbumSelectionListAdapterPreferences;

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
        AlbumSelectionListAdapterPreferences viewPrefs = new AlbumSelectionListAdapterPreferences();
        viewPrefs.selectable(false, false);
        viewPrefs.setFlattenAlbumHierarchy(true);
        availableGalleries = new AvailableAlbumsListAdapter(viewPrefs, parentAlbum, context);

        this.availableGalleries.clear();
        this.availableGalleries.addAll(albumNames);

        MaterialAlertDialogBuilder builder1 = new MaterialAlertDialogBuilder(new ContextThemeWrapper(context, R.style.Theme_App_EditPages));
        builder1.setTitle(R.string.alert_title_select_album);
//        builder1.setMessage(R.string.alert_message_select_album);
        int selectedPosition = availableGalleries.getPosition(defaultSelectedAlbumId);
        if (selectedPosition >= 0) {
            selectedAlbumId = availableGalleries.getItem(selectedPosition);
        }
        builder1.setSingleChoiceItems(availableGalleries, selectedPosition, (dialog, which) -> selectedAlbumId = availableGalleries.getItem(which));
        builder1.setPositiveButton(
                R.string.button_ok,
                (dialog, id) -> {
                    if (selectedAlbumId != null) {
                        dialog.cancel();
                        positiveActionListener.onClick(dialog, id);
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
