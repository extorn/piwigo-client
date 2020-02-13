package delit.piwigoclient.ui.slideshow;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import java.io.Serializable;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.AbstractBaseResourceItem;
import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.model.piwigo.ResourceItem;

/**
 * Created by gareth on 29/10/17.
 */

class DownloadSelectionDialog {
    private final Context context;
    private RadioGroup downloadOptionGroup;
    private DownloadSelectionListener downloadSelectionListener;
    private AlertDialog dialog;
    private PictureResourceItem item;

    public DownloadSelectionDialog(Context context) {
        this.context = context;
    }

    public AlertDialog buildDialog(final String resourceName, String currentImageUrlDisplayed, final PictureResourceItem item, final DownloadSelectionListener downloadSelectionListener) {
        this.downloadSelectionListener = downloadSelectionListener;
        final AlertDialog.Builder builder1 = new AlertDialog.Builder(context);
        builder1.setTitle(R.string.alert_image_download_title);
        this.item = item;
        final DownloadItemsListAdapter adapter = new DownloadItemsListAdapter(context, R.layout.layout_dialog_select_singlechoice_compressed, item);

        View view = LayoutInflater.from(context).inflate(R.layout.layout_dialog_download_file, null, false);
        ((TextView) view.findViewById(R.id.alertMessage)).setText(R.string.alert_image_download_message);
        downloadOptionGroup = view.findViewById(R.id.download_option);
        downloadOptionGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
            }
        });
        final ListView fileSelectList = view.findViewById(R.id.fileSelectList);
        fileSelectList.setAdapter(adapter);

        int defaultFileSelectionPos = adapter.getPosition(currentImageUrlDisplayed);
        if(defaultFileSelectionPos >= 0) {
            fileSelectList.setItemChecked(defaultFileSelectionPos, true);
        } else {
            fileSelectList.setItemChecked(adapter.getCount() - 1, true);
        }

        builder1.setView(view);
//                builder1.setSingleChoiceItems(adapter, adapter.getCount() - 1, null);
        builder1.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });

        builder1.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
//                        int pos = ((AlertDialog)dialog).getListView().getCheckedItemPosition();
                int pos = fileSelectList.getCheckedItemPosition();
                ResourceItem.ResourceFile selectedItem = adapter.getItem(pos);


                switch(downloadOptionGroup.getCheckedRadioButtonId()) {
                    case R.id.download_option_copy_link:
                        onCopyLink(selectedItem, resourceName);
                        break;
                    case R.id.download_option_download_file:
                        onDownloadFile(selectedItem, resourceName);
                        break;
                    case R.id.download_option_share_with_an_app:
                        onShareFile(selectedItem, resourceName);
                        break;
                    default:

                        // no selection
                }

            }
        });
        builder1.setCancelable(true);
        dialog = builder1.create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                ((AlertDialog)dialog).getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
            }
        });
        return dialog;
    }

    private void onShareFile(AbstractBaseResourceItem.ResourceFile selectedItem, String resourceName) {
        downloadSelectionListener.onShare(selectedItem, resourceName);
    }

    private void onDownloadFile(AbstractBaseResourceItem.ResourceFile selectedItem, String resourceName) {
        downloadSelectionListener.onDownload(selectedItem, resourceName);
    }

    private void onCopyLink(AbstractBaseResourceItem.ResourceFile selectedItem, String resourceName) {
        Uri uri = Uri.parse(item.getFileUrl(selectedItem.getName()));
        ClipboardManager mgr = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = ClipData.newRawUri(context.getString(R.string.download_link_clipboard_data_desc, resourceName), uri);
        mgr.setPrimaryClip(clipData);
        downloadSelectionListener.onCopyLink(selectedItem, resourceName);
    }

    public interface DownloadSelectionListener extends Serializable {
        void onDownload(ResourceItem.ResourceFile selectedItem, String resourceName);
        void onShare(ResourceItem.ResourceFile selectedItem, String resourceName);

        void onCopyLink(AbstractBaseResourceItem.ResourceFile selectedItem, String resourceName);
    }
}
