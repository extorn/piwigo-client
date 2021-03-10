package delit.piwigoclient.ui.file.action;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.Iterator;

import delit.libs.ui.OwnedSafeAsyncTask;
import delit.libs.util.IOUtils;
import delit.libs.util.progress.BasicProgressTracker;
import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.file.FolderItem;
import delit.piwigoclient.ui.file.FolderItemRecyclerViewAdapter;
import delit.piwigoclient.ui.file.FolderItemViewAdapterPreferences;
import delit.piwigoclient.ui.file.RecyclerViewDocumentFileFolderItemSelectFragment;
import delit.piwigoclient.ui.util.UiUpdatingProgressListener;

public class SelectedFilesUriCheckTask<F extends RecyclerViewDocumentFileFolderItemSelectFragment<F,FUIH,LVA>, FUIH extends FragmentUIHelper<FUIH,F>,LVA extends FolderItemRecyclerViewAdapter<LVA, FolderItem,?,?>> extends OwnedSafeAsyncTask<F, Void, Integer, HashSet<FolderItem>> {
    private final HashSet<FolderItem> selectedItems;
    private int removedCount;

    public SelectedFilesUriCheckTask(@NonNull F owner, HashSet<FolderItem> selectedItems) {
        super(owner);
        withContext(owner.requireContext());
        this.selectedItems = selectedItems;
    }

    private FolderItemViewAdapterPreferences getViewPrefs() {
        return getOwner().getViewPrefs();
    }

    @Override
    protected HashSet<FolderItem> doInBackgroundSafely(Void... voids) {
        UiUpdatingProgressListener progressBarUpdater = new UiUpdatingProgressListener(getOwner().getUiHelper().getProgressIndicator(), R.string.checking_uri_permissions);
        BasicProgressTracker progressTracker = new BasicProgressTracker("checking uri permissions", selectedItems.size(), progressBarUpdater);
        try {
            removedCount = 0;
            for (Iterator<FolderItem> iterator = selectedItems.iterator(); iterator.hasNext(); ) {
                FolderItem selectedItem = iterator.next();
                Uri uri = selectedItem.getContentUri();
                selectedItem.cacheFields(getContext());
                if (!IOUtils.appHoldsAllUriPermissionsForUri(getContext(), uri, getViewPrefs().getSelectedUriPermissionFlags())) {
                    iterator.remove();
                    removedCount++;
                }
                progressTracker.incrementWorkDone(1);
            }
        } finally {
            progressTracker.markComplete();
        }
        return selectedItems;
    }

    @Override
    protected void onPostExecuteSafely(HashSet<FolderItem> folderItems) {
        super.onPostExecuteSafely(folderItems);
        getOwner().onActionFilesReadyToShareWithRequester(folderItems, removedCount > 0);
    }
}
