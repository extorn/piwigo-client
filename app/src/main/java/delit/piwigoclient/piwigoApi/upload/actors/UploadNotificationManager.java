package delit.piwigoclient.piwigoApi.upload.actors;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import delit.piwigoclient.R;

public abstract class UploadNotificationManager {

    private final Context context;

    public UploadNotificationManager(Context context) {
        this.context = context;
    }

    public Context getContext() {
        return context;
    }

    abstract public int getNotificationId();

    abstract protected String getNotificationTitle();

    public void updateNotificationText(@StringRes int textRes, int progress) {
//        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder notificationBuilder = buildNotification(getContext().getString(textRes));
        notificationBuilder.setProgress(100, progress, false);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getContext());
        notificationManager.notify(getNotificationId(), notificationBuilder.build());
    }

    public void updateNotificationText(String message, boolean showIndeterminateProgress) {
//        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder notificationBuilder = buildNotification(message);
        if (showIndeterminateProgress) {
            notificationBuilder.setProgress(0, 0, true);
        }
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getContext());
        notificationManager.notify(getNotificationId(), notificationBuilder.build());
    }

    public void updateNotificationText(@StringRes int textRes, boolean showIndeterminateProgress) {
        updateNotificationText(getContext().getString(textRes), showIndeterminateProgress);
    }

    protected NotificationCompat.Builder buildNotification(String text) {
        NotificationCompat.Builder notificationBuilder = getNotificationBuilder();
//        notificationBuilder.setContentIntent(PendingIntent.getActivity(this, 0, new Intent(), 0));
        notificationBuilder.setContentTitle(getNotificationTitle())
                .setContentText(text);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // this is not a vector graphic
            notificationBuilder.setSmallIcon(R.drawable.ic_file_upload_black);
            notificationBuilder.setCategory("service");
        } else {
            notificationBuilder.setSmallIcon(R.drawable.ic_file_upload_black_24dp);
            notificationBuilder.setCategory(NotificationCompat.CATEGORY_SERVICE);
        }
        notificationBuilder.setAutoCancel(true);
//        .setTicker(getText(R.string.ticker_text))
        return notificationBuilder;
    }
    
    protected NotificationCompat.Builder getNotificationBuilder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannelIfNeeded();
        }
        return new NotificationCompat.Builder(getContext(), getDefaultNotificationChannelId());
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createNotificationChannelIfNeeded() {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getContext());
        NotificationChannel channel = notificationManager.getNotificationChannel(getDefaultNotificationChannelId());
        int importance = NotificationManager.IMPORTANCE_LOW; // no noise for low.
        if (channel == null || channel.getImportance() != importance) {
            String name = getContext().getString(R.string.app_name);
            channel = new NotificationChannel(getDefaultNotificationChannelId(), name, importance);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private String getDefaultNotificationChannelId() {
        return getContext().getString(R.string.app_name) + "_UploadService";
    }

    public Notification getNotification() {
        NotificationCompat.Builder notificationBuilder = buildNotification(getContext().getString(R.string.notification_message_upload_service));
        notificationBuilder.setProgress(0, 0, true);
        return notificationBuilder.build();
    }


}
