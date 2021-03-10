package delit.piwigoclient.piwigoApi.upload.action;

import android.content.Context;

import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.upload.actors.UploadNotificationManager;

public class BackgroundUploadNotificationManager extends UploadNotificationManager {

    private static final int BACKGROUND_UPLOAD_NOTIFICATION_ID = 2;

    public BackgroundUploadNotificationManager(Context context) {
        super(context);
    }

    @Override
    public int getNotificationId() {
        return BACKGROUND_UPLOAD_NOTIFICATION_ID;
    }

    @Override
    protected String getNotificationTitle() {
        return getContext().getString(R.string.notification_title_background_upload_service);
    }
}
