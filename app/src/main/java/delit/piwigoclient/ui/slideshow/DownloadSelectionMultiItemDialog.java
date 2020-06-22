package delit.piwigoclient.ui.slideshow;

import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import delit.libs.ui.view.list.MappedArrayAdapter;
import delit.libs.util.CollectionUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.video.RemoteAsyncFileCachingDataSource;
import delit.piwigoclient.model.piwigo.AbstractBaseResourceItem;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.VideoResourceItem;

/**
 * Created by gareth on 29/10/17.
 */

public class DownloadSelectionMultiItemDialog {
    private final Context context;
    private Spinner downloadOptionGroup;
    private DownloadSelectionMultiItemListener downloadSelectionListener;
    private AlertDialog dialog;
    private FilterItemsRequestedForDownload filterItemsRequestedForDownload;
    private MappedArrayAdapter<String, Integer> downloadActionsOptionsAdapter;
    private MappedArrayAdapter<AbstractBaseResourceItem.ResourceFile, String> downloadFileSizeOptionsAdapter;

    public DownloadSelectionMultiItemDialog(Context context) {
        this.context = new ContextThemeWrapper(context, R.style.Theme_App_EditPages);
    }

    public AlertDialog buildDialog(String defaultSelectedFilesizeName, final ResourceItem item, final DownloadSelectionMultiItemListener downloadSelectionListener) {
        HashSet<ResourceItem> items = new HashSet<>(1);
        items.add(item);
        return buildDialog(defaultSelectedFilesizeName, items, downloadSelectionListener);
    }

    public Context getContext() {
        return context;
    }

    public AlertDialog buildDialog(String defaultSelectedFilesizeName, Set<ResourceItem> itemsSelectedForDownload, final DownloadSelectionMultiItemListener downloadSelectionListener) {
        this.downloadSelectionListener = downloadSelectionListener;

        final MaterialAlertDialogBuilder builder1 = new MaterialAlertDialogBuilder(getContext());
        builder1.setTitle(R.string.alert_image_download_title);
        builder1.setView(R.layout.layout_dialog_download_file);
        builder1.setNegativeButton(R.string.button_cancel, (dialog, which) -> {});
        builder1.setPositiveButton(R.string.button_ok, (dialog, which) -> onOkSelected((AlertDialog)dialog));
        builder1.setCancelable(true);
        dialog = builder1.create();
        dialog.setOnShowListener(dialog -> {
            AlertDialog view = (AlertDialog)dialog;
            addDataToViewFields(view, itemsSelectedForDownload, defaultSelectedFilesizeName);
            view.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
        });

        filterItemsRequestedForDownload = new FilterItemsRequestedForDownload(itemsSelectedForDownload).invoke();
        Set<ResourceItem> itemsToDownload = filterItemsRequestedForDownload.getItemsToDownload();
        Set<ResourceItem> vidsUnableToDownload = filterItemsRequestedForDownload.getVidsUnableToDownload();
        downloadActionsOptionsAdapter = buildDownloadOptionsAdapter(dialog.getContext(), itemsToDownload, vidsUnableToDownload);

        return dialog;
    }

    private void onOkSelected(AlertDialog dialog) {
        final ListView fileSelectList = dialog.findViewById(R.id.fileSelectList);
        int pos = fileSelectList.getCheckedItemPosition();
        if(pos < 0) {
            throw new IllegalStateException("OK button pressed without a valid file size selected");
        }
        String selectedFilesizeName = downloadFileSizeOptionsAdapter.getItemValue(pos);

        String selectedItem = (String) downloadOptionGroup.getSelectedItem();
        int selectedAction = -1;
        if(selectedItem != null) {
            selectedAction = downloadActionsOptionsAdapter.getItemValueByItem(selectedItem);
        }
        onDownloadOptionsSelected(filterItemsRequestedForDownload, selectedFilesizeName, selectedAction);
    }

    /**
     * Examine data for each resource and find common file sizes available to download.
     * Note that videos will always be downloaded in original size.
     *
     * @param resourcesToDownload
     * @return
     */
    protected List<String> getFileSizesAvailableForAllResources(Set<ResourceItem> resourcesToDownload) {
        List<String> filesAvailableToDownload = null;
        for(ResourceItem item : resourcesToDownload) {
            if(!(item instanceof VideoResourceItem)) {
                List<String> availableSizesForResource = new ArrayList<>();
                for (ResourceItem.ResourceFile f : item.getAvailableFiles()) {
                    availableSizesForResource.add(f.getName());
                }
                if (filesAvailableToDownload == null) {
                    filesAvailableToDownload = new ArrayList<>(availableSizesForResource);
                } else {
                    CollectionUtils.removeItemsNotInRhsCollection(filesAvailableToDownload, availableSizesForResource);
                }
            }
        }

        if(filesAvailableToDownload == null) {
            // all items are video resources (download original for all)
            filesAvailableToDownload = new ArrayList<>(1);
            filesAvailableToDownload.add(resourcesToDownload.iterator().next().getFullSizeFile().getName());
        }
        return filesAvailableToDownload;
    }

    private void addDataToViewFields(AlertDialog view, Set<ResourceItem> resourcesToDownload, String defaultSelectedFilesizeName) {
        SortedMap<AbstractBaseResourceItem.ResourceFile,String> downloadFileSizeOptionsValues = new TreeMap<>(Collections.reverseOrder());
        List<String> sharedFileSizesAvailable = getFileSizesAvailableForAllResources(resourcesToDownload);
        for(ResourceItem item : resourcesToDownload) {
            ArrayList<AbstractBaseResourceItem.ResourceFile> resourceFiles = item.getAvailableFiles();
            for(AbstractBaseResourceItem.ResourceFile f : resourceFiles) {
                if(sharedFileSizesAvailable.contains(f.getName())) {
                    if (!downloadFileSizeOptionsValues.containsValue(f.getName())) {
                        downloadFileSizeOptionsValues.put(f, f.getName());
                    }
                }
            }
        }
        if(resourcesToDownload.size() > 1) {
            // swap the chosen original file (which has a specific size) for a generic size-less one.
            ResourceItem.ResourceFile key = null;
            for(Map.Entry<ResourceItem.ResourceFile, String> entry : downloadFileSizeOptionsValues.entrySet()) {
                if(ResourceItem.ResourceFile.ORIGINAL.equals(entry.getValue())) {
                    key = entry.getKey();
                    break;
                }
            }
            if(key != null) {
                downloadFileSizeOptionsValues.remove(key);
                downloadFileSizeOptionsValues.put(ResourceItem.ResourceFile.getGenericOriginalFile(), ResourceItem.ResourceFile.ORIGINAL);
            }
        }
        downloadFileSizeOptionsAdapter = new MappedArrayAdapter<>(view.getContext(), R.layout.layout_dialog_select_singlechoice_compressed, downloadFileSizeOptionsValues);

        ((TextView) view.findViewById(R.id.alertMessage)).setText(R.string.alert_image_download_message);
        downloadOptionGroup = view.findViewById(R.id.download_option);
        downloadOptionGroup.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(position != 0);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        final ListView fileSelectList = view.findViewById(R.id.fileSelectList);

        downloadOptionGroup.setAdapter(downloadActionsOptionsAdapter);

        fileSelectList.setAdapter(downloadFileSizeOptionsAdapter);

        int defaultFileSelectionPos = downloadFileSizeOptionsAdapter.getPositionByValue(defaultSelectedFilesizeName);
        if(defaultFileSelectionPos >= 0) {
            fileSelectList.setItemChecked(defaultFileSelectionPos, true);
        } else {
            fileSelectList.setItemChecked(downloadFileSizeOptionsAdapter.getCount() - 1, true);
        }
    }

    private MappedArrayAdapter<String, Integer> buildDownloadOptionsAdapter(Context context, Set<ResourceItem> itemsToDownload, Set<ResourceItem> vidsUnableToDownload) {
        Map<String,Integer> downloadOptions = new LinkedHashMap<>();
        downloadOptions.put("", -1);
        if(itemsToDownload.size() == 1) {
            downloadOptions.put(context.getString(R.string.copy_link), R.string.copy_link);
        }
        if(vidsUnableToDownload.size() != itemsToDownload.size()) {
            downloadOptions.put(context.getString(R.string.download_file), R.string.download_file);
            downloadOptions.put(context.getString(R.string.share_with_an_app), R.string.share_with_an_app);
        }
        return new MappedArrayAdapter<>(context, R.layout.support_simple_spinner_dropdown_item, downloadOptions);
    }

    private void onDownloadOptionsSelected(FilterItemsRequestedForDownload filterItemsRequestedForDownload, String selectedFilesizeName, int selectedAction) {
        Set<ResourceItem> itemsToDownload = filterItemsRequestedForDownload.getItemsToDownload();
        Set<ResourceItem> vidsUnableToDownload = filterItemsRequestedForDownload.getVidsUnableToDownload();
        switch(selectedAction) {
            case R.string.copy_link:
                onCopyLink(itemsToDownload, selectedFilesizeName);
                break;
            case R.string.download_file:
                itemsToDownload.removeAll(vidsUnableToDownload);
                onDownloadFile(itemsToDownload, selectedFilesizeName, vidsUnableToDownload);
                break;
            case R.string.share_with_an_app:
                itemsToDownload.removeAll(vidsUnableToDownload);
                onShareFile(itemsToDownload, selectedFilesizeName, vidsUnableToDownload);
                break;
            default:
                // no selection (do nothing) - this is impossible
                throw new IllegalStateException("No valid download files action selected: " + selectedAction);
        }
    }

    private void onShareFile(Set<ResourceItem> items, String selectedPiwigoFilesizeName, Set<ResourceItem> filesUnavailableToDownload) {
        downloadSelectionListener.onShare(items, selectedPiwigoFilesizeName, filesUnavailableToDownload);
    }

    private void onDownloadFile(Set<ResourceItem> items, String selectedPiwigoFilesizeName, Set<ResourceItem> filesUnavailableToDownload) {
        downloadSelectionListener.onDownload(items, selectedPiwigoFilesizeName, filesUnavailableToDownload);
    }

    private void onCopyLink(Set<ResourceItem> items, String selectedPiwigoFilesizeName) {
        downloadSelectionListener.onCopyLink(context, items, selectedPiwigoFilesizeName);
    }

    public interface DownloadSelectionMultiItemListener {
        void onDownload(Set<ResourceItem> items, String selectedPiwigoFilesizeName, Set<ResourceItem> filesUnavailableToDownload);
        void onShare(Set<ResourceItem> items, String selectedPiwigoFilesizeName, Set<ResourceItem> filesUnavailableToDownload);
        void onCopyLink(Context context, Set<ResourceItem> items, String selectedPiwigoFilesizeName);
    }

    private class FilterItemsRequestedForDownload {
        private Set<ResourceItem> itemsSelectedForDownload;
        private Set<ResourceItem> itemsToDownload;
        private Set<ResourceItem> vidsUnableToDownload;

        public FilterItemsRequestedForDownload(Set<ResourceItem> itemsSelectedForDownload) {
            this.itemsSelectedForDownload = itemsSelectedForDownload;
        }

        public Set<ResourceItem> getItemsToDownload() {
            return itemsToDownload;
        }

        public Set<ResourceItem> getVidsUnableToDownload() {
            return vidsUnableToDownload;
        }

        public FilterItemsRequestedForDownload invoke() {
            itemsToDownload = new HashSet<>(itemsSelectedForDownload.size());
            itemsToDownload.addAll(itemsSelectedForDownload); // all items are left in the list at this time - copy remote URI link is always possible
            vidsUnableToDownload = new HashSet<>(0);
            for(ResourceItem item : itemsToDownload) {
                if(item instanceof VideoResourceItem) {
                    VideoResourceItem vidItem = (VideoResourceItem)item;
                    if(!RemoteAsyncFileCachingDataSource.isFileFullyCached(context, Uri.parse(vidItem.getFileUrl(AbstractBaseResourceItem.ResourceFile.ORIGINAL)))) {
                        vidsUnableToDownload.add(item);
                    }
                }
            }
            return this;
        }
    }
}
