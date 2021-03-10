package delit.piwigoclient.piwigoApi.upload.action;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.upload.ForegroundPiwigoUploadService;
import delit.piwigoclient.piwigoApi.upload.actors.UploadNotificationManager;
import delit.piwigoclient.ui.UploadActivity;

public class ForegroundUploadNotificationManager extends UploadNotificationManager {

    private static final int FOREGROUND_UPLOAD_NOTIFICATION_ID = 3;

    public ForegroundUploadNotificationManager(Context context) {
        super(context);
    }

    @Override
    public int getNotificationId() {
        return FOREGROUND_UPLOAD_NOTIFICATION_ID;
    }

    @Override
    protected String getNotificationTitle() {
        return getContext().getString(R.string.notification_title_foreground_upload_service);
    }

    @Override
    protected NotificationCompat.Builder buildNotification(String text) {
        NotificationCompat.Builder builder = super.buildNotification(text);

        Intent cancelIntent = new Intent(ForegroundPiwigoUploadService.ACTION_STOP);
        cancelIntent.setPackage(getContext().getPackageName());
        PendingIntent pendingCancelIntent = PendingIntent.getBroadcast(getContext(), 0, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        // add a cancel button to cancel the upload if clicked
        builder.addAction(new NotificationCompat.Action(R.drawable.ic_cancel_black, getContext().getString(R.string.button_cancel), pendingCancelIntent));

        Intent contentIntent = new Intent(getContext(), UploadActivity.class);
        // open the upload activity if notification clicked
        builder.setContentIntent(PendingIntent.getActivity(getContext(), 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT));
        return builder;
    }
}
