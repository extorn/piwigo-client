package delit.piwigoclient.business;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.BoolRes;
import androidx.annotation.IntegerRes;
import androidx.annotation.StringRes;

import delit.libs.ui.util.DisplayUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.upload.UploadJob;

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
        return getInt(context, prefs, R.string.preference_data_upload_max_filesize_mb_key, R.integer.preference_data_upload_max_filesize_mb_default);
    }

    public static int getMaxUploadChunkSizeMb(Context context, SharedPreferences prefs) {
        return getInt(context, prefs, R.string.preference_data_upload_chunkSizeKb_key, R.integer.preference_data_upload_chunkSizeKb_default);
    }

    public static int getUploadChunkMaxRetries(Context context, SharedPreferences prefs) {
        return getInt(context, prefs, R.string.preference_data_upload_chunk_auto_retries_key, R.integer.preference_data_upload_chunk_auto_retries_default);
    }

    public static UploadJob.VideoCompressionParams getVideoCompressionParams(Context context, SharedPreferences prefs) {
        UploadJob.VideoCompressionParams params = new UploadJob.VideoCompressionParams();
        params.setQuality(getVideoCompressionQuality(context, prefs));
        return params;
    }

    public static UploadJob.ImageCompressionParams getImageCompressionParams(Context context, SharedPreferences prefs) {
        UploadJob.ImageCompressionParams params = new UploadJob.ImageCompressionParams();
        params.setQuality(getImageCompressionQuality(context, prefs));
        params.setOutputFormat(getImageCompressionOutputFormat(context, prefs));
        return params;
    }

    public static double getVideoCompressionQuality(Context context, SharedPreferences prefs) {
        return ((double) getInt(context, prefs, R.string.preference_data_upload_compress_videos_quality_key, R.integer.preference_data_upload_compress_videos_quality_default)) / 1000;
    }

    public static int getVideoCompressionQualityOption(Context context, SharedPreferences prefs) {
        return getInt(context, prefs, R.string.preference_data_upload_compress_videos_quality_key, R.integer.preference_data_upload_compress_videos_quality_default);
    }

    public static boolean isCompressVideosByDefault(Context context, SharedPreferences prefs) {
        return getBoolean(context, prefs, R.string.preference_data_upload_compress_videos_key, R.bool.preference_data_upload_compress_videos_default);
    }

    public static boolean isCompressImagesByDefault(Context context, SharedPreferences prefs) {
        return getBoolean(context, prefs, R.string.preference_data_upload_compress_images_key, R.bool.preference_data_upload_compress_images_default);
    }

    public static int getImageCompressionQuality(Context context, SharedPreferences prefs) {
        return getInt(context, prefs, R.string.preference_data_upload_compress_images_quality_key, R.integer.preference_data_upload_compress_images_quality_default);
    }

    public static String getImageCompressionOutputFormat(Context context, SharedPreferences prefs) {
        return getString(context, prefs, R.string.preference_data_upload_compress_images_output_format_key, R.string.preference_data_upload_compress_images_output_format_default);
    }

    public static boolean isAllowUploadOfRawVideosIfIncompressible(Context context, SharedPreferences prefs) {
        return getBoolean(context, prefs, R.string.preference_data_upload_allow_upload_of_incompressible_videos_key, R.bool.preference_data_upload_allow_upload_of_incompressible_videos_default);
    }

    private static String getString(Context context, SharedPreferences prefs, @StringRes int prefKeyResId, @StringRes int prefDefaultResId) {
        return prefs.getString(context.getString(prefKeyResId), context.getString(prefDefaultResId));
    }

    private static boolean getBoolean(Context context, SharedPreferences prefs, @StringRes int prefKeyResId, @BoolRes int prefDefaultResId) {
        return prefs.getBoolean(context.getString(prefKeyResId), context.getResources().getBoolean(prefDefaultResId));
    }

    private static int getInt(Context context, SharedPreferences prefs, @StringRes int prefKeyResId, @IntegerRes int prefDefaultResId) {
        return prefs.getInt(context.getString(prefKeyResId), context.getResources().getInteger(prefDefaultResId));
    }


}
