package delit.piwigoclient.ui.util;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.MimeTypeFilter;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.HashMap;

import delit.libs.core.util.Logging;
import delit.libs.ui.OwnedSafeAsyncTask;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.util.IOUtils;
import delit.libs.util.progress.TaskProgressTracker;
import delit.piwigoclient.R;
import delit.piwigoclient.database.AppSettingsViewModel;
import delit.piwigoclient.ui.common.MyActivity;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;
import delit.piwigoclient.ui.file.FolderItem;
import delit.piwigoclient.ui.upload.AbstractUploadFragment;

public class SharedFilesIntentProcessingTask<T extends MyActivity<T>> extends OwnedSafeAsyncTask<T, Void, Integer, Void> {

    private static final String TAG = "SharedFilesIntentParser";
    private final AppSettingsViewModel appSettingsViewModel;
    private final int fileSelectionEventId;
    private final Intent intent;
    private String[] acceptedMimeTypes = new String[]{"image/*", "video/*", "application/pdf", "application/zip"};

    public SharedFilesIntentProcessingTask(T parent, int fileSelectionEventId, Intent intent) {
        super(parent);
        withContext(parent);
        this.intent = intent;
        this.fileSelectionEventId = fileSelectionEventId;
        ViewModelStoreOwner viewModelProvider = DisplayUtils.getViewModelStoreOwner(parent);
        appSettingsViewModel = new ViewModelProvider(viewModelProvider).get(AppSettingsViewModel.class);
    }

    public void setAcceptedMimeTypes(String[] acceptedMimeTypes) {
        this.acceptedMimeTypes = acceptedMimeTypes;
    }

    @Override
    protected void onPreExecuteSafely() {
        super.onPreExecuteSafely();
        getOwner().getUiHelper().showProgressIndicator(getOwner().getString(R.string.progress_importing_files),0);
    }

    @Override
    protected Void doInBackgroundSafely(Void... objects) {
        SentFilesResult sentFilesResult = findSentFilesWithinIntent(intent);
        if(sentFilesResult != null) {
            processSharedFilesInBackground(sentFilesResult);
        }
        return null;
    }

    protected void processSharedFilesInBackground(SentFilesResult sentFilesResult) {
        // this activity was invoked from another application
        FileSelectionCompleteEvent evt = new FileSelectionCompleteEvent(fileSelectionEventId, -1).withFolderItems(sentFilesResult.getSentFiles());
        EventBus.getDefault().postSticky(evt);
    }

    @Override
    protected void onProgressUpdateSafely(Integer... progress) {
        super.onProgressUpdateSafely(progress);
        try {
            getOwner().getUiHelper().showProgressIndicator(getOwner().getString(R.string.progress_importing_files),progress[0]);
        } catch(NullPointerException e) {
            Logging.log(Log.ERROR, TAG, "Unable to report progress. Likely not attached");
            Logging.recordException(e);
        }

    }

    @Override
    protected void onPostExecuteSafely(Void params) {
        getOwner().getUiHelper().hideProgressIndicator();
    }

    /**
     * @param intent to check for sent files.
     * @return null ONLY if the Intent was not intended to send any files.
     */
    private @Nullable
    SentFilesResult findSentFilesWithinIntent(@NonNull Intent intent) {
        // Get intent, action and MIME type
        String action = intent.getAction();
        String type = intent.getType();

        if(canSearchForFilesInIntent(action, type)) {
            return searchIntentForFiles(intent);
        } else {
            getOwner().getUiHelper().showDetailedMsg(R.string.alert_error, getContext().getString(R.string.alert_error_unable_to_handle_shared_mime_type, type));
        }
        return null;
    }

    protected boolean canSearchForFilesInIntent(String action, String type) {
        if (Intent.ACTION_SEND.equals(action) && type != null) {
            return !type.startsWith("*/");
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            // type is */* if it contains a mixture of file types
            return type.equals("*/*") || null != MimeTypeFilter.matches(type, acceptedMimeTypes);
        } else return "application/octet-stream".equals(type);
    }

    private @NonNull
    SentFilesResult searchIntentForFiles(Intent intent) {

        SentFilesResult result;

        ClipData clipData = intent.getClipData();
        if(clipData != null && clipData.getItemCount() > 0) {
            result = tryToExtractFilesFromClipData(intent, clipData);
        } else {
            result = tryToExtractFilesFromStreamExtra(intent);
        }
        return result;
    }

    @NonNull
    private SentFilesResult tryToExtractFilesFromStreamExtra(Intent intent) {
        SentFilesResult result;// process the extra stream data
        try {
            ArrayList<Uri> imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            result = processFilesFound(intent, imageUris);
            intent.removeExtra(Intent.EXTRA_STREAM);
        } catch(ClassCastException e) {
            Uri sharedUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            result = new SentFilesResult(1);
            if (sharedUri != null) {
                String mimeType = getContext().getContentResolver().getType(sharedUri);
                handleSentImage(sharedUri, mimeType, result);
            }
            intent.removeExtra(Intent.EXTRA_STREAM);
        }
        return result;
    }

    @NonNull
    private SentFilesResult tryToExtractFilesFromClipData(Intent intent, ClipData clipData) {
        SentFilesResult result;// process clip data
        result = new SentFilesResult(clipData.getItemCount());
//            String mimeType = clipData.getDescription().getMimeTypeCount() == 1 ? clipData.getDescription().getMimeType(0) : null;
        TaskProgressTracker fileImportTracker = new TaskProgressTracker(clipData.getItemCount(), new ProgressListener());
        boolean canTakePermission = IOUtils.allUriFlagsAreSet(intent.getFlags(), IOUtils.URI_PERMISSION_READ);
        for(int i = 0; i < clipData.getItemCount(); i++) {
            try {
                ClipData.Item sharedItem = clipData.getItemAt(i);
                Uri sharedUri = sharedItem.getUri();
                if (sharedUri != null) {
                    String mimeType = getContext().getContentResolver().getType(sharedUri);
                    handleSentImage(sharedUri, mimeType, result);
                    if (canTakePermission) {
                        try {
                            appSettingsViewModel.takePersistableUriPermissions(getContext(), sharedUri, IOUtils.URI_PERMISSION_READ, AbstractUploadFragment.URI_PERMISSION_CONSUMER_ID_FOREGROUND_UPLOAD, getContext().getString(R.string.uri_permission_justification_to_upload));
                            result.addPermissionsGranted(sharedUri, IOUtils.URI_PERMISSION_READ);
                        } catch (SecurityException e) {
                            Logging.log(Log.DEBUG, TAG, "No persistable permission available for uri %1$s", sharedUri);
                        }
                    }
                }
            } finally {
                fileImportTracker.incrementWorkDone(1);
            }
        }
        intent.setClipData(null);
        return result;
    }

    private @NonNull
    SentFilesResult processFilesFound(@NonNull Intent intent, @Nullable ArrayList<Uri> imageUris) {
        SentFilesResult result;
        String[] mimeTypes = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mimeTypes = intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES);
        }
        if (imageUris != null) {
            result = new SentFilesResult(imageUris.size());
            TaskProgressTracker fileImportTracker = new TaskProgressTracker(imageUris.size(), new ProgressListener());
            int i = 0;
            for (Uri imageUri : imageUris) {
                try {
                    String mimeType;
                    if (mimeTypes != null && mimeTypes.length >= i) {
                        mimeType = mimeTypes[i];
                        i++;
                    } else {
                        mimeType = intent.getType();
                    }
                    if (imageUri != null) {
                        handleSentImage(imageUri, mimeType, result);
                    }
                } finally {
                    fileImportTracker.incrementWorkDone(1);
                }
            }
        } else {
            result = tryToExtractFilesFromIntentData(intent);
        }
        return result;
    }

    @NonNull
    private SentFilesResult tryToExtractFilesFromIntentData(@NonNull Intent intent) {
        SentFilesResult result;
        String mimeType = intent.getType();
        Uri sharedUri = intent.getData();
        if(sharedUri != null) {
            boolean canTakePermission = IOUtils.allUriFlagsAreSet(intent.getFlags(), IOUtils.URI_PERMISSION_READ);
            TaskProgressTracker fileImportTracker = new TaskProgressTracker(1, new ProgressListener());
            result = new SentFilesResult(1);
            handleSentImage(sharedUri, mimeType, result);
            if(canTakePermission) {
                try {
                    appSettingsViewModel.takePersistableUriPermissions(getContext(), sharedUri, IOUtils.URI_PERMISSION_READ, AbstractUploadFragment.URI_PERMISSION_CONSUMER_ID_FOREGROUND_UPLOAD, getContext().getString(R.string.uri_permission_justification_to_upload));
                    result.addPermissionsGranted(sharedUri, IOUtils.URI_PERMISSION_READ);
                } catch(SecurityException e) {
                    Logging.log(Log.DEBUG, TAG, "No persistable permission available for uri %1$s", sharedUri);
                }
            }
            fileImportTracker.markComplete();
            intent.setData(null);
        } else {
            result = new SentFilesResult(0);
        }
        return result;
    }

    private void handleSentImage(@NonNull Uri sharedUri, String mimeType, @NonNull SentFilesResult filesToUpload) {
        filesToUpload.add(sharedUri);
    }

    private static class SentFilesResult {

        private final ArrayList<Uri> sentFiles;
        private final HashMap<Uri, Integer> sentFilesAndPermissions;

        public SentFilesResult(int sentFilesCount) {
            this.sentFiles = new ArrayList<>(sentFilesCount);
            this.sentFilesAndPermissions = new HashMap<>(sentFilesCount);
        }

        public ArrayList<FolderItem> getSentFiles() {
            ArrayList<FolderItem> items = new ArrayList<>(sentFiles.size());
            for(Uri uri : sentFiles) {
                FolderItem fi = new FolderItem(uri);
                Integer perms = sentFilesAndPermissions.get(uri);
                if(perms != null) {
                    fi.setPermissionsGranted(perms);
                }
                items.add(fi);
            }
            return items;
        }

        public void addPermissionsGranted(Uri uri, int permissions) {
            sentFilesAndPermissions.put(uri, permissions);
        }

        public void add(Uri sharedUri) {
            sentFiles.add(sharedUri);
        }
    }

    protected class ProgressListener implements delit.libs.util.progress.ProgressListener {
        @Override
        public void onProgress(@FloatRange(from = 0, to = 1) double percent) {
            publishProgress((int) Math.rint(percent * 100));
        }

        @Override
        public double getUpdateStep() {
            return 0.01;//1%
        }
    }
}
