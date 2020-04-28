package delit.piwigoclient.ui.slideshow;

import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import delit.piwigoclient.R;
import delit.piwigoclient.business.video.RemoteAsyncFileCachingDataSource;
import delit.piwigoclient.model.piwigo.AbstractBaseResourceItem;
import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.VideoResourceItem;

/**
 * Created by gareth on 29/10/17.
 */

public class DownloadSelectionMultiItemDialog {
    private final Context context;
    private RadioGroup downloadOptionGroup;
    private DownloadSelectionMultiItemListener downloadSelectionListener;
    private AlertDialog dialog;

    public DownloadSelectionMultiItemDialog(Context context) {
        this.context = context;
    }

    public AlertDialog buildDialog(String defaultSelectedFilesizeName, final ResourceItem item, final DownloadSelectionMultiItemListener downloadSelectionListener) {
        HashSet<ResourceItem> items = new HashSet<>(1);
        items.add(item);
        List<String> availableFiles = new ArrayList<>(item.getAvailableFiles().size());
        for(AbstractBaseResourceItem.ResourceFile fileSize : item.getAvailableFiles()) {
            availableFiles.add(fileSize.getName());
        }
        return buildDialog(defaultSelectedFilesizeName, items, availableFiles, downloadSelectionListener);
    }

    public AlertDialog buildDialog(String defaultSelectedFilesizeName, Set<ResourceItem> itemsSelectedForDownload, final List<String> fileSizes, final DownloadSelectionMultiItemListener downloadSelectionListener) {
        this.downloadSelectionListener = downloadSelectionListener;
        final MaterialAlertDialogBuilder builder1 = new MaterialAlertDialogBuilder(context);
        builder1.setTitle(R.string.alert_image_download_title);

        Set<ResourceItem> itemsToDownload = new HashSet<>(itemsSelectedForDownload.size());
        itemsToDownload.addAll(itemsSelectedForDownload); // all items are left in the list at this time - copy remote URI link is always possible
        Set<ResourceItem> vidsUnableToDownload = new HashSet<>(0);
        for(ResourceItem item : itemsToDownload) {
            if(item instanceof VideoResourceItem) {
                VideoResourceItem vidItem = (VideoResourceItem)item;
                if(!RemoteAsyncFileCachingDataSource.isFileFullyCached(context, Uri.parse(vidItem.getFileUrl(AbstractBaseResourceItem.ResourceFile.ORIGINAL)))) {
                    vidsUnableToDownload.add(item);
                }
            }
        }

        final ArrayAdapter<String> adapter = new ArrayAdapter<>(context, R.layout.layout_dialog_select_singlechoice_compressed, fileSizes);

        View view = LayoutInflater.from(context).inflate(R.layout.layout_dialog_download_file, null, false);
        ((TextView) view.findViewById(R.id.alertMessage)).setText(R.string.alert_image_download_message);
        downloadOptionGroup = view.findViewById(R.id.download_option);
        downloadOptionGroup.setOnCheckedChangeListener((group, checkedId) -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true));
        final ListView fileSelectList = view.findViewById(R.id.fileSelectList);

        view.findViewById(R.id.download_option_copy_link).setVisibility(itemsToDownload.size() != 1 ? View.GONE : View.VISIBLE);
        if(vidsUnableToDownload.size() == itemsToDownload.size()) {
            view.findViewById(R.id.download_option_download_file).setVisibility(View.GONE);
            view.findViewById(R.id.download_option_share_with_an_app).setVisibility(View.GONE);
        }

        fileSelectList.setAdapter(adapter);

        int defaultFileSelectionPos = adapter.getPosition(defaultSelectedFilesizeName);
        if(defaultFileSelectionPos >= 0) {
//            Spinner
//            fileSelectList.setSelection(defaultFileSelectionPos, true);
//            List view
            fileSelectList.setItemChecked(defaultFileSelectionPos, true);
        } else {
            //            Spinner
//            fileSelectList.setSelection(adapter.getCount() - 1, true);
//            List view
            fileSelectList.setItemChecked(adapter.getCount() - 1, true);
        }

        builder1.setView(view);
        builder1.setNegativeButton(R.string.button_cancel, (dialog, which) -> {
        });

        builder1.setPositiveButton(R.string.button_ok, (dialog, which) -> {
            // spinner
//            int pos = fileSelectList.getSelectedItemPosition();
            // List view
            int pos = fileSelectList.getCheckedItemPosition();
            if(pos < 0) {
                throw new IllegalStateException("OK button pressed without a valid file size selected");
            }
            String selectedFilesizeName = adapter.getItem(pos);

            switch(downloadOptionGroup.getCheckedRadioButtonId()) {
                case R.id.download_option_copy_link:
                    onCopyLink(itemsToDownload, selectedFilesizeName);
                    break;
                case R.id.download_option_download_file:
                    itemsToDownload.removeAll(vidsUnableToDownload);
                    onDownloadFile(itemsToDownload, selectedFilesizeName, vidsUnableToDownload);
                    break;
                case R.id.download_option_share_with_an_app:
                    itemsToDownload.removeAll(vidsUnableToDownload);
                    onShareFile(itemsToDownload, selectedFilesizeName, vidsUnableToDownload);
                    break;
                default:
                    // no selection
            }

        });
        builder1.setCancelable(true);
        dialog = builder1.create();
        dialog.setOnShowListener(dialog -> ((AlertDialog)dialog).getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false));
        return dialog;
    }

    private Map<String, AbstractBaseResourceItem.ResourceFile> getFileLinks(Set<PictureResourceItem> items, String selectedFilesizeName) {
        Map<String, AbstractBaseResourceItem.ResourceFile> downloadLinks = new HashMap<>(items.size());
        for(PictureResourceItem item : items) {
            downloadLinks.put(item.getName(), item.getFile(selectedFilesizeName));
        }
        return downloadLinks;
    }

    private void onShareFile(Set<ResourceItem> items, String selectedPiwigoFilesizeName, Set<ResourceItem> filesUnavailableToDownload) {
        downloadSelectionListener.onShare(items, selectedPiwigoFilesizeName, filesUnavailableToDownload);
    }

    private void onDownloadFile(Set<ResourceItem> items, String selectedPiwigoFilesizeName, Set<ResourceItem> filesUnavailableToDownload) {
        downloadSelectionListener.onDownload(items, selectedPiwigoFilesizeName, filesUnavailableToDownload);
    }
//
    private void onCopyLink(Set<ResourceItem> items, String selectedPiwigoFilesizeName) {
        downloadSelectionListener.onCopyLink(context, items, selectedPiwigoFilesizeName);
    }

    public interface DownloadSelectionMultiItemListener extends Serializable {
        void onDownload(Set<ResourceItem> items, String selectedPiwigoFilesizeName, Set<ResourceItem> filesUnavailableToDownload);
        void onShare(Set<ResourceItem> items, String selectedPiwigoFilesizeName, Set<ResourceItem> filesUnavailableToDownload);
        void onCopyLink(Context context, Set<ResourceItem> items, String selectedPiwigoFilesizeName);
    }
}
