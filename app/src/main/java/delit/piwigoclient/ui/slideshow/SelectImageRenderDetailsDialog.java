package delit.piwigoclient.ui.slideshow;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.NumberPicker;
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
    private NumberPicker maxZoomPicker;
    private DownloadItemsListAdapter adapter;
    private ListView fileSelectList;

    public SelectImageRenderDetailsDialog(Context context) {
        this.context = context;
    }

    public interface RenderDetailSelectListener extends Serializable {
        void onSelection(String selectedUrl, float rotateDegrees, float maxZoom);
    }

    public AlertDialog buildDialog(float currentMaxZoom, String currentImageUrlDisplayed, ArrayList<ResourceItem.ResourceFile> availableFiles, final RenderDetailSelectListener listener) {
        android.app.AlertDialog.Builder builder1 = new android.app.AlertDialog.Builder(context);
        builder1.setTitle(R.string.alert_image_show_image_title);
        adapter = new DownloadItemsListAdapter(context, R.layout.select_dialog_singlechoice_compressed, availableFiles);

        View view = LayoutInflater.from(context).inflate(R.layout.zoom_control_dialog_layout, null, false);

        imageRotation = view.findViewById(R.id.imageRotationField);

        imageRotation.setAdapter(ArrayAdapter.createFromResource(context, R.array.rotation_array, R.layout.dark_spinner_item));
        rotationValues = context.getResources().getIntArray(R.array.rotation_values_array);
        for(int i = 0; i < rotationValues.length; i++) {
            if(rotationValues[i] == 0) {
                imageRotation.setSelection(i);
                break;
            }
        }
        maxZoomPicker = view.findViewById(R.id.maxZoomValueField);
        maxZoomPicker.setMaxValue(100);
        maxZoomPicker.setMinValue(3);
        maxZoomPicker.setValue((int)Math.rint(currentMaxZoom));
        maxZoomPicker.setWrapSelectorWheel(true);
        fileSelectList = view.findViewById(R.id.fileSelectList);
        fileSelectList.setAdapter(adapter);
        AdapterView.OnItemClickListener itemClickListener = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                int selectPos = Math.min(Math.max(0, position), adapter.getCount() -1);

                float rotateDegrees = 0f;

                if(selectPos >= 0) {

                    ResourceItem.ResourceFile selectedItem = adapter.getItem(selectPos);

                    if (adapter.getCount() >= 2 && selectedItem.getName().equals("original")) {
                        int i = 0;
                        ResourceItem.ResourceFile rf = adapter.getItem(i);
                        while (rf.getName().equals("square") || rf.getName().equals("original")) {
                            rf = adapter.getItem(++i);
                            if (i == adapter.getCount()) {
                                rf = null;
                                break;
                            }
                        }
                        if (rf != null) {
                            boolean oldIsLandscape = rf.getWidth() > rf.getHeight();
                            boolean newIsLandscape = selectedItem.getWidth() > selectedItem.getHeight();

                            if (newIsLandscape && !oldIsLandscape) {
                                rotateDegrees = -90f;
                            } else if (!newIsLandscape && oldIsLandscape) {
                                rotateDegrees = 90f;
                            }
                        }
                    }
                }
                int i = 0;
                for (int rotateDegreeOption : rotationValues) {
                    if (0 == Float.compare(rotateDegrees, (float) rotateDegreeOption)) {
                        imageRotation.setSelection(i);
                        break;
                    }
                    i++;
                }
            }
        };
        fileSelectList.setOnItemClickListener(itemClickListener);
        int defaultFileSelectionPos = adapter.getPosition(currentImageUrlDisplayed);
        fileSelectList.setItemChecked(defaultFileSelectionPos, true);
        itemClickListener.onItemClick(fileSelectList, null, defaultFileSelectionPos, -1);
        builder1.setView(view);
        builder1.setNegativeButton(R.string.button_cancel, null);
        builder1.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                listener.onSelection(getSelectedFile().getUrl(), getRotateDegrees(), getMaxZoom());
            }
        });
        builder1.setCancelable(true);
        return builder1.create();
    }

    private float getRotateDegrees() {
         return rotationValues[imageRotation.getSelectedItemPosition()];
    }

    private float getMaxZoom() {
        return (float)maxZoomPicker.getValue();
    }

    private ResourceItem.ResourceFile getSelectedFile() {
        return adapter.getItem(fileSelectList.getCheckedItemPosition());
    }
}
