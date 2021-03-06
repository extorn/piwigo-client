package delit.piwigoclient.business;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.annotation.NonNull;

import java.util.Calendar;
import java.util.Date;

import delit.libs.ui.util.DisplayUtils;
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

    /**
     * @param orientationId {@link Configuration.ORIENTATION_LANDSCAPE} or {@link Configuration.ORIENTATION_PORTRAIT}
     * @return default column count
     */
    @SuppressWarnings("JavadocReference")
    public static int getDefaultFilesColumnCount(Activity activity, int orientationId) {
        return DisplayUtils.getDefaultColumnCount(activity, orientationId, 1);
    }

    /**
     * @param orientationId {@link Configuration.ORIENTATION_LANDSCAPE} or {@link Configuration.ORIENTATION_PORTRAIT}
     * @return default column count
     */
    @SuppressWarnings("JavadocReference")
    public static int getDefaultFoldersColumnCount(Activity activity, int orientationId) {
        return DisplayUtils.getDefaultColumnCount(activity, orientationId, 2);
    }

    public static Date getLastWarnedAboutVersionOrFeatures(@NonNull SharedPreferences prefs, @NonNull Context context) {
        long dateTimeMillis = prefs.getLong(context.getString(R.string.preference_functions_missing_app_version_warned_key), 0);
        return new Date(dateTimeMillis);
    }

    public static boolean getAndUpdateLastWarnedAboutVersionOrFeatures(@NonNull SharedPreferences prefs, @NonNull Context context) {
        Calendar lastWarnedAt = Calendar.getInstance();
        lastWarnedAt.setTime(getLastWarnedAboutVersionOrFeatures(prefs, context));
        Calendar warnIfLastWarnedBeforeDateTime = Calendar.getInstance();
        warnIfLastWarnedBeforeDateTime.setTime(new Date());
        warnIfLastWarnedBeforeDateTime.add(Calendar.DATE, -7); // 7 days before now.
        boolean showWarning = lastWarnedAt.before(warnIfLastWarnedBeforeDateTime);
        if(showWarning) {
            ConnectionPreferences.PreferenceActor prefActor = new ConnectionPreferences.PreferenceActor().with(R.string.preference_functions_missing_app_version_warned_key);
            prefActor.writeLong(prefs, context, System.currentTimeMillis());
        }
        return showWarning;
    }

    public static int getColumnsOfUsers(SharedPreferences prefs, Activity activity) {
        int orientationId = activity.getResources().getConfiguration().orientation;
        return DisplayUtils.getDefaultColumnCount(activity, orientationId, 2);
    }

    public static int getColumnsOfGroups(SharedPreferences prefs, Activity activity) {
        int orientationId = activity.getResources().getConfiguration().orientation;
        return DisplayUtils.getDefaultColumnCount(activity, orientationId, 2);
    }
}
