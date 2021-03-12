package delit.piwigoclient.ui.file.action;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import delit.libs.ui.OwnedSafeAsyncTask;
import delit.libs.ui.util.ExecutorManager;
import delit.libs.util.IOUtils;
import delit.libs.util.progress.BasicProgressTracker;
import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.file.FolderItem;
import delit.piwigoclient.ui.file.FolderItemRecyclerViewAdapter;
import delit.piwigoclient.ui.file.FolderItemViewAdapterPreferences;
import delit.piwigoclient.ui.file.RecyclerViewDocumentFileFolderItemSelectFragment;
import delit.piwigoclient.ui.util.UiUpdatingProgressListener;

public class SelectedFilesUriCheckTask<F extends RecyclerViewDocumentFileFolderItemSelectFragment<F,FUIH,P>, FUIH extends FragmentUIHelper<FUIH,F>, P extends FolderItemViewAdapterPreferences<P>> extends OwnedSafeAsyncTask<F, Void, Integer, HashSet<FolderItem>> {
    private final HashSet<FolderItem> selectedItems;
    private int removedCount;
    private static final String TAG = "SelectedFilesUriCheckTask";

    public SelectedFilesUriCheckTask(@NonNull F owner, HashSet<FolderItem> selectedItems) {
        super(owner);
        withContext(owner.requireContext());
        this.selectedItems = selectedItems;
    }

    private P getViewPrefs() {
        return getOwner().getViewPrefs();
    }

    @Override
    protected HashSet<FolderItem> doInBackgroundSafely(Void... voids) {
        UiUpdatingProgressListener progressBarUpdater = new UiUpdatingProgressListener(getOwner().getUiHelper().getProgressIndicator(), R.string.checking_uri_permissions);
        BasicProgressTracker progressTracker = new BasicProgressTracker("checking uri permissions", selectedItems.size(), progressBarUpdater);
        ExecutorManager executor = new ExecutorManager(3,10,0, 1);
        try {
            Set<FolderItem> filesWithoutPermissions = Collections.synchronizedSet(new HashSet<>());
            // using a scheduler like this means the scheduler gets blocked if no space not this thread.
            Future<List<Future<Void>>> taskScheduler = executor.submitTasksInTask(new ExecutorManager.TaskSubmitter<Void,FolderItem>(executor, selectedItems) {
                @Override
                public Callable<Void> buildTask(FolderItem item) {
                    return () -> {
                        try {
                            Uri uri = item.getContentUri();
                            item.cacheFields(getContext());
                            if (!IOUtils.appHoldsAllUriPermissionsForUri(getContext(), uri, getViewPrefs().getSelectedUriPermissionFlags())) {
                                filesWithoutPermissions.add(item);
                            }
                        } finally {
                            progressTracker.incrementWorkDone(1);
                        }
                        return null;
                    };
                }
            });

            // Wait for the tasks to finish.
            progressTracker.waitUntilComplete(1000 * selectedItems.size(), true);

            for (FolderItem fileWithoutPermissions : filesWithoutPermissions) {
                selectedItems.remove(fileWithoutPermissions);
                removedCount++;
            }
            executor.shutdown(2);

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
