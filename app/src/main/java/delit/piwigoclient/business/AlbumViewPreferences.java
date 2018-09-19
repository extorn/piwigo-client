package delit.piwigoclient.business;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.util.DisplayMetrics;

import delit.piwigoclient.R;

public class AlbumViewPreferences {

    public static int getAlbumsToDisplayPerRow(Activity activity, SharedPreferences prefs) {
        int defaultColumns = getDefaultAlbumColumnCount(activity, activity.getResources().getConfiguration().orientation);
        if (activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return getPreferredColumnsOfAlbumsLandscape(prefs, activity, defaultColumns);
        } else {
            return getPreferredColumnsOfAlbumsPortrait(prefs, activity, defaultColumns);
        }
    }

    private static float getScreenWidthInches(Activity activity) {
        DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        return (float) dm.widthPixels / dm.xdpi;
    }

    private static float getScreenHeightInches(Activity activity) {
        DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        return (float) dm.heightPixels / dm.xdpi;
    }

    public static int getDefaultImagesColumnCount(Activity activity, int screenOrientation) {

        float screenWidth;
        if (screenOrientation == Configuration.ORIENTATION_PORTRAIT) {
            screenWidth = getScreenWidthInches(activity);
        } else {
            screenWidth = getScreenHeightInches(activity);
        }
        int columnsToShow = Math.max(1, Math.round(screenWidth)); // allow a minimum of 1 inch per column
        return Math.max(1, columnsToShow); // never allow less than one column by default.
    }

    public static int getDefaultAlbumColumnCount(Activity activity, int screenOrientation) {
        float screenWidth;
        if (screenOrientation == Configuration.ORIENTATION_PORTRAIT) {
            screenWidth = getScreenWidthInches(activity);
        } else {
            screenWidth = getScreenHeightInches(activity);
        }
        int columnsToShow = Math.max(1, Math.round(screenWidth / 2)); // allow a minimum of 2 inch per column
        return Math.max(1, columnsToShow); // never allow less than one column by default.
    }

    private static int getPreferredColumnsOfAlbumsLandscape(SharedPreferences prefs, Context context, int defaultColumns) {
        return prefs.getInt(context.getString(R.string.preference_gallery_albums_preferredColumnsLandscape_key), defaultColumns);
    }

    private static int getPreferredColumnsOfAlbumsPortrait(SharedPreferences prefs, Context context, int defaultColumns) {
        return prefs.getInt(context.getString(R.string.preference_gallery_albums_preferredColumnsPortrait_key), defaultColumns);
    }

    public static int getImagesToDisplayPerRow(Activity activity, SharedPreferences prefs) {
        int mColumnCount = getDefaultImagesColumnCount(activity, activity.getResources().getConfiguration().orientation);
        if (activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mColumnCount = prefs.getInt(activity.getString(R.string.preference_gallery_images_preferredColumnsLandscape_key), mColumnCount);
        } else {
            mColumnCount = prefs.getInt(activity.getString(R.string.preference_gallery_images_preferredColumnsPortrait_key), mColumnCount);
        }
        return Math.max(1, mColumnCount);
    }

    public static boolean isShowAlbumThumbnailsZoomed(SharedPreferences prefs, Context context) {
        return prefs.getBoolean(context.getString(R.string.preference_gallery_show_album_thumbnail_zoomed_key), context.getResources().getBoolean(R.bool.preference_gallery_show_album_thumbnail_zoomed_default));
    }

    public static boolean isShowResourceNames(SharedPreferences prefs, Context context) {
        return prefs.getBoolean(context.getString(R.string.preference_gallery_show_image_name_key), context.getResources().getBoolean(R.bool.preference_gallery_show_image_name_default));
    }

    public static int getRecentlyAlteredMaxAgeMillis(SharedPreferences prefs, Context context) {
        return prefs.getInt(context.getString(R.string.preference_gallery_recentlyAlteredAgeMillis_key), context.getResources().getInteger(R.integer.preference_gallery_recentlyAlteredAgeMillis_default));
    }

    public static String getPreferredResourceThumbnailSize(SharedPreferences prefs, Context context) {
        return prefs.getString(context.getString(R.string.preference_gallery_item_thumbnail_size_key), context.getString(R.string.preference_gallery_item_thumbnail_size_default));
    }

    public static String getPreferredAlbumThumbnailSize(SharedPreferences prefs, Context context) {
        return prefs.getString(context.getString(R.string.preference_gallery_album_thumbnail_size_key), context.getString(R.string.preference_gallery_album_thumbnail_size_default));
    }

    public static String getKnownMultimediaExtensions(SharedPreferences prefs, Context context) {
        return prefs.getString(context.getString(R.string.preference_piwigo_playable_media_extensions_key), context.getString(R.string.preference_piwigo_playable_media_extensions_default));
    }

    public static String getResourceSortOrder(SharedPreferences prefs, Context context) {
        return prefs.getString(context.getString(R.string.preference_gallery_sortOrder_key), context.getString(R.string.preference_gallery_sortOrder_default));
    }

    public static int getResourceRequestPageSize(SharedPreferences prefs, Context context) {
        return prefs.getInt(context.getString(R.string.preference_album_request_pagesize_key), context.getResources().getInteger(R.integer.preference_album_request_pagesize_default));
    }
}
