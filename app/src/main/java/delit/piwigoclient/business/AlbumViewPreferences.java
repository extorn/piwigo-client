package delit.piwigoclient.business;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import delit.piwigoclient.R;
import delit.piwigoclient.util.DisplayUtils;

public class AlbumViewPreferences {

    public static int getAlbumsToDisplayPerRow(Activity activity, SharedPreferences prefs) {
        int defaultColumns = getDefaultAlbumColumnCount(activity, activity.getResources().getConfiguration().orientation);
        if (activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return getPreferredColumnsOfAlbumsLandscape(prefs, activity, defaultColumns);
        } else {
            return getPreferredColumnsOfAlbumsPortrait(prefs, activity, defaultColumns);
        }
    }

    public static int getDefaultImagesColumnCount(Activity activity, int screenOrientation) {
        return DisplayUtils.getDefaultColumnCount(activity, screenOrientation, 1);
    }

    public static int getDefaultAlbumColumnCount(Activity activity, int screenOrientation) {
        return DisplayUtils.getDefaultColumnCount(activity, screenOrientation, 2);
    }

    private static int getPreferredColumnsOfAlbumsLandscape(SharedPreferences prefs, Context context, int defaultColumns) {
        return prefs.getInt(context.getString(R.string.preference_gallery_albums_preferredColumnsLandscape_key), defaultColumns);
    }

    private static int getPreferredColumnsOfAlbumsPortrait(SharedPreferences prefs, Context context, int defaultColumns) {
        return prefs.getInt(context.getString(R.string.preference_gallery_albums_preferredColumnsPortrait_key), defaultColumns);
    }

    public static int getImagesToDisplayPerRow(Activity activity, SharedPreferences prefs) {
        int defaultColumns = getDefaultImagesColumnCount(activity, activity.getResources().getConfiguration().orientation);
        if (activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            defaultColumns = prefs.getInt(activity.getString(R.string.preference_gallery_images_preferredColumnsLandscape_key), defaultColumns);
        } else {
            defaultColumns = prefs.getInt(activity.getString(R.string.preference_gallery_images_preferredColumnsPortrait_key), defaultColumns);
        }
        return Math.max(1, defaultColumns);
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

    public static boolean isVideoPlaybackEnabled(SharedPreferences prefs, Context context) {
        return prefs.getBoolean(context.getString(R.string.preference_gallery_enable_video_playback_key), context.getResources().getBoolean(R.bool.preference_gallery_enable_video_playback_default));
    }

    public static String getPreferredSlideshowImageSize(SharedPreferences prefs, Context context) {
        return prefs.getString(context.getString(R.string.preference_gallery_item_slideshow_image_size_key), context.getString(R.string.preference_gallery_item_slideshow_image_size_default));
    }

    public static boolean isIncludeVideosInSlideshow(SharedPreferences prefs, Context context) {
        return prefs.getBoolean(context.getString(R.string.preference_gallery_include_videos_in_slideshow_key), context.getResources().getBoolean(R.bool.preference_gallery_include_videos_in_slideshow_default));
    }
}
