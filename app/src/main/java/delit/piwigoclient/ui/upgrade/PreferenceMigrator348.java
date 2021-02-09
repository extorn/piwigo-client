package delit.piwigoclient.ui.upgrade;

import android.content.Context;
import android.content.SharedPreferences;

import delit.piwigoclient.R;

public class PreferenceMigrator348 extends PreferenceMigrator {

    public PreferenceMigrator348() {
        super(348);
    }

    @Override
    protected void upgradePreferences(Context context, SharedPreferences prefs, SharedPreferences.Editor editor) {
        rekeyBooleanPref(context, prefs, editor, "preference_gallery_sort_order_inverted", R.string.preference_gallery_child_resource_sort_order_inverted_key, R.bool.preference_gallery_child_resource_sort_order_inverted_default);
        rekeyIntPref(context, prefs, editor, "preference_album_subalbum_sort_order_items", R.string.preference_gallery_child_album_sort_order_key, R.integer.preference_gallery_child_album_sort_order_default);
        rekeyStringPref(context, prefs, editor, "preference_gallery_sort_order", R.string.preference_gallery_child_resource_sort_order_key, R.string.preference_gallery_child_resource_sort_order_default);
    }


}
