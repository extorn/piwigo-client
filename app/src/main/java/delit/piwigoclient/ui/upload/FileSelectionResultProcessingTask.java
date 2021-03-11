package delit.piwigoclient.ui.upload;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.ui.OwnedSafeAsyncTask;
import delit.libs.ui.util.DisplayUtils;
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

        //Phase 1. (15%) (note this might remove items from the list in the event)
        loadAndPerformSimpleChecksOnFiles(overallTaskProgressTracker, event.getSelectedFolderItems());


        // At this point, overallChecksumCalcTask is initialised (15% -100% or 0% - 100% as appropriate - same number of files to process)
        long totalImportedFileBytes = getTotalImportedFileBytes(event.getSelectedFolderItems());
        DividableProgressTracker overallChecksumCalcTask = overallTaskProgressTracker.addChildTask("overall files checksum calculation", totalImportedFileBytes, overallTaskProgressTracker.getRemainingWork());

        // this will store the acceptable imported files
        ArrayList<UploadDataItem> uploadDataItems = new ArrayList<>(event.getSelectedFolderItems().size());

        for (FolderItem f : event.getSelectedFolderItems()) {
            if(BuildConfig.DEBUG) {
                Log.e(TAG,"Overall File selection progress : " + overallTaskProgressTracker.getProgressPercentage());
            }
            calculateChecksumOnFile(overallChecksumCalcTask, uploadDataItems, f);
        }
        overallChecksumCalcTask.markComplete();
        overallTaskProgressTracker.markComplete();
        getOwner().updateLastOpenedFolderPref(getContext(), event.getSelectedFolderItems());
        return uploadDataItems;
    }

    private void calculateChecksumOnFile(DividableProgressTracker overallChecksumCalcTask, ArrayList<UploadDataItem> uploadDataItems, FolderItem f) {
        if(BuildConfig.DEBUG) {
            Log.w(TAG, "Upload Fragment Passed URI: " + f.getContentUri());
        }
        UploadDataItem item = new UploadDataItem(f.getContentUri(), f.getName(), f.getMime(), f.getFileLength());
        DividableProgressTracker fileChecksumTracker = overallChecksumCalcTask.addChildTask("file checksum calculation", f.getFileLength(), f.getFileLength());
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

    private void loadAndPerformSimpleChecksOnFiles(DividableProgressTracker fileSelectionProgress, ArrayList<FolderItem> selectedFolderItems) {

        int itemCount = selectedFolderItems.size();

        ConnectionPreferences.ProfilePreferences activeProfile = ConnectionPreferences.getActiveProfile();
        if (PiwigoSessionDetails.getInstance(activeProfile) != null) {

            Set<String> allowedFileTypes = PiwigoSessionDetails.getInstance(activeProfile).getAllowedFileTypes();

            Set<String> unsupportedExts = new HashSet<>();


            // Initialise phase 1 (0% -15% - split between number of files to process)
            DividableProgressTracker cachingProgressTracker = fileSelectionProgress.addChildTask("caching files information", itemCount, 6);
            try {
                FolderItem.cacheDocumentInformation(getContext(), selectedFolderItems, new TrackerUpdatingProgressListener(cachingProgressTracker));
            } finally {
                cachingProgressTracker.markComplete();
            }
            Logging.log(Log.DEBUG, TAG, "Doc Field Caching Progress : %1$.0f (complete? %2$b)", 100 * cachingProgressTracker.getProgressPercentage(), cachingProgressTracker.isComplete());

            DividableProgressTracker uriPermissionsProgressTracker = fileSelectionProgress.addChildTask("Checking file type and uri permissions", itemCount, 9);
            // Phase 1.2 (0%) check for appropriate Uri Permissions
            Iterator<FolderItem> iter = selectedFolderItems.iterator();
            int filesMissingPermissionsCount = 0;
            while (iter.hasNext()) {
                FolderItem f = iter.next();
                //FIXME move this code into the UI. Mark them red and suggest they are compressed (as a group perhaps?)
                if (f.getExt() == null || (!allowedFileTypes.contains(f.getExt()) && !allowedFileTypes.contains(f.getExt().toLowerCase()))) {
                    String mimeType = f.getMime();
                    if (mimeType == null && !IOUtils.isPlayableMedia(mimeType)) {
                        iter.remove();
                        unsupportedExts.add(f.getExt());
                    }
                }
                if(!IOUtils.appHoldsAllUriPermissionsForUri(getContext(), f.getContentUri(), IOUtils.URI_PERMISSION_READ)) {
                    filesMissingPermissionsCount++;
                }
                uriPermissionsProgressTracker.incrementWorkDone(1);
            }
            uriPermissionsProgressTracker.markComplete();

            // Phase 1.3 (0%) notify user of any issues found so far
            if (!unsupportedExts.isEmpty()) {
                String msg = getOwner().getString(R.string.alert_error_unsupported_file_extensions_pattern, CollectionUtils.toCsvList(unsupportedExts));
                DisplayUtils.postOnUiThread(() ->getOwner().getUiHelper().showOrQueueDialogMessage(R.string.alert_information, msg));
            }
            if (filesMissingPermissionsCount > 0) {
                String msg;
                if(filesMissingPermissionsCount == selectedFolderItems.size()) {
                    msg = getContext().getString(R.string.alert_error_files_without_permissions);
                } else {
                    msg = getContext().getString(R.string.alert_error_files_without_permissions_pattern, filesMissingPermissionsCount);
                }
                DisplayUtils.postOnUiThread(() ->getOwner().getUiHelper().showOrQueueDialogMessage(R.string.alert_warning, msg));
            }
        }
    }

    @Override
    protected void onPostExecuteSafely(List<UploadDataItem> folderItems) {
        getOwner().onAddFilesForUpload(folderItems);
    }

}
