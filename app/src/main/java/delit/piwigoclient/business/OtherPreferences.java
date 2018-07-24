package delit.piwigoclient.business;

import android.content.Context;
import android.content.SharedPreferences;

import delit.piwigoclient.R;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;

public class OtherPreferences {

    public static boolean isShowFilenames(SharedPreferences prefs, Context context) {
        return prefs.getBoolean(context.getString(R.string.preference_data_file_selector_showFilenames_key), context.getResources().getBoolean(R.bool.preference_data_file_selector_showFilenames_default));
    }

    public static int getFileSelectorColumnsOfFiles(SharedPreferences prefs, Context context) {
        if(context.getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT) {
            return prefs.getInt(context.getString(R.string.preference_data_file_selector_preferredFileColumnsPortrait_key), 3);
        } else {
            return prefs.getInt(context.getString(R.string.preference_data_file_selector_preferredFileColumnsLandscape_key), 5);
        }
    }

    public static int getFileSelectorColumnsOfFolders(SharedPreferences prefs, Context context) {
        if(context.getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT) {
            return prefs.getInt(context.getString(R.string.preference_data_file_selector_preferredFolderColumnsPortrait_key), 3);
        } else {
            return prefs.getInt(context.getString(R.string.preference_data_file_selector_preferredFolderColumnsLandscape_key), 4);
        }

    }
}
