package delit.piwigoclient.ui.events.trackable;

import android.content.Context;
import android.net.Uri;
import android.os.Build;

import androidx.documentfile.provider.DocumentFile;

import com.crashlytics.android.Crashlytics;

import java.io.IOException;
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
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                try {
                    if("file".equals(uri.getScheme())) {
                        item.withLegacyCachedFields();
                    }
                } catch (IOException e) {
                    Crashlytics.logException(e);
                }
            }
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

    public ArrayList<DocumentFile> getSelectedFolderItemsAsFiles(Context context) {
        ArrayList<DocumentFile> selectedFiles = new ArrayList<>(selectedFolderItems.size());
        for (FolderItemRecyclerViewAdapter.FolderItem item : selectedFolderItems) {
            selectedFiles.add(item.getDocumentFile(context));
        }
        return selectedFiles;
    }
}
