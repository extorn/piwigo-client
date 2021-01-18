package delit.piwigoclient.ui.util.download;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.util.concurrent.atomic.AtomicInteger;

import delit.libs.ui.util.DisplayUtils;
import delit.libs.util.IOUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.ui.AbstractMainActivity;
import delit.piwigoclient.ui.PicassoFactory;
import delit.piwigoclient.ui.common.UIHelper;

public class DownloadedFileNotificationGenerator<T> implements Target {

    protected static final AtomicInteger notificationId = new AtomicInteger(100);
    private static final String TAG = "DownloadTarget";

    private final UIHelper<T> uiHelper;
    private final Uri downloadedFile;
    private final DownloadTargetLoadListener<T> listener;

    public interface DownloadTargetLoadListener<T> {
        void onDownloadTargetResult(DownloadedFileNotificationGenerator<T> generator, boolean success);
    }

    public Uri getDownloadedFile() {
        return downloadedFile;
    }

    public DownloadedFileNotificationGenerator(@NonNull UIHelper<T> uiHelper, @NonNull DownloadTargetLoadListener<T> loadListener, @NonNull Uri downloadedFile) {
        this.uiHelper = uiHelper;
        this.listener = loadListener;
        this.downloadedFile = downloadedFile;
    }

    public void execute() {
        PicassoFactory.getInstance().getPicassoSingleton(uiHelper.getAppContext()).load(downloadedFile).error(R.drawable.ic_file_gray_24dp).resize(256,256).centerInside().into(this);
    }

    private Context getContext() {
        return uiHelper.getAppContext();
    }

    @Override
    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
        if(BuildConfig.DEBUG) {
            Log.d(TAG, "Generated bitmap from : " + downloadedFile.getPath());
        }
        DisplayUtils.runOnUiThread(() -> buildAndShowNotification(bitmap));
        listener.onDownloadTargetResult(this,true);
    }

    private void buildAndShowNotification(Bitmap bitmap) {

        Intent notificationIntent;

        //        if(openImageNotFolder) {
        notificationIntent = new Intent(Intent.ACTION_VIEW);
        // Action on click on notification
        MimeTypeMap map = MimeTypeMap.getSingleton();
        Uri shareFileUri = AbstractMainActivity.toContentUri(getContext(), downloadedFile);
        String ext = MimeTypeMap.getFileExtensionFromUrl(downloadedFile.toString());
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
                .setContentText(IOUtils.getFilename(getContext(), downloadedFile))
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
    public void onBitmapFailed(Drawable errorDrawable) {
        if(BuildConfig.DEBUG) {
            Log.d(TAG, "Failed to generate bitmap from : " + downloadedFile.getPath());
        }
        Bitmap errorBitmap = DisplayUtils.getBitmap(errorDrawable);
        DisplayUtils.runOnUiThread(() -> buildAndShowNotification(errorBitmap));
        listener.onDownloadTargetResult(this,false);
    }

    @Override
    public void onPrepareLoad(Drawable placeHolderDrawable) {
        // Don't need to do anything before loading image
        if(BuildConfig.DEBUG) {
            Log.d(TAG, "About to generate bitmap from : " + downloadedFile.getPath());
        }
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if(obj instanceof DownloadedFileNotificationGenerator) {
            DownloadedFileNotificationGenerator<?> other = (DownloadedFileNotificationGenerator<?>) obj;
            return downloadedFile.equals(other.downloadedFile);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return downloadedFile.hashCode();
    }
}
