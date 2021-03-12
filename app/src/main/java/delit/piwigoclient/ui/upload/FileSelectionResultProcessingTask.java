package delit.piwigoclient.ui.upload;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import delit.libs.core.util.Logging;
import delit.libs.ui.OwnedSafeAsyncTask;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.util.ExecutorManager;
import delit.libs.util.CollectionUtils;
import delit.libs.util.IOUtils;
import delit.libs.util.Md5SumUtils;
import delit.libs.util.progress.DividableProgressTracker;
import delit.libs.util.progress.TrackerUpdatingProgressListener;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;
import delit.piwigoclient.ui.file.FolderItem;
import delit.piwigoclient.ui.upload.list.UploadDataItem;
import delit.piwigoclient.ui.util.UiUpdatingProgressListener;

class FileSelectionResultProcessingTask extends OwnedSafeAsyncTask<AbstractUploadFragment<?,?>, FileSelectionCompleteEvent, Object, List<UploadDataItem>> {

    private static final String TAG = "FileSelResProcTask";

    FileSelectionResultProcessingTask(AbstractUploadFragment<?,?> parent) {
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

        DividableProgressTracker taskTracker;
        //Phase 1. (18%) (note this might remove items from the list in the event)
        taskTracker = overallTaskProgressTracker.addChildTask("cache file info", 100, 10);
        doActionCacheFileInformation(taskTracker, event.getSelectedFolderItems());
        taskTracker =overallTaskProgressTracker.addChildTask("uri permission and mime type check", 100,8);
        doActionCheckForUriPermissionAndPermissableMimeType(taskTracker, event.getSelectedFolderItems());
        taskTracker =overallTaskProgressTracker.addChildTask("files checksum calculation", 100, overallTaskProgressTracker.getRemainingWork() - 1);
        ArrayList<UploadDataItem> uploadDataItems = doActionCalculateChecksums(taskTracker, event.getSelectedFolderItems());

        getOwner().updateLastOpenedFolderPref(getContext(), event.getSelectedFolderItems());
        overallTaskProgressTracker.markComplete();
        return uploadDataItems;
    }

    private ArrayList<UploadDataItem> doActionCalculateChecksums(DividableProgressTracker progressTracker, ArrayList<FolderItem> selectedFolderItems) {
        long totalImportedFileBytes = getTotalImportedFileBytes(selectedFolderItems);
        DividableProgressTracker taskTracker = progressTracker.addChildTask("checksum calculation detail", totalImportedFileBytes, progressTracker.getRemainingWork());

        // this will store the acceptable imported files
        ArrayList<UploadDataItem> uploadDataItems = new ArrayList<>(selectedFolderItems.size());

        for (FolderItem f : selectedFolderItems) {
            if(BuildConfig.DEBUG) {
                Log.e(TAG,"Overall File selection progress : " + taskTracker.getProgressPercentage());
            }
            calculateChecksumOnFile(taskTracker, uploadDataItems, f);
        }
        taskTracker.markComplete();
        progressTracker.markComplete();
        return uploadDataItems;
    }

    private void calculateChecksumOnFile(DividableProgressTracker overallChecksumCalcTask, ArrayList<UploadDataItem> uploadDataItems, FolderItem f) {
        if(BuildConfig.DEBUG) {
            Log.w(TAG, "Upload Fragment Passed URI: " + f.getContentUri());
        }
        UploadDataItem item = new UploadDataItem(f.getContentUri(), f.getName(), f.getMime(), f.getFileLength());
        DividableProgressTracker fileChecksumTracker = overallChecksumCalcTask.addChildTask("file checksum calculation", 100, f.getFileLength());
        try {
            item.calculateDataHashCode(getContext(), new TrackerUpdatingProgressListener(fileChecksumTracker));
            uploadDataItems.add(item);
        } catch (Md5SumUtils.Md5SumException e) {
            Logging.recordException(e);
        } catch(SecurityException secException) {
            DisplayUtils.runOnUiThread(() -> getOwner().getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getContext().getString(R.string.sorry_file_unusable_as_app_shared_from_does_not_provide_necessary_permissions)));
        } finally {
            fileChecksumTracker.markComplete();
        }
    }

    private long getTotalImportedFileBytes(ArrayList<FolderItem> selectedFolderItems) {
        long totalImportedFileBytes = 0;
        for (FolderItem item : selectedFolderItems) {
            totalImportedFileBytes += item.getFileLength();
        }
        return totalImportedFileBytes;
    }

    private void doActionCacheFileInformation(DividableProgressTracker progressTracker, ArrayList<FolderItem> selectedFolderItems) {
        try {
            FolderItem.cacheDocumentInformation(getContext(), selectedFolderItems, new TrackerUpdatingProgressListener(progressTracker));
        } finally {
            progressTracker.markComplete();
        }
        Logging.log(Log.DEBUG, TAG, "Doc Field Caching Progress : %1$.0f (complete? %2$b)", 100 * progressTracker.getProgressPercentage(), progressTracker.isComplete());
    }

    private void doActionCheckForUriPermissionAndPermissableMimeType(DividableProgressTracker progressTracker, ArrayList<FolderItem> selectedFolderItems) {
        ConnectionPreferences.ProfilePreferences activeProfile = ConnectionPreferences.getActiveProfile();
        if (PiwigoSessionDetails.getInstance(activeProfile) == null) {
            progressTracker.markComplete();
            return;
        }

        int itemCount = selectedFolderItems.size();

        Set<String> allowedFileTypes = PiwigoSessionDetails.getInstance(activeProfile).getAllowedFileTypes();

        DividableProgressTracker uriPermissionsProgressTracker = progressTracker.addChildTask("Checking file type and uri permissions", itemCount, 99);
        ExecutorManager executor = new ExecutorManager(12, 32, 1000, 1);


        Set<FolderItem> filesWithoutPermissions = Collections.synchronizedSet(new HashSet<>());
        Set<FolderItem> unsupportedFiles = Collections.synchronizedSet(new HashSet<>());
        // using a scheduler like this means the scheduler gets blocked if no space not this thread.
        Future<List<Future<Void>>> taskScheduler = executor.submitTasksInTask(new ExecutorManager.TaskSubmitter<Void,FolderItem>(executor, selectedFolderItems) {
            @Override
            public Callable<Void> buildTask(FolderItem item) {
                return () -> {
                    //FIXME move this code into the UI. Mark them red and suggest they are compressed (as a group perhaps?)
                    if (item.getExt() == null || (!allowedFileTypes.contains(item.getExt()) && !allowedFileTypes.contains(item.getExt().toLowerCase()))) {
                        String mimeType = item.getMime();
                        if (mimeType == null && !IOUtils.isPlayableMedia(mimeType)) {
                            unsupportedFiles.add(item);
                        }
                    }
                    if (!IOUtils.appHoldsAllUriPermissionsForUri(getContext(), item.getContentUri(), IOUtils.URI_PERMISSION_READ)) {
                        filesWithoutPermissions.add(item);
                    }
                    uriPermissionsProgressTracker.incrementWorkDone(1);
                    return null;
                };
            }
        });

        // Wait for the tasks to finish.
        uriPermissionsProgressTracker.waitUntilComplete(1000 * selectedFolderItems.size(), true);
        uriPermissionsProgressTracker.markComplete();
        executor.shutdown(2);

        selectedFolderItems.removeAll(unsupportedFiles);


        // Notify user of any issues found so far
        if (!unsupportedFiles.isEmpty()) {
            Set<String> unsupportedExts = new TreeSet<>();
            for(FolderItem folderItem : unsupportedFiles) {
                unsupportedExts.add(folderItem.getExt());
            }
            String msg = getOwner().getString(R.string.alert_error_unsupported_file_extensions_pattern, CollectionUtils.toCsvList(unsupportedExts));
            DisplayUtils.postOnUiThread(() ->getOwner().getUiHelper().showOrQueueDialogMessage(R.string.alert_information, msg));
        }
        if (!filesWithoutPermissions.isEmpty()) {
            String msg;
            if(filesWithoutPermissions.containsAll(selectedFolderItems)) {
                msg = getContext().getString(R.string.alert_error_files_without_permissions);
            } else {
                filesWithoutPermissions.retainAll(selectedFolderItems);
                msg = getContext().getString(R.string.alert_error_files_without_permissions_pattern, filesWithoutPermissions.size());
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
