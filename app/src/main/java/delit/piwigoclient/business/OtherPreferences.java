package delit.piwigoclient.business;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;

import delit.piwigoclient.R;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;

public class OtherPreferences {

    public static boolean isShowFilenames(SharedPreferences prefs, Context context) {
        return prefs.getBoolean(context.getString(R.string.preference_data_file_selector_showFilenames_key), context.getResources().getBoolean(R.bool.preference_data_file_selector_showFilenames_default));
    }

    public static int getFileSelectorColumnsOfFiles(SharedPreferences prefs, Activity activity) {
        int orientation = activity.getResources().getConfiguration().orientation;
        int defaultColums = getDefaultFilesColumnCount(activity, orientation);
        if (orientation == ORIENTATION_PORTRAIT) {
            return prefs.getInt(activity.getString(R.string.preference_data_file_selector_preferredFileColumnsPortrait_key), defaultColums);
        } else {
            return prefs.getInt(activity.getString(R.string.preference_data_file_selector_preferredFileColumnsLandscape_key), defaultColums);
        }
    }

    public static int getFileSelectorColumnsOfFolders(SharedPreferences prefs, Activity activity) {
        int orientation = activity.getResources().getConfiguration().orientation;
        int defaultColums = getDefaultFoldersColumnCount(activity, orientation);
        if (orientation == ORIENTATION_PORTRAIT) {
            return prefs.getInt(activity.getString(R.string.preference_data_file_selector_preferredFolderColumnsPortrait_key), defaultColums);
        } else {
            return prefs.getInt(activity.getString(R.string.preference_data_file_selector_preferredFolderColumnsLandscape_key), defaultColums);
        }

    }

    private static float getScreenHeightInches(Activity activity) {
        DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        return (float) dm.heightPixels / dm.xdpi;
    }

    private static float getScreenWidthInches(Activity activity) {
        DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        return (float) dm.widthPixels / dm.xdpi;
    }

    /**
     * @param orientationId {@link Configuration.ORIENTATION_LANDSCAPE} or {@link Configuration.ORIENTATION_PORTRAIT}
     * @return default column count
     */
    @SuppressWarnings("JavadocReference")
    public static int getDefaultFilesColumnCount(Activity activity, int orientationId) {
        float screenWidth;
        if (activity.getResources().getConfiguration().orientation == orientationId) {
            screenWidth = getScreenWidthInches(activity);
        } else {
            screenWidth = getScreenHeightInches(activity);
        }
        int columnsToShow = (int) Math.max(1, Math.round(screenWidth / 1)); // allow a minimum of 1 inch per column
        return Math.max(1, columnsToShow); // never allow less than one column by default.
    }

    /**
     * @param orientationId {@link Configuration.ORIENTATION_LANDSCAPE} or {@link Configuration.ORIENTATION_PORTRAIT}
     * @return default column count
     */
    @SuppressWarnings("JavadocReference")
    public static int getDefaultFoldersColumnCount(Activity activity, int orientationId) {
        float screenWidth;
        if (activity.getResources().getConfiguration().orientation == orientationId) {
            screenWidth = getScreenWidthInches(activity);
        } else {
            screenWidth = getScreenHeightInches(activity);
        }
        int columnsToShow = Math.max(1, Math.round(screenWidth / 2)); // allow a minimum of 2 inch per column
        return Math.max(1, columnsToShow); // never allow less than one column by default.
    }
}
