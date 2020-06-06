package delit.piwigoclient.business;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.core.os.ConfigurationCompat;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;

import delit.piwigoclient.R;

public class AppPreferences {
    public static boolean isAlwaysShowNavButtons(SharedPreferences prefs, Context context) {
        return prefs.getBoolean(context.getString(R.string.preference_app_always_show_nav_buttons_key), context.getResources().getBoolean(R.bool.preference_app_always_show_nav_buttons_default));
    }

    public static DocumentFile getAppDownloadFolder(SharedPreferences prefs, Context context) {
        File downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        String folder = prefs.getString(context.getString(R.string.preference_app_default_download_folder_key), null);
        if(folder != null) {
            DocumentFile docFile = DocumentFile.fromTreeUri(context, Uri.parse(folder));
            if(docFile.exists()) {
                return docFile;
            }
        }
        return DocumentFile.fromTreeUri(context, Uri.fromFile(downloadsFolder));
    }

    public static boolean isAlwaysShowStatusBar(SharedPreferences prefs, Context context) {
        return prefs.getBoolean(context.getString(R.string.preference_app_always_show_status_bar_key), context.getResources().getBoolean(R.bool.preference_app_always_show_status_bar_default));
    }

    public static String getDesiredLanguage(SharedPreferences prefs, Context context) {
        String val = prefs.getString(context.getString(R.string.preference_app_desired_language_key), null);
        if (val == null) {
            return ConfigurationCompat.getLocales(context.getResources().getConfiguration()).get(0).toString();
        }
        return val;
    }

    public static int getShortMessageDuration(SharedPreferences prefs, Context context) {
        return prefs.getInt(context.getString(R.string.preference_short_message_display_time_millis_key), context.getResources().getInteger(R.integer.preference_short_message_display_time_millis_default)); //TODO need custom Toast to do this
    }

    public static int getLongMessageDuration(SharedPreferences prefs, Context context) {
        return prefs.getInt(context.getString(R.string.preference_long_message_display_time_millis_key), context.getResources().getInteger(R.integer.preference_long_message_display_time_millis_default)); //TODO need custom Toast to do this
    }

    public static int getUserHintDuration(SharedPreferences prefs, Context context) {
        return prefs.getInt(context.getString(R.string.preference_user_hint_display_time_millis_key), context.getResources().getInteger(R.integer.preference_user_hint_display_time_millis_default)); //TODO need custom Toast to do this
    }

    public static void clearListOfShownHints(SharedPreferences prefs, Context context) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(context.getString(R.string.usage_hints_shown_list_key), null);
        editor.apply();
    }

    public static boolean isUseVideoCache(Context context, SharedPreferences prefs) {
        return prefs.getBoolean(context.getString(R.string.preference_video_cache_enabled_key), context.getResources().getBoolean(R.bool.preference_video_cache_enabled_default));
    }

    public static int getVideoCacheSizeMb(SharedPreferences prefs, Context context) {
        return prefs.getInt(context.getString(R.string.preference_video_cache_maxsize_mb_key), context.getResources().getInteger(R.integer.preference_video_cache_maxsize_mb_default));
    }

    public static class UriPermissionUseCheckResult {
        Uri uri;
        String uses;

        public UriPermissionUseCheckResult(@NonNull Uri uri) {
            this.uri = uri;
        }

        public void addUse(@NonNull String use) {
            uses = use;
        }

        public String getUses() {
            return uses;
        }

        public boolean isUsed() {
            return uses != null;
        }
    }
}
