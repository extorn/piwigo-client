package delit.piwigoclient.business;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.ConfigurationCompat;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.util.Iterator;
import java.util.Map;

import delit.libs.core.util.Logging;
import delit.libs.util.ArrayUtils;
import delit.libs.util.IOUtils;
import delit.libs.util.VersionUtils;
import delit.piwigoclient.R;

public class BaseAppPreferences {
    private static final String TAG = "BaseAppPrefs";

    public static boolean isAlwaysShowNavButtons(SharedPreferences prefs, Context context) {
        return prefs.getBoolean(context.getString(R.string.preference_app_always_show_nav_buttons_key), context.getResources().getBoolean(R.bool.preference_app_always_show_nav_buttons_default));
    }

    public static @NonNull DocumentFile getAppDownloadFolder(@NonNull SharedPreferences prefs, @NonNull Context context) {

        String folder = prefs.getString(context.getString(R.string.preference_app_default_download_folder_key), null);
        if(folder != null) {
            try {
                Uri uri = Uri.parse(folder);
                DocumentFile docFile = null;
                if (uri.getPath() != null) {
                    File f = new File(uri.getPath());
                    if ("file".equals(uri.getScheme()) || (f.exists() && f.isDirectory())) {
                        docFile = DocumentFile.fromFile(new File(uri.getPath()));
                    } else {
                        docFile = DocumentFile.fromTreeUri(context, uri);
                    }
                }
                if (docFile != null && docFile.exists()) {
                    return docFile;
                }
            } catch(IllegalArgumentException e) {
                Logging.log(Log.ERROR,TAG, "Unsupported download folder %1$s. Using app default.", folder);
                Bundle b = new Bundle();
                b.putString("path", folder);
                b.putSerializable("error", e);
                Logging.logAnalyticEvent(context, "UnsupportedDownloadFolder", b);
            }
        }
        return getDefaultDownloadFolder(context);
    }

    private static DocumentFile getDefaultDownloadFolder(Context context) {
        File folder = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if(folder == null) {
            folder = context.getFilesDir();
        }
        return DocumentFile.fromFile(folder);
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
        editor.remove(context.getString(R.string.usage_hints_shown_list_key));
        editor.apply();
    }

    public static boolean isUseVideoCache(Context context, SharedPreferences prefs) {
        return prefs.getBoolean(context.getString(R.string.preference_video_cache_enabled_key), context.getResources().getBoolean(R.bool.preference_video_cache_enabled_default));
    }

    public static int getVideoCacheSizeMb(SharedPreferences prefs, Context context) {
        return prefs.getInt(context.getString(R.string.preference_video_cache_maxsize_mb_key), context.getResources().getInteger(R.integer.preference_video_cache_maxsize_mb_default));
    }

    public static boolean havePermissions(@NonNull Context context, DocumentFile downloadFolder, int ... neededPermissions) {
        if(downloadFolder != null && IOUtils.isPrivateFolder(context, downloadFolder.getUri().getPath())) {
            return true;
        }
        int havePerms = IOUtils.getUriPermissionsFlagsHeld(context, downloadFolder.getUri());
        return IOUtils.allUriFlagsAreSet(havePerms, neededPermissions);
    }

    public static boolean isCheckForPiwigoServerUpdates(Context context, SharedPreferences prefs) {
        return prefs.getBoolean(context.getString(R.string.preference_check_for_server_updates_key), context.getResources().getBoolean(R.bool.preference_check_for_server_updates_default));
    }

    public static String getLatestReleaseNotesVersionShown(SharedPreferences prefs, Context context) {
        return prefs.getString(context.getString(R.string.latest_release_notes_shown_key), null);
    }

    public static void setLatestReleaseNotesShown(SharedPreferences prefs, Context context, String newValue) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(context.getString(R.string.latest_release_notes_shown_key), newValue);
        editor.apply();
    }

    /**
     *
     * @param context
     * @param fromVersionCodeExclusive
     * @return Map of VersionStr : ReleaseNotesStr
     */
    public static Map<String, String> getAppReleaseHistory(@NonNull Context context, @Nullable String fromVersionCodeExclusive) {
        Map<String,String> historicalReleasesData = ArrayUtils.toMap(context.getResources().getStringArray(R.array.release_history));
        int[] fromVersion = VersionUtils.parseVersionString(fromVersionCodeExclusive);
        for (Iterator<Map.Entry<String, String>> iterator = historicalReleasesData.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<String, String> releaseNote = iterator.next();
            int[] thisReleaseVersion = VersionUtils.parseVersionString(releaseNote.getKey());
            if (!VersionUtils.versionExceeds(fromVersion, thisReleaseVersion)) {
                iterator.remove();
            }
        }
        return historicalReleasesData;
    }

    public static void setHidePaymentOptionForever(@NonNull SharedPreferences prefs, @NonNull Context context) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(context.getString(R.string.preference_hide_payments_option_key), true);
        editor.apply();
    }

    public static boolean isHidePaymentOptionForever(@NonNull SharedPreferences prefs, @NonNull Context context) {
        return prefs.getBoolean(context.getString(R.string.preference_hide_payments_option_key), false);
    }
}
