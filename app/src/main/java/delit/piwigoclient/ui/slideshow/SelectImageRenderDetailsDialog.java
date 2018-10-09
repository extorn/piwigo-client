package delit.piwigoclient.ui.slideshow;

import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.SwitchCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;

import java.io.Serializable;
import java.util.ArrayList;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.ResourceItem;

/**
 * Created by gareth on 29/10/17.
 */

class SelectImageRenderDetailsDialog {
    private final Context context;
    private int[] rotationValues;
    private Spinner imageRotation;
    private SwitchCompat maxZoomPicker;
    private DownloadItemsListAdapter adapter;
    private ListView fileSelectList;

    public SelectImageRenderDetailsDialog(Context context) {
        this.context = context;
    }

    public AlertDialog buildDialog(String currentImageUrlDisplayed, ArrayList<ResourceItem.ResourceFile> availableFiles, final RenderDetailSelectListener listener) {
        android.support.v7.app.AlertDialog.Builder builder1 = new android.support.v7.app.AlertDialog.Builder(context);
        builder1.setTitle(R.string.alert_image_show_image_title);
        adapter = new DownloadItemsListAdapter(context, R.layout.layout_dialog_select_singlechoice_compressed, availableFiles);

        View view = LayoutInflater.from(context).inflate(R.layout.layout_dialog_zoom_control, null, false);

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
        builder1.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if(fileSelectList.getCheckedItemCount() > 0) {
                    listener.onSelection(getSelectedFile().getUrl(), getRotateDegrees(), maxZoomPicker.isChecked() ? 100 : 3);
                }
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

    public interface RenderDetailSelectListener extends Serializable {
        void onSelection(String selectedUrl, float rotateDegrees, float maxZoom);
    }
}
