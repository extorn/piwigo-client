package delit.piwigoclient.ui.upload;

import android.content.SharedPreferences;
import android.util.Log;

import androidx.core.content.MimeTypeFilter;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import delit.libs.core.util.Logging;
import delit.libs.ui.OwnedSafeAsyncTask;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.util.ExecutorManager;
import delit.libs.util.CollectionUtils;
import delit.libs.util.IOUtils;
import delit.libs.util.Utils;
import delit.libs.util.progress.DividableProgressTracker;
import delit.libs.util.progress.TrackerUpdatingProgressListener;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;
import delit.piwigoclient.ui.file.FolderItem;
import delit.piwigoclient.ui.upload.list.UploadDataItem;
import delit.piwigoclient.ui.util.UiUpdatingProgressListener;

class BaseFileSelectionResultProcessingTask extends OwnedSafeAsyncTask<AbstractUploadFragment<?,?>, FileSelectionCompleteEvent, Object, List<UploadDataItem>> {

    private static final String TAG = "BaseFileSelResProcTask";
    private SharedPreferences prefs = null;

    BaseFileSelectionResultProcessingTask(AbstractUploadFragment<?,?> parent) {
        super(parent);
        withContext(parent.requireContext().getApplicationContext());
    }

    @Override
    protected void onPreExecuteSafely() {
        getOwner().showOverallUploadProgressIndicator(R.string.calculating_file_checksums, 0);
    }

    @Override
    protected List<UploadDataItem> doInBackgroundSafely(FileSelectionCompleteEvent... objects) {
        UiUpdatingProgressListener progressViewUpdater = new UiUpdatingProgressListener(getOwner().getOverallUploadProgressIndicator(), R.string.calculating_file_checksums);
        DividableProgressTracker overallTaskProgressTracker = new DividableProgressTracker("Overall file selection", 100, progressViewUpdater);
        FileSelectionCompleteEvent event = objects[0];

        int postProcessWork = getPostProcessingWorkAsPercentageOfTotalWork();
        long workToSplit = overallTaskProgressTracker.getRemainingWork() - postProcessWork;
        long fileInfoCacheTaskWork = Utils.longFractionOf(workToSplit, 10, 18);
        long permissionCheckWork = workToSplit - fileInfoCacheTaskWork;

        DividableProgressTracker taskTracker;
        //Phase 1. (note this might remove items from the list in the event)
        taskTracker = overallTaskProgressTracker.addChildTask("cache file info", 100, fileInfoCacheTaskWork);
        doActionCacheFileInformation(taskTracker, event.getSelectedFolderItems());

        List<UploadDataItem> uploadDataItems = buildUploadDataItemsList(event.getSelectedFolderItems());

        taskTracker =overallTaskProgressTracker.addChildTask("uri permission and mime type check", 100,permissionCheckWork);
        doActionCheckForUriPermissionAndPermissableMimeType(taskTracker, uploadDataItems);
        getOwner().updateLastOpenedFolderPref(getContext(), event.getSelectedFolderItems());

        postProcessDataItems(uploadDataItems, overallTaskProgressTracker, postProcessWork);

        overallTaskProgressTracker.markComplete();

        return uploadDataItems;
    }

    protected void postProcessDataItems(List<UploadDataItem> uploadDataItems, DividableProgressTracker overallTaskProgressTracker, long workToAllocateInTracker) {
    }

    protected int getPostProcessingWorkAsPercentageOfTotalWork() {
        return 0;
    }

    private List<UploadDataItem> buildUploadDataItemsList(ArrayList<FolderItem> folderItems) {
        List<UploadDataItem> uploadDataItems = new ArrayList<>();
        for(FolderItem item : folderItems) {
            uploadDataItems.add(new UploadDataItem(item.getContentUri(), item.getName(), item.getExt(), item.getMime(), item.getFileLength()));
        }
        return uploadDataItems;
    }

    protected SharedPreferences getPrefs() {
        if (prefs == null) {
            prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        }
        return prefs;
    }

    private void doActionCacheFileInformation(DividableProgressTracker progressTracker, ArrayList<FolderItem> selectedFolderItems) {
        try {
            FolderItem.cacheDocumentInformation(getContext(), selectedFolderItems, new TrackerUpdatingProgressListener(progressTracker));
        } finally {
            progressTracker.markComplete();
        }
        Logging.log(Log.DEBUG, TAG, "Doc Field Caching Progress : %1$.0f (complete? %2$b)", 100 * progressTracker.getProgressPercentage(), progressTracker.isComplete());
    }

    private void doActionCheckForUriPermissionAndPermissableMimeType(DividableProgressTracker progressTracker, List<UploadDataItem> uploadDataItems) {
        ConnectionPreferences.ProfilePreferences activeProfile = ConnectionPreferences.getActiveProfile();
        if (PiwigoSessionDetails.getInstance(activeProfile) == null) {
            progressTracker.markComplete();
            return;
        }

        int itemCount = uploadDataItems.size();

        Set<String> allowedFileTypes = PiwigoSessionDetails.getInstance(activeProfile).getAllowedFileTypes();

        DividableProgressTracker uriPermissionsProgressTracker = progressTracker.addChildTask("Checking file type and uri permissions", itemCount, 99);
        ExecutorManager executor = new ExecutorManager(12, 32, 1000, 1);


        AtomicInteger filesWithoutPermissions = new AtomicInteger();
        AtomicInteger itemsNeedCompression = new AtomicInteger();
        SortedSet<String> unsupportedFileExts = Collections.synchronizedSortedSet(new TreeSet<>());
        // using a scheduler like this means the scheduler gets blocked if no space not this thread.
        Future<List<Future<Void>>> taskScheduler = executor.submitTasksInTask(new ExecutorManager.TaskSubmitter<Void,UploadDataItem>(executor, uploadDataItems) {
            @Override
            public Callable<Void> buildTask(UploadDataItem item) {
                return () -> {
                    String fileExt = item.getFileExt();
                    if (fileExt == null || (!allowedFileTypes.contains(fileExt) && !allowedFileTypes.contains(fileExt.toLowerCase()))) {
                        item.setNeedsCompression(true);
                        itemsNeedCompression.incrementAndGet();
                        if(!IOUtils.isPlayableMedia(item.getMimeType()) && !MimeTypeFilter.matches(item.getMimeType(), "image/*")) {
                            unsupportedFileExts.add(fileExt);
                        }
                    }

                    if (!IOUtils.appHoldsAllUriPermissionsForUri(getContext(), item.getUri(), IOUtils.URI_PERMISSION_READ)) {
                        filesWithoutPermissions.incrementAndGet();
                    }
                    uriPermissionsProgressTracker.incrementWorkDone(1);
                    return null;
                };
            }
        });

        // Wait for the tasks to finish.
        uriPermissionsProgressTracker.waitUntilComplete(1000 * uploadDataItems.size(), true);
        uriPermissionsProgressTracker.markComplete();
        executor.shutdown(2);

        // Notify user of any issues found so far
        if(itemsNeedCompression.get() > 0) {
            String msg = getOwner().getString(R.string.alert_error_unsupported_file_extensions_require_compression_pattern, itemsNeedCompression.get());
            DisplayUtils.postOnUiThread(() ->getOwner().getUiHelper().showOrQueueDialogMessage(R.string.alert_information, msg));
        }
        if (!unsupportedFileExts.isEmpty()) {
            String msg = getOwner().getString(R.string.alert_error_unsupported_file_extensions_pattern, CollectionUtils.toCsvList(unsupportedFileExts));
            DisplayUtils.postOnUiThread(() ->getOwner().getUiHelper().showOrQueueDialogMessage(R.string.alert_information, msg));
        }
        if (filesWithoutPermissions.get() > 0) {
            String msg;
            if(filesWithoutPermissions.intValue() == uploadDataItems.size()) {
                msg = getContext().getString(R.string.alert_error_files_without_permissions);
            } else {
                msg = getContext().getString(R.string.alert_error_files_without_permissions_pattern, filesWithoutPermissions.intValue());
            }
            DisplayUtils.postOnUiThread(() ->getOwner().getUiHelper().showOrQueueDialogMessage(R.string.alert_warning, msg));
        }
        progressTracker.markComplete();
    }

    @Override
    protected void onPostExecuteSafely(List<UploadDataItem> folderItems) {
        getOwner().onAddFilesForUpload(folderItems);
    }

}
