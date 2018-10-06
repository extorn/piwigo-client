package delit.piwigoclient.business;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import delit.piwigoclient.R;
import delit.piwigoclient.util.DisplayUtils;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;

public class UploadPreferences {
    public static int getColumnsOfFilesListedForUpload(SharedPreferences prefs, Activity activity) {
        int orientation = activity.getResources().getConfiguration().orientation;
        int defaultColums = getDefaultFilesColumnCount(activity, orientation);
        if (orientation == ORIENTATION_PORTRAIT) {
            return prefs.getInt(activity.getString(R.string.preference_data_upload_preferredColumnsPortrait_key), defaultColums);
        } else {
            return prefs.getInt(activity.getString(R.string.preference_data_upload_preferredColumnsLandscape_key), defaultColums);
        }
    }

    /**
     * @param orientationId {@link Configuration.ORIENTATION_LANDSCAPE} or {@link Configuration.ORIENTATION_PORTRAIT}
     * @return default column count
     */
    @SuppressWarnings("JavadocReference")
    public static int getDefaultFilesColumnCount(Activity activity, int orientationId) {
        return DisplayUtils.getDefaultColumnCount(activity, orientationId, 0.8);
    }

    public static int getMaxUploadFilesizeMb(Context context, SharedPreferences prefs) {
        return prefs.getInt(context.getString(R.string.preference_data_upload_max_filesize_mb_key), context.getResources().getInteger(R.integer.preference_data_upload_max_filesize_mb_default));
    }

    public static int getMaxUploadChunkSizeMb(Context context, SharedPreferences prefs) {
        return prefs.getInt(context.getString(R.string.preference_data_upload_chunkSizeKb_key), context.getResources().getInteger(R.integer.preference_data_upload_chunkSizeKb_default));
    }

    public static int getUploadChunkMaxRetries(Context context, SharedPreferences prefs) {
        return prefs.getInt(context.getString(R.string.preference_data_upload_chunk_auto_retries_key), context.getResources().getInteger(R.integer.preference_data_upload_chunk_auto_retries_default));
    }
}
