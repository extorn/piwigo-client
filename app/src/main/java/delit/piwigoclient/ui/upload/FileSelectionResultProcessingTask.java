package delit.piwigoclient.ui.upload;

import android.util.Log;

import androidx.core.content.MimeTypeFilter;

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
import delit.libs.util.progress.TaskProgressTracker;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;
import delit.piwigoclient.ui.file.FolderItem;
import delit.piwigoclient.ui.upload.list.UploadDataItemModel;
import delit.piwigoclient.ui.util.UiUpdatingProgressListener;

class FileSelectionResultProcessingTask extends OwnedSafeAsyncTask<AbstractUploadFragment<?>, FileSelectionCompleteEvent, Object, List<UploadDataItemModel.UploadDataItem>> {

    private static final String TAG = "FileSelResProcTask";

    FileSelectionResultProcessingTask(AbstractUploadFragment<?> parent) {
        super(parent);
        withContext(parent.requireContext().getApplicationContext());
    }

    @Override
    protected void onPreExecuteSafely() {
        getOwner().showOverallUploadProgressIndicator(R.string.calculating_file_checksums, 0);
    }

    @Override
    protected List<UploadDataItemModel.UploadDataItem> doInBackgroundSafely(FileSelectionCompleteEvent... objects) {
        UiUpdatingProgressListener progressListener = new UiUpdatingProgressListener(getOwner().getOverallUploadProgressIndicator(), R.string.calculating_file_checksums);
        TaskProgressTracker fileSelectionProgress = new TaskProgressTracker(100, progressListener);
        FileSelectionCompleteEvent event = objects[0];
        int itemCount = event.getSelectedFolderItems().size();

        ConnectionPreferences.ProfilePreferences activeProfile = ConnectionPreferences.getActiveProfile();
        if (PiwigoSessionDetails.getInstance(activeProfile) != null) {

            Set<String> allowedFileTypes = PiwigoSessionDetails.getInstance(activeProfile).getAllowedFileTypes();

            Set<String> unsupportedExts = new HashSet<>();
            int currentItem = 0;


            // Initialise phase 1 (0% -15% - split between number of files to process)
            TaskProgressTracker cachingProgressTracker = fileSelectionProgress.addSubTask(itemCount, 15);
            FolderItem.cacheDocumentInformation(getContext(), event.getSelectedFolderItems(), cachingProgressTracker);
            Logging.log(Log.DEBUG, TAG, "Doc Field Caching Progress : %1$.0f (complete? %2$b)", 100 * cachingProgressTracker.getTaskProgress(), cachingProgressTracker.isComplete());
            Iterator<FolderItem> iter = event.getSelectedFolderItems().iterator();
            int missingPermissions = 0;
            while (iter.hasNext()) {
                currentItem++;
                FolderItem f = iter.next();
                //TODO check this is correct to assume that upper case extensions are okay (server isn't case sensitive!).
                if (f.getExt() == null || (!allowedFileTypes.contains(f.getExt()) && !allowedFileTypes.contains(f.getExt().toLowerCase()))) {
                    String mimeType = f.getMime();
                    if (mimeType == null || !MimeTypeFilter.matches(mimeType, "video/*")) {
                        iter.remove();
                        unsupportedExts.add(f.getExt());
                    }
                }
                if(!IOUtils.appHoldsAllUriPermissionsForUri(getContext(), f.getContentUri(), IOUtils.URI_PERMISSION_READ)) {
                    missingPermissions++;
                }
            }
            if (!unsupportedExts.isEmpty()) {
                String msg = getOwner().getString(R.string.alert_error_unsupported_file_extensions_pattern, CollectionUtils.toCsvList(unsupportedExts));
                DisplayUtils.postOnUiThread(() ->getOwner().getUiHelper().showOrQueueDialogMessage(R.string.alert_information, msg));
            }
            if (missingPermissions > 0) {
                String msg = getContext().getString(R.string.alert_error_files_without_permissions_pattern, missingPermissions);
                DisplayUtils.postOnUiThread(() ->getOwner().getUiHelper().showOrQueueDialogMessage(R.string.alert_warning, msg));
            }
        }

        long totalImportedFileBytes = 0;
        for (FolderItem item : event.getSelectedFolderItems()) {
            totalImportedFileBytes += item.getFileLength();
        }

        // At this point, firstMainTaskProgressListener is initialise phase 2 (15% -100% or 0% - 100% as appropriate - same number of files to process)
        TaskProgressTracker overallChecksumCalcTask = fileSelectionProgress.addSubTask(totalImportedFileBytes, fileSelectionProgress.getRemainingWork());

        ArrayList<UploadDataItemModel.UploadDataItem> uploadDataItems = new ArrayList<>(event.getSelectedFolderItems().size());

        for (FolderItem f : event.getSelectedFolderItems()) {

            if(BuildConfig.DEBUG) {
                Log.w(TAG, "Upload Fragment Passed URI: " + f.getContentUri());
            }
            UploadDataItemModel.UploadDataItem item = new UploadDataItemModel.UploadDataItem(f.getContentUri(), f.getName(), f.getMime());
            TaskProgressTracker fileChecksumTracker = overallChecksumCalcTask.addSubTask(f.getFileLength(), f.getFileLength());
            try {
                item.calculateDataHashCode(getContext(), fileChecksumTracker);
                uploadDataItems.add(item);
            } catch (Md5SumUtils.Md5SumException e) {
                Logging.recordException(e);
            } catch(SecurityException secException) {
                DisplayUtils.runOnUiThread(() -> getOwner().getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getContext().getString(R.string.sorry_file_unusable_as_app_shared_from_does_not_provide_necessary_permissions)));
            } finally {
                fileChecksumTracker.markComplete();
            }
        }
        overallChecksumCalcTask.markComplete();
        fileSelectionProgress.markComplete();
        getOwner().updateLastOpenedFolderPref(getContext(), event.getSelectedFolderItems());
        return uploadDataItems;
    }

    @Override
    protected void onPostExecuteSafely(List<UploadDataItemModel.UploadDataItem> folderItems) {
        getOwner().hideOverallUploadProgressIndicator();
        getOwner().switchToUploadingFilesTab();
        getOwner().updateFilesForUploadList(folderItems);
    }

}
