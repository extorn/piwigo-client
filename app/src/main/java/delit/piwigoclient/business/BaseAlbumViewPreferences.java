package delit.piwigoclient.business;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import delit.libs.ui.util.DisplayUtils;
import delit.piwigoclient.R;

public class BaseAlbumViewPreferences {
    public static int getAlbumsToDisplayPerRow(Activity activity, SharedPreferences prefs) {
        int defaultColumns = getDefaultAlbumColumnCount(activity, activity.getResources().getConfiguration().orientation);
        if (activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return getPreferredColumnsOfAlbumsLandscape(prefs, activity, defaultColumns);
        } else {
            return getPreferredColumnsOfAlbumsPortrait(prefs, activity, defaultColumns);
        }
    }

    public static int getDefaultImagesColumnCount(@NonNull Activity activity, int screenOrientation) {
        return DisplayUtils.getDefaultColumnCount(activity, screenOrientation, 1);
    }

    public static int getDefaultAlbumColumnCount(@NonNull Activity activity, int screenOrientation) {
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


    public static boolean isShowFileSizeShowingMessage(SharedPreferences prefs, Context context) {
        return prefs.getBoolean(context.getString(R.string.preference_gallery_show_file_size_showing_key), context.getResources().getBoolean(R.bool.preference_gallery_show_file_size_showing_default));
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

    public static String getResourceSortOrder(SharedPreferences prefs, Context context) {
        return prefs.getString(context.getString(R.string.preference_gallery_sort_order_key), context.getString(R.string.preference_gallery_sort_order_default));
    }

    public static boolean getResourceSortOrderInverted(SharedPreferences prefs, Context context) {
        return prefs.getBoolean(context.getString(R.string.preference_gallery_sort_order_inverted_key), context.getResources().getBoolean(R.bool.preference_gallery_sort_order_inverted_default));
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

    //FIXME OBSOLETE - REMOVE
    public static boolean isSlideshowExtraInfoShadowTransparent(SharedPreferences prefs, Context context) {
        return prefs.getBoolean(context.getString(R.string.preference_gallery_slideshow_transparent_extra_info_shadow_key), context.getResources().getBoolean(R.bool.preference_gallery_slideshow_transparent_extra_info_shadow_default));
    }


    public static int getAlbumChildAlbumsSortOrder(SharedPreferences prefs, Context context) {
        return prefs.getInt(context.getString(R.string.preference_album_subalbum_sort_order_key), context.getResources().getInteger(R.integer.preference_album_subalbum_sort_order_default));
    }

    public static ImageView.ScaleType getSlideshowImageScalingType(SharedPreferences prefs, Context context) {
        String scaleTypeName = prefs.getString(context.getString(R.string.preference_gallery_slideshow_image_scaletype_key), context.getResources().getString(R.string.preference_gallery_slideshow_image_scaletype_default));
        return ImageView.ScaleType.valueOf(scaleTypeName);
    }

    public static boolean isRotateImageSoAspectMatchesScreenAspect(SharedPreferences prefs, Context context) {
        return prefs.getBoolean(context.getString(R.string.preference_gallery_slideshow_image_rotate_key), context.getResources().getBoolean(R.bool.preference_gallery_slideshow_image_rotate_default));
    }

    public static long getAutoHideItemDetailDelayMillis(SharedPreferences prefs, Context context) {
        return prefs.getInt(context.getString(R.string.preference_gallery_slideshow_item_detail_auto_hide_delay_key), context.getResources().getInteger(R.integer.preference_gallery_slideshow_item_detail_auto_hide_delay_default));
    }
}
