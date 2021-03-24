package delit.piwigoclient.business;

import android.content.Context;
import android.content.SharedPreferences;

import delit.piwigoclient.R;

public class SlideshowViewPreferences extends BaseSlideshowViewPreferences {
    public static boolean isShowItemDetailOnSlideshowItemOpen(SharedPreferences prefs, Context context) {
        return context.getResources().getBoolean(R.bool.preference_gallery_slideshow_item_detail_show_on_item_show_default);
    }
}
