package delit.piwigoclient.ui.util.download;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.concurrent.atomic.AtomicInteger;

import delit.libs.util.IOUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.ui.AbstractMainActivity;
import delit.piwigoclient.ui.common.UIHelper;

public class DownloadedFileNotificationGenerator<T> extends FileThumbnailGenerator<DownloadedFileNotificationGenerator<T>> {

    protected static final AtomicInteger notificationId = new AtomicInteger(100);
    private static final String TAG = "DnldFileNotifGen";
    private final UIHelper<T> uiHelper;

    public DownloadedFileNotificationGenerator(UIHelper<T> uiHelper, @NonNull DownloadTargetLoadListener<DownloadedFileNotificationGenerator<T>> loadListener, @NonNull Uri downloadedFile) {
        super(uiHelper.getAppContext(), loadListener, downloadedFile, new Point(256,256));
        this.uiHelper = uiHelper;
    }

    @Override
    protected void withLoadedThumbnail(Bitmap bitmap) {

        Intent notificationIntent;

        //        if(openImageNotFolder) {
        notificationIntent = new Intent(Intent.ACTION_VIEW);
        // Action on click on notification
        MimeTypeMap map = MimeTypeMap.getSingleton();
        Uri shareFileUri = AbstractMainActivity.toContentUri(getContext(), getDownloadedFile());
        String ext = MimeTypeMap.getFileExtensionFromUrl(getDownloadedFile().toString());
        String mimeType = map.getMimeTypeFromExtension(ext.toLowerCase());
        notificationIntent.setDataAndType(shareFileUri, mimeType);
        notificationIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            notificationIntent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        }

        //        } else {
        // N.B.this only works with a very select few android apps - folder browsing seemingly isn't a standard thing in android.
        //            notificationIntent = pkg Intent(Intent.ACTION_VIEW);
        //            Uri selectedUri = Uri.fromFile(downloadedFile.getParentFile());
        //            notificationIntent.setDataAndType(selectedUri, "resource/folder");
        //        }

        PendingIntent pendingIntent = PendingIntent.getActivity(getContext(), 0,
                notificationIntent, 0);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getContext(), uiHelper.getDefaultNotificationChannelId())
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setLargeIcon(bitmap)
                .setContentTitle(getContext().getString(R.string.notification_download_event))
                .setContentText(IOUtils.getFilename(getContext(), getDownloadedFile()))
                .setContentIntent(pendingIntent)
                .setGroup(DownloadManager.NOTIFICATION_GROUP_DOWNLOADS)
//                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_ALL)
                .setAutoCancel(true);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // this is not a vector graphic
            mBuilder.setSmallIcon(R.drawable.ic_notifications_black);
        } else {
            mBuilder.setSmallIcon(R.drawable.ic_notifications_black_24dp);
        }

        uiHelper.showNotification(TAG, notificationId.getAndIncrement(), mBuilder.build());
    }

    @Override
    protected void withErrorThumbnail(Bitmap bitmap) {
        withLoadedThumbnail(bitmap);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if(obj instanceof DownloadedFileNotificationGenerator) {
            DownloadedFileNotificationGenerator<?> other = (DownloadedFileNotificationGenerator<?>) obj;
            return getDownloadedFile().equals(other.getDownloadedFile());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getDownloadedFile().hashCode();
    }
}
