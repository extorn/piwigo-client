package delit.piwigoclient.business;

import android.content.Context;
import android.content.SharedPreferences;

import delit.piwigoclient.R;

public class AlbumViewPreferences extends BaseAlbumViewPreferences {

    public static boolean isAutoDriveSlideshow(SharedPreferences prefs, Context context) {
        return prefs.getBoolean(context.getString(R.string.preference_gallery_slideshow_auto_drive_key), context.getResources().getBoolean(R.bool.preference_gallery_slideshow_auto_drive_default));
    }

    public static long getAutoDriveDelayMillis(SharedPreferences prefs, Context context) {
        return prefs.getInt(context.getString(R.string.preference_gallery_slideshow_auto_drive_delay_key), context.getResources().getInteger(R.integer.preference_gallery_slideshow_auto_drive_delay_default));
    }

    public static long getAutoDriveVideoDelayMillis(SharedPreferences prefs, Context context) {
        return prefs.getInt(context.getString(R.string.preference_gallery_slideshow_auto_drive_video_delay_key), context.getResources().getInteger(R.integer.preference_gallery_slideshow_auto_drive_video_delay_default));
    }

}
