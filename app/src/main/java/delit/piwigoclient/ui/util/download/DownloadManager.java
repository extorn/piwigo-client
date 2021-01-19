package delit.piwigoclient.ui.util.download;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.documentfile.provider.DocumentFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.util.IOUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AppPreferences;
import delit.piwigoclient.piwigoApi.handlers.ImageGetToFileHandler;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.CancelDownloadEvent;
import delit.piwigoclient.ui.events.DownloadFileRequestEvent;
import delit.piwigoclient.ui.file.DocumentFileFilter;
import delit.piwigoclient.ui.file.RegexpDocumentFileFilter;
import delit.piwigoclient.util.MyDocumentProvider;

public class DownloadManager<T> implements Parcelable, DownloadAction.DownloadActionListener, DownloadedFileNotificationGenerator.DownloadTargetLoadListener<DownloadedFileNotificationGenerator<T>> {

    public static final String NOTIFICATION_GROUP_DOWNLOADS = "Downloads";

    private static final String TAG = "DownloadManager";
    //TODO move the download mechanism into a background service so it isn't cancelled if the user leaves the app.
    private ArrayList<DownloadFileRequestEvent> queuedDownloads = new ArrayList<>();
    private ArrayList<DownloadFileRequestEvent> activeDownloads = new ArrayList<>(1);
    private UIHelper<T> uiHelper;
    //TODO we don't store these notification handlers so possibly notifications may not get created.
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")// Don't query them as it's just to stop garbage collection
    private final ArrayList<DownloadedFileNotificationGenerator<T>> thumbNotificationGenerators = new ArrayList<>();

    public DownloadManager(UIHelper<T> uiHelper) {
        this.uiHelper = uiHelper;
    }

    public void withUiHelper(UIHelper<T> uiHelper) {
        this.uiHelper = uiHelper;
    }

    @Override
    public void onDownloadActionSuccess(DownloadFileRequestEvent downloadEvent, Uri downloadedToUri) {
        notifyUserFileDownloadComplete(uiHelper, downloadedToUri);
        processDownloadEvent(downloadEvent);
    }

    @Override
    public void onDownloadActionFailure() {
        removeActionDownloadEvent();
        scheduleNextDownloadIfPresent();
    }

    protected DownloadManager(Parcel in) {
        queuedDownloads = in.createTypedArrayList(DownloadFileRequestEvent.CREATOR);
        activeDownloads = in.createTypedArrayList(DownloadFileRequestEvent.CREATOR);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(queuedDownloads);
        dest.writeTypedList(activeDownloads);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<DownloadManager<?>> CREATOR = new Creator<DownloadManager<?>>() {
        @Override
        public DownloadManager<?> createFromParcel(Parcel in) {
            return new DownloadManager<>(in);
        }

        @Override
        public DownloadManager<?>[] newArray(int size) {
            return new DownloadManager[size];
        }
    };

    private void scheduleNextDownloadIfPresent() {
        synchronized (this) {
            if (!queuedDownloads.isEmpty() && activeDownloads.isEmpty()) {
                processNextQueuedDownloadEvent();
            }
        }
    }

    private @Nullable DownloadFileRequestEvent removeActionDownloadEvent() {
        synchronized (this) {
            if (activeDownloads.isEmpty()) {
                return null;
            }
            return activeDownloads.remove(0);
        }
    }

    private void processDownloadEvent(DownloadFileRequestEvent event) {
        DownloadFileRequestEvent.FileDetails fileDetail = event.getNextFileDetailToDownload();
        if(fileDetail != null) {
            if (fileDetail.getLocalFileToCopy() != null) {
                copyLocallyCachedFileToDownloadFolder(event, fileDetail);
            } else {
                downloadRemoteFileToDownloadFolder(event, fileDetail);
            }
        } else {
            // all items downloaded - process them as needed.
            onFileDownloadEventProcessed(event);
        }
    }

    private void downloadRemoteFileToDownloadFolder(DownloadFileRequestEvent event, DownloadFileRequestEvent.FileDetails fileDetail) {
        // invoke a download of this file
        String mimeType = IOUtils.getMimeType(getContext(), Uri.parse(fileDetail.getRemoteUri()));
        Logging.log(Log.DEBUG, TAG, "RFD: Mime type : " + mimeType + " retrieved from remote uri : " + fileDetail.getRemoteUri());
        if(mimeType == null) {
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(IOUtils.getFileExt(fileDetail.getOutputFilename()));
            Logging.log(Log.DEBUG, TAG, "RFD: Mime type : " + mimeType + " retrieved from output uri : " + fileDetail.getOutputFilename());
        }
        if(mimeType == null) {
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(IOUtils.getFileExt(fileDetail.getResourceName()));
            Logging.log(Log.DEBUG, TAG, "RFD: Mime type : " + mimeType + " retrieved from resource name : " + fileDetail.getResourceName());
        }
        if(mimeType == null) {
            Logging.log(Log.ERROR, TAG, "Unable to establish mime type for download");
            onFileDownloadEventProcessed(event); // sink the event from the queue to allow others to download
            processNextQueuedDownloadEvent();
        } else {
            String downloadToFile = fileDetail.getOutputFilename();
            boolean hasFileExt = downloadToFile.lastIndexOf('.') > downloadToFile.length() - 5;
            if (!hasFileExt) {
                String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                Logging.log(Log.ERROR, TAG, "Added mime type for download " + mimeType);
                downloadToFile = downloadToFile + '.' + ext;
            }
            DocumentFile destinationFile = getDestinationFile(mimeType, downloadToFile);
            if(destinationFile == null) {
                getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_please_select_download_folder_and_retry));
                removeActionDownloadEvent(); // sink the event from the queue to allow others to download
                processNextQueuedDownloadEvent();
            } else {
                event.setRequestId(getUiHelper().invokeActiveServiceCall(getString(R.string.progress_downloading), new ImageGetToFileHandler(fileDetail.getRemoteUri(), destinationFile.getUri()), new DownloadAction(event, this)));
            }
        }
    }

    private Context getContext() {
        return uiHelper.getAppContext();
    }

    public void onFileDownloadEventProcessed(DownloadFileRequestEvent event) {
        //DownloadFileRequestEvent event =
        removeActionDownloadEvent(); // we've got the event, so ignore the return
        if (event.isShareDownloadedWithAppSelector()) {
            Set<Uri> destinationFiles = new HashSet<>(event.getFileDetails().size());
            for(DownloadFileRequestEvent.FileDetails fileDetail : event.getFileDetails()) {
                destinationFiles.add(fileDetail.getDownloadedFile());
            }
            shareFilesWithOtherApps(getContext(), destinationFiles);
        } else {
            // Don't do this because it will clear the individual notifications.
//            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, getUiHelper().getDefaultNotificationChannelId())
//                    //                    .setSmallIcon(R.drawable.ic_notifications_black_24dp)
//                    .setSmallIcon(R.drawable.ic_star_yellow_24dp)
//                    .setCategory(NotificationCompat.CATEGORY_EVENT)
//                    .setContentTitle(this.getString(R.string.notification_download_event))
//                    .setContentText(getString(R.string.alert_image_download_complete_message))
//                    .setGroup(NOTIFICATION_GROUP_DOWNLOADS)
//                    .setGroupSummary(true)
//                    .setAutoCancel(true);
//            getUiHelper().showNotification(TAG, DownloadTarget.notificationId.getAndIncrement(), mBuilder.build());
            if(event.getFileDetails().size() > 1) {
                getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_image_download_complete_message));
            }
        }
    }

    private UIHelper<T> getUiHelper() {
        return uiHelper;
    }

    private void notifyUserFileDownloadComplete(final UIHelper<T> uiHelper, final Uri downloadedFile) {
        //uiHelper.showDetailedMsg(R.string.alert_image_download_title, uiHelper.getContext().getString(R.string.alert_image_download_complete_message));
        if(BuildConfig.DEBUG) {
            Log.e(TAG, "Downloaded File - Generating Thumbnail for " + downloadedFile);
        }
        DownloadedFileNotificationGenerator generator = new DownloadedFileNotificationGenerator(uiHelper, this, downloadedFile);
        generator.execute();
        thumbNotificationGenerators.add(generator);
    }

    private void shareFilesWithOtherApps(Context context, final Set<Uri> filesToShare) {
//        File sharedFolder = new File(getContext().getExternalCacheDir(), "shared");
//        sharedFolder.mkdir();
//        File tmpFile = File.createTempFile(resourceFilename, resourceFileExt, sharedFolder);
//        tmpFile.deleteOnExit();

        //Send multiple seems essential to allow to work with the other apps. Not clear why.
        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        MimeTypeMap map = MimeTypeMap.getSingleton();

        ArrayList<Uri> urisToShare = new ArrayList<>(filesToShare.size());
        ArrayList<String> mimesOfSharedUris = new ArrayList<>(filesToShare.size());

        for(Uri fileToShare : filesToShare) {
            String ext = IOUtils.getFileExt(getContext(), fileToShare);
            if(ext != null) {
                String mimeType = map.getMimeTypeFromExtension(ext.toLowerCase());
                mimesOfSharedUris.add(mimeType);
            } else {
                Logging.log(Log.WARN, TAG, "Sharing file with another app, unable to tell it the mime type as file has no extension (" + fileToShare + ")");
            }
            urisToShare.add(fileToShare);
        }
        if(mimesOfSharedUris.size() == 1) {
            intent.setType(mimesOfSharedUris.get(0));
        } else {
            intent.setType("application/octet-stream");
        }

        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, urisToShare);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            intent.putStringArrayListExtra(Intent.EXTRA_MIME_TYPES, mimesOfSharedUris);
        }
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        }
        context.startActivity(Intent.createChooser(intent, getString(R.string.open_files)));
    }

    public void onEvent(CancelDownloadEvent event) {
        synchronized (this) {
            Iterator<DownloadFileRequestEvent> iter = activeDownloads.iterator();
            while (iter.hasNext()) {
                DownloadFileRequestEvent evt = iter.next();
                if (evt.getRequestId() == event.messageId) {
                    iter.remove();
                    break;
                }
            }

        }
    }

    protected void copyLocallyCachedFileToDownloadFolder(DownloadFileRequestEvent event, DownloadFileRequestEvent.FileDetails fileDetail) {
        // copy this local download cache to the destination.
        try {
            String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(IOUtils.getFileExt(fileDetail.getRemoteUri()));
            Logging.log(Log.DEBUG, TAG, "LFC: Mime type : " + mimeType + " retrieved from remote uri : " + fileDetail.getRemoteUri());
            if(mimeType == null) {
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(IOUtils.getFileExt(fileDetail.getOutputFilename()));
                Logging.log(Log.DEBUG, TAG, "LFC: Mime type : " + mimeType + " retrieved from output uri : " + fileDetail.getOutputFilename());
            }
            if(mimeType == null) {
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(IOUtils.getFileExt(fileDetail.getResourceName()));
                Logging.log(Log.DEBUG, TAG, "RFD: Mime type : " + mimeType + " retrieved from resource name : " + fileDetail.getResourceName());
            }
            if(mimeType == null) {
                Logging.log(Log.ERROR, TAG, "Unable to establish mime type for download");
                removeActionDownloadEvent(); // sink the event from the queue to allow others to download
                processNextQueuedDownloadEvent();
            } else {
                DocumentFile destFile = getDestinationFile(mimeType, fileDetail.getOutputFilename());
                if(destFile == null) {
                    getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_please_select_download_folder_and_retry));
                    removeActionDownloadEvent(); // sink the event from the queue to allow others to download
                    processNextQueuedDownloadEvent();
                } else {
                    IOUtils.copyDocumentUriDataToUri(getContext(), fileDetail.getLocalFileToCopy(), destFile.getUri());
                    Uri myDocUri = destFile.getUri();
                    if(!DocumentFile.isDocumentUri(getContext(), myDocUri)) {
                        myDocUri = DocumentsContract.buildDocumentUri(MyDocumentProvider.getAuthority(), destFile.getName());
                    }
//                    Uri mediaStoreUri = IOUtils.addFileToMediaStore(this, myDocUri);
//                    fileDetail.setDownloadedFile(mediaStoreUri);
                    fileDetail.setDownloadedFile(myDocUri);
                    notifyUserFileDownloadComplete(getUiHelper(), myDocUri);
                    processDownloadEvent(event);
                }
            }
        } catch (IOException e) {
            Logging.recordException(e);
            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_unable_to_copy_file_from_cache_pattern, e.getMessage()));
            removeActionDownloadEvent(); // sink the event from the queue to allow others to download
            processNextQueuedDownloadEvent();
        }
    }

    private @Nullable DocumentFile getDestinationFile(@NonNull String mimeType, @NonNull String outputFilename) {
        DocumentFile folder = AppPreferences.getAppDownloadFolder(getSharedPrefs(), getContext());
        if(folder == null) {
            return null;
        }
        String filenameWithoutExt = IOUtils.getFileNameWithoutExt(outputFilename);
        DocumentFile df = null;
        try {
            df = folder.createFile(mimeType, filenameWithoutExt);
        } catch(Throwable th) {
            Logging.log(Log.WARN, TAG, "Sinking intermittent exception");
            Logging.recordException(th);
        }
        if(df == null) {
            if (folder.findFile(outputFilename) != null) {

                int matchingFiles = DocumentFileFilter.filterDocumentFiles(folder.listFiles(), new RegexpDocumentFileFilter().withFilenamePattern("^" + filenameWithoutExt + "\\([\\d*]\\)\\.(?:.){1,5}$")).size();
                df = folder.createFile(mimeType, filenameWithoutExt + '(' + (matchingFiles + 1) + ')');
            }
        }
        return df;
    }

    private SharedPreferences getSharedPrefs() {
        return getUiHelper().getPrefs();
    }

    protected void processNextQueuedDownloadEvent() {
        synchronized (this) {
            DownloadFileRequestEvent nextEvent = queuedDownloads.remove(0);

            activeDownloads.add(nextEvent);
            processDownloadEvent(nextEvent);
        }
    }

    public void onEvent(DownloadFileRequestEvent event) {
        synchronized (this) {
            queuedDownloads.add(event);
            if (activeDownloads.size() == 0) {
                try {
                    processNextQueuedDownloadEvent();
                } catch(Exception e) {
                    Logging.recordException(e);
                    getUiHelper().showOrQueueEnhancedDialogQuestion(R.string.alert_error, getString(R.string.alert_error_starting_download), e.getMessage(), View.NO_ID, R.string.button_ok, new UIHelper.QuestionResultAdapter<>(getUiHelper()));
                    activeDownloads.remove(0);
                }
            } else {
                getUiHelper().showDetailedShortMsg(R.string.alert_information, getString(R.string.resource_queued_for_download, queuedDownloads.size()));
            }
        }
    }

    private String getString(@StringRes int stringResId, Object ... formatArgs) {
        return uiHelper.getAppContext().getString(stringResId, formatArgs);
    }

    @Override
    public void onDownloadTargetResult(DownloadedFileNotificationGenerator<T> generator, boolean success) {
        thumbNotificationGenerators.remove(generator);
    }
}
