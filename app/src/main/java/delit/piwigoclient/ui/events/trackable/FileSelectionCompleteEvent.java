package delit.piwigoclient.ui.events.trackable;

import java.io.File;
import java.util.ArrayList;

import delit.piwigoclient.ui.file.FolderItemRecyclerViewAdapter;

/**
 * Created by gareth on 13/06/17.
 */

public class FileSelectionCompleteEvent extends TrackableResponseEvent {

    private long actionTimeMillis;
    private final ArrayList<FolderItemRecyclerViewAdapter.FolderItem> selectedFolderItems;
    private boolean contentUrisPresent;

    public FileSelectionCompleteEvent(int actionId, long actionTimeMillis) {
        super(actionId);
        this.selectedFolderItems = new ArrayList<>();
        this.actionTimeMillis = actionTimeMillis;
        this.contentUrisPresent = false;
    }

    public FileSelectionCompleteEvent withFiles(ArrayList<File> selectedFiles) {
        for (File f : selectedFiles) {
            selectedFolderItems.add(new FolderItemRecyclerViewAdapter.FolderItem(f));
        }
        contentUrisPresent = false;
        return this;
    }

    public FileSelectionCompleteEvent withFolderItems(ArrayList<FolderItemRecyclerViewAdapter.FolderItem> selectedFolderItems) {
        this.selectedFolderItems.addAll(selectedFolderItems);
        this.contentUrisPresent = true;
        return this;
    }

    public long getActionTimeMillis() {
        return actionTimeMillis;
    }

    public ArrayList<FolderItemRecyclerViewAdapter.FolderItem> getSelectedFolderItems() {
        return selectedFolderItems;
    }

    public ArrayList<File> getSelectedFolderItemsAsFiles() {
        ArrayList<File> selectedFiles = new ArrayList<>(selectedFolderItems.size());
        for (FolderItemRecyclerViewAdapter.FolderItem item : selectedFolderItems) {
            selectedFiles.add(item.getFile());
        }
        return selectedFiles;
    }

    public boolean isContentUrisPresent() {
        return contentUrisPresent;
    }
}
