package delit.piwigoclient.ui.slideshow;

import android.support.v7.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import java.io.Serializable;
import java.util.ArrayList;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.ResourceItem;

/**
 * Created by gareth on 29/10/17.
 */

class DownloadSelectionDialog {
    private final Context context;

    public DownloadSelectionDialog(Context context) {
        this.context = context;
    }

    public AlertDialog buildDialog(final String resourceName, ArrayList<ResourceItem.ResourceFile> availableFiles, final DownloadSelectionListener listener) {
        AlertDialog.Builder builder1 = new AlertDialog.Builder(context);
        builder1.setTitle(R.string.alert_image_download_title);
        final DownloadItemsListAdapter adapter = new DownloadItemsListAdapter(context, R.layout.select_dialog_singlechoice_compressed, availableFiles);

        View view = LayoutInflater.from(context).inflate(R.layout.download_dialog_layout, null, false);
        ((TextView) view.findViewById(R.id.alertMessage)).setText(R.string.alert_image_download_message);
        final ListView fileSelectList = view.findViewById(R.id.fileSelectList);
        fileSelectList.setAdapter(adapter);
        fileSelectList.setItemChecked(adapter.getCount() - 1, true);
        builder1.setView(view);
//                builder1.setSingleChoiceItems(adapter, adapter.getCount() - 1, null);
        builder1.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        builder1.setNeutralButton(R.string.button_copy_link, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int pos = fileSelectList.getCheckedItemPosition();
                ResourceItem.ResourceFile selectedItem = adapter.getItem(pos);
                Uri uri = Uri.parse(selectedItem.getUrl());
                ClipboardManager mgr = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clipData = ClipData.newRawUri(context.getString(R.string.download_link_clipboard_data_desc, resourceName), uri);
                mgr.setPrimaryClip(clipData);
            }
        });
        builder1.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
//                        int pos = ((AlertDialog)dialog).getListView().getCheckedItemPosition();
                int pos = fileSelectList.getCheckedItemPosition();
                ResourceItem.ResourceFile selectedItem = adapter.getItem(pos);
                listener.onSelection(selectedItem);
            }
        });
        builder1.setCancelable(true);
        return builder1.create();
    }

    public interface DownloadSelectionListener extends Serializable {
        void onSelection(ResourceItem.ResourceFile selectedItem);
    }
}
