package delit.piwigoclient.ui.slideshow;

import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.model.piwigo.ResourceItem;

/**
 * Created by gareth on 29/10/17.
 */

class SelectImageRenderDetailsDialog {
    private final Context context;
    private int[] rotationValues;
    private Spinner imageRotation;
    private SwitchMaterial maxZoomPicker;
    private DownloadItemsListAdapter adapter;
    private ListView fileSelectList;

    public SelectImageRenderDetailsDialog(Context context) {
        this.context = new ContextThemeWrapper(context, R.style.Theme_App_EditPages);
    }

    public AlertDialog buildDialog(String currentImageUrlDisplayed, final PictureResourceItem model, final RenderDetailSelectListener listener) {
        MaterialAlertDialogBuilder builder1 = new MaterialAlertDialogBuilder(new android.view.ContextThemeWrapper(context, R.style.Theme_App_EditPages));
        builder1.setTitle(R.string.alert_image_show_image_title);
        adapter = new DownloadItemsListAdapter(context, R.layout.layout_dialog_select_singlechoice_compressed, model);

        View view = LayoutInflater.from(builder1.getContext()).inflate(R.layout.layout_dialog_zoom_control, null, false);

        imageRotation = view.findViewById(R.id.imageRotationField);

        imageRotation.setAdapter(ArrayAdapter.createFromResource(context, R.array.rotation_array, R.layout.support_simple_spinner_dropdown_item));
        rotationValues = context.getResources().getIntArray(R.array.rotation_values_array);
        for (int i = 0; i < rotationValues.length; i++) {
            if (rotationValues[i] == 0) {
                imageRotation.setSelection(i);
                break;
            }
        }
        maxZoomPicker = view.findViewById(R.id.allowFreeZoom);
        fileSelectList = view.findViewById(R.id.fileSelectList);
        fileSelectList.setAdapter(adapter);
        int defaultFileSelectionPos = adapter.getPosition(currentImageUrlDisplayed);
        if(defaultFileSelectionPos >= 0) {
            fileSelectList.setItemChecked(defaultFileSelectionPos, true);
        }
        builder1.setView(view);
        builder1.setNegativeButton(R.string.button_cancel, null);
        builder1.setPositiveButton(R.string.button_ok, (dialog, which) -> {
            if(fileSelectList.getCheckedItemCount() > 0) {
                listener.onSelection(model.getFileUrl(getSelectedFile().getName()), getRotateDegrees(), maxZoomPicker.isChecked() ? 100 : 3);
            }
        });
        builder1.setCancelable(true);
        return builder1.create();
    }

    private float getRotateDegrees() {
        return rotationValues[imageRotation.getSelectedItemPosition()];
    }

    private ResourceItem.ResourceFile getSelectedFile() {
        return adapter.getItem(fileSelectList.getCheckedItemPosition());
    }

    public interface RenderDetailSelectListener {
        void onSelection(String selectedUrl, float rotateDegrees, float maxZoom);
    }
}
