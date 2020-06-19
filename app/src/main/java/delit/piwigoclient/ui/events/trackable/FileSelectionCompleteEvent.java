package delit.piwigoclient.ui.events.trackable;

import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;
import java.util.Collection;

import delit.piwigoclient.ui.file.FolderItemRecyclerViewAdapter;

/**
 * Created by gareth on 13/06/17.
 */

public class FileSelectionCompleteEvent extends TrackableResponseEvent {

    private long actionTimeMillis;
    private final ArrayList<FolderItemRecyclerViewAdapter.FolderItem> selectedFolderItems;

    public FileSelectionCompleteEvent(int actionId, long actionTimeMillis) {
        super(actionId);
        this.selectedFolderItems = new ArrayList<>();
        this.actionTimeMillis = actionTimeMillis;
    }

    public FileSelectionCompleteEvent withFiles(Collection<Uri> uris) {
        for (Uri uri : uris) {
            FolderItemRecyclerViewAdapter.FolderItem item = new FolderItemRecyclerViewAdapter.FolderItem(uri);
            selectedFolderItems.add(item);
        }
        return this;
    }

    public FileSelectionCompleteEvent withFolderItems(ArrayList<FolderItemRecyclerViewAdapter.FolderItem> selectedFolderItems) {
        this.selectedFolderItems.addAll(selectedFolderItems);
        return this;
    }

    public long getActionTimeMillis() {
        return actionTimeMillis;
    }

    public ArrayList<FolderItemRecyclerViewAdapter.FolderItem> getSelectedFolderItems() {
        return selectedFolderItems;
    }

    public ArrayList<DocumentFile> getSelectedFolderItemsAsFiles() {
        ArrayList<DocumentFile> selectedFiles = new ArrayList<>(selectedFolderItems.size());
        for (FolderItemRecyclerViewAdapter.FolderItem item : selectedFolderItems) {
            selectedFiles.add(item.getDocumentFile());
        }
        return selectedFiles;
    }
}
