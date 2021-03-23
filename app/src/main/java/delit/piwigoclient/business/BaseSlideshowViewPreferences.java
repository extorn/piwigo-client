package delit.piwigoclient.business;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.ImageView;

import delit.piwigoclient.R;

public class BaseSlideshowViewPreferences {

    public static String getPreferredSlideshowImageSize(SharedPreferences prefs, Context context) {
        return prefs.getString(context.getString(R.string.preference_gallery_item_slideshow_image_size_key), context.getString(R.string.preference_gallery_item_slideshow_image_size_default));
    }

    public static boolean isIncludeVideosInSlideshow(SharedPreferences prefs, Context context) {
        return prefs.getBoolean(context.getString(R.string.preference_gallery_include_videos_in_slideshow_key), context.getResources().getBoolean(R.bool.preference_gallery_include_videos_in_slideshow_default));
    }

    public static ImageView.ScaleType getSlideshowImageScalingType(SharedPreferences prefs, Context context) {
        String scaleTypeName = prefs.getString(context.getString(R.string.preference_gallery_slideshow_image_scaletype_key), context.getResources().getString(R.string.preference_gallery_slideshow_image_scaletype_default));
        return ImageView.ScaleType.valueOf(scaleTypeName);
    }

    public static long getAutoHideItemDetailDelayMillis(SharedPreferences prefs, Context context) {
        return prefs.getInt(context.getString(R.string.preference_gallery_slideshow_item_detail_auto_hide_delay_key), context.getResources().getInteger(R.integer.preference_gallery_slideshow_item_detail_auto_hide_delay_default));
    }
}
