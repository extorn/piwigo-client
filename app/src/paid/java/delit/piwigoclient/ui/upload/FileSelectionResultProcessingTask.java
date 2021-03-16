package delit.piwigoclient.ui.upload;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.util.Md5SumUtils;
import delit.libs.util.progress.DividableProgressTracker;
import delit.libs.util.progress.TrackerUpdatingProgressListener;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.business.UploadPreferences;
import delit.piwigoclient.database.PiwigoUploadsDatabase;
import delit.piwigoclient.database.PriorUploadRepository;
import delit.piwigoclient.ui.upload.list.UploadDataItem;

public class FileSelectionResultProcessingTask extends BaseFileSelectionResultProcessingTask {

    private static final String TAG = "FileSelResProcTask";
    private final boolean checkForPriorUploads;

    FileSelectionResultProcessingTask(AbstractUploadFragment<?,?> parent) {
        super(parent);
        checkForPriorUploads = UploadPreferences.isCheckForPriorUploads(getContext(), getPrefs());
    }

    @Override
    protected int getPostProcessingWorkAsPercentageOfTotalWork() {
        if(!checkForPriorUploads) {
            return super.getPostProcessingWorkAsPercentageOfTotalWork();
        }
        return 80;
    }

    @Override
    protected void postProcessDataItems(List<UploadDataItem> uploadDataItems, DividableProgressTracker overallTaskProgressTracker, long workToAllocateInTracker) {
        if(!checkForPriorUploads || uploadDataItems.isEmpty()) {
            return;
        }
        DividableProgressTracker taskTracker = overallTaskProgressTracker.addChildTask("files checksum calculation", 100, getPostProcessingWorkAsPercentageOfTotalWork());
        doActionCalculateChecksums(taskTracker, uploadDataItems);

        PriorUploadRepository repository = PriorUploadRepository.getInstance(PiwigoUploadsDatabase.getInstance(getContext()));
        String profileKey = ConnectionPreferences.getActiveProfile().getAbsoluteProfileKey(getPrefs(), getContext());
        List<String> matchingChecksums = repository.getAllPreviouslyUploadedChecksumsToServerKeyMatching(profileKey, getChecksums(uploadDataItems));
        if(!matchingChecksums.isEmpty()) {
            for (UploadDataItem uploadDataItem : uploadDataItems) {
                if (matchingChecksums.contains(uploadDataItem.getDataHashcode())) {
                    uploadDataItem.setPreviouslyUploaded(true);
                }
            }
        }
        List<Uri> matchingUris = repository.getAllPreviouslyUploadedUrisToServerKeyMatching(profileKey, getUris(uploadDataItems));
        if(!matchingUris.isEmpty()) {
            for (UploadDataItem uploadDataItem : uploadDataItems) {
                if (matchingUris.contains(uploadDataItem.getUri())) {
                    uploadDataItem.setPreviouslyUploaded(true);
                }
            }
        }
    }

    private Set<Uri> getUris(List<UploadDataItem> uploadDataItems) {
        Set<Uri> uris = new HashSet<>();
        for (UploadDataItem uploadDataItem : uploadDataItems) {
            uris.add(uploadDataItem.getUri());
        }
        return uris;
    }

    private Set<String> getChecksums(List<UploadDataItem> uploadDataItems) {
        Set<String> checksums = new HashSet<>();
        for (UploadDataItem uploadDataItem : uploadDataItems) {
            checksums.add(uploadDataItem.getDataHashcode());
        }
        return checksums;
    }

    protected final <T extends ViewModel> T obtainActivityViewModel(@NonNull Context context, @NonNull Class<T> modelClass) {
        Application application = getOwner().requireActivity().getApplication();
        ViewModelProvider.AndroidViewModelFactory factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application);
        return new ViewModelProvider(getOwner(), factory).get(modelClass);
    }

    private void doActionCalculateChecksums(DividableProgressTracker progressTracker, List<UploadDataItem> dataItems) {
        long totalImportedFileBytes = getTotalImportedFileBytes(dataItems);
        DividableProgressTracker taskTracker = progressTracker.addChildTask("checksum calculation detail", totalImportedFileBytes, progressTracker.getRemainingWork());

        for (Iterator<UploadDataItem> iterator = dataItems.iterator(); iterator.hasNext(); ) {
            UploadDataItem f = iterator.next();
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Overall File selection progress : " + taskTracker.getProgressPercentage());
            }
            if(!calculateChecksumOnFile(taskTracker, f)) {
                iterator.remove();
            }
        }
        taskTracker.markComplete();
        progressTracker.markComplete();
    }

    private boolean calculateChecksumOnFile(DividableProgressTracker overallChecksumCalcTask, UploadDataItem dataItem) {
        if(BuildConfig.DEBUG) {
            Log.w(TAG, "Upload Fragment Passed URI: " + dataItem.getUri());
        }
        DividableProgressTracker fileChecksumTracker = overallChecksumCalcTask.addChildTask("file checksum calculation", 100, dataItem.getDataLength());
        boolean success = false;
        try {
            dataItem.calculateDataHashCode(getContext(), new TrackerUpdatingProgressListener(fileChecksumTracker));
            success = true;
        } catch (Md5SumUtils.Md5SumException e) {
            Logging.recordException(e);
        } catch(SecurityException secException) {
            DisplayUtils.runOnUiThread(() -> getOwner().getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getContext().getString(R.string.sorry_file_unusable_as_app_shared_from_does_not_provide_necessary_permissions)));
        } finally {
            fileChecksumTracker.markComplete();
        }
        return success;
    }

    private long getTotalImportedFileBytes(List<UploadDataItem> selectedFolderItems) {
        long totalImportedFileBytes = 0;
        for (UploadDataItem item : selectedFolderItems) {
            totalImportedFileBytes += item.getDataLength();
        }
        return totalImportedFileBytes;
    }

}
