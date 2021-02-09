package delit.piwigoclient.ui.events.trackable;

import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;
import java.util.Collection;

import delit.piwigoclient.ui.file.FolderItem;

/**
 * Created by gareth on 13/06/17.
 */

public class FileSelectionCompleteEvent extends TrackableResponseEvent {

    private final long actionTimeMillis;
    private final ArrayList<FolderItem> selectedFolderItems;
    private boolean necessaryPermissionsGranted = true;

    public FileSelectionCompleteEvent(int actionId, long actionTimeMillis) {
        super(actionId);
        this.selectedFolderItems = new ArrayList<>();
        this.actionTimeMillis = actionTimeMillis;
    }

    public FileSelectionCompleteEvent withFiles(ArrayList<Uri> uris, boolean permissionsGrantedForAllFilesNeeded) {
        withFiles(uris);
        necessaryPermissionsGranted = permissionsGrantedForAllFilesNeeded;
        return this;
    }

    public FileSelectionCompleteEvent withFiles(Collection<Uri> uris) {
        for (Uri uri : uris) {
            FolderItem item = new FolderItem(uri);
            selectedFolderItems.add(item);
        }
        return this;
    }

    public FileSelectionCompleteEvent withFolderItems(ArrayList<FolderItem> selectedFolderItems) {
        this.selectedFolderItems.addAll(selectedFolderItems);
        return this;
    }

    public long getActionTimeMillis() {
        return actionTimeMillis;
    }

    public ArrayList<FolderItem> getSelectedFolderItems() {
        return selectedFolderItems;
    }

    public ArrayList<DocumentFile> getSelectedFolderItemsAsFiles() {
        ArrayList<DocumentFile> selectedFiles = new ArrayList<>(selectedFolderItems.size());
        for (FolderItem item : selectedFolderItems) {
            selectedFiles.add(item.getDocumentFile());
        }
        return selectedFiles;
    }

    public boolean isNecessaryPermissionsGranted() {
        return necessaryPermissionsGranted;
    }
}
