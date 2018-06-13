package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItemStub;

public class AutoUploadJobConfig {
    private int jobId;
    private SharedPreferences jobPreferences;

    public AutoUploadJobConfig(Context c, int jobId) {
        this.jobId = jobId;
        jobPreferences = c.getSharedPreferences(getSharedPreferencesName(jobId), Context.MODE_PRIVATE);
    }

    public static String getSharedPreferencesName(int jobId) {
        return String.format("autoUploadJob[%1$d]",jobId);
    }

    public int getJobId() {
        return jobId;
    }

    private boolean getBooleanValue(Context c, @StringRes int prefKeyId) {
        if(!jobPreferences.contains(c.getString(prefKeyId))) {
            throw new IllegalStateException("Job misconfigured");
        }
        return jobPreferences.getBoolean(c.getString(prefKeyId), false);
    }

    private int getIntValue(Context c, @StringRes int prefKeyId) {
        if(!jobPreferences.contains(c.getString(prefKeyId))) {
            throw new IllegalStateException("Job misconfigured");
        }
        return jobPreferences.getInt(c.getString(prefKeyId), -1);
    }

    private @NonNull String getStringValue(Context c, @StringRes int prefKeyId) {
        String value = jobPreferences.getString(c.getString(prefKeyId), null);
        if(value == null) {
            throw new IllegalStateException("Job misconfigured");
        }
        return value;
    }

    private @NonNull List getCsvListValue(Context c, @StringRes int prefKeyId) {
        String value = getStringValue(c, prefKeyId);
        String[] values = value.split(",");
        return Arrays.asList(values);
    }

    public ConnectionPreferences.ProfilePreferences getConnectionPrefs(Context c) {
        String connectionProfileName = getStringValue(c, R.string.preference_data_upload_automatic_job_server_key);
        return ConnectionPreferences.getPreferences(connectionProfileName);
    }

    public File getLocalFolderToMonitor(Context c) {
        String localFolderName = getStringValue(c, R.string.preference_data_upload_automatic_job_local_folder_key);
        return new File(localFolderName);
    }

    public String getUploadToAlbumName(Context c) {
        String remoteAlbumDetails = getStringValue(c, R.string.preference_data_upload_automatic_job_server_album_key);
        return ServerAlbumListPreference.ServerAlbumPreference.getSelectedAlbumName(remoteAlbumDetails);
    }

    public long getUploadToAlbumId(Context c) {
        String remoteAlbumDetails = getStringValue(c, R.string.preference_data_upload_automatic_job_server_album_key);
        return ServerAlbumListPreference.ServerAlbumPreference.getSelectedAlbumId(remoteAlbumDetails);
    }

    public boolean isJobValid(Context c) {
        return getBooleanValue(c, R.string.preference_data_upload_automatic_job_is_valid_key);
    }

    public int getUploadedFilePrivacyLevel(Context c) {
        return getIntValue(c, R.string.preference_data_upload_automatic_job_privacy_level_key);
    }

    public int getMaxUploadSize(Context c) {
        return getIntValue(c, R.string.preference_data_upload_automatic_job_max_upload_size_mb_key);
    }

    public boolean isJobEnabled(Context c) {
        return getBooleanValue(c, R.string.preference_data_upload_automatic_job_enabled_key);
    }

    public boolean isDeleteFilesAfterUpload(Context c) {
        return getBooleanValue(c, R.string.preference_data_upload_automatic_job_delete_uploaded_key);
    }

    public List<String> getFileExtsToUpload(Context c) {
        return getCsvListValue(c, R.string.preference_data_upload_automatic_job_file_exts_uploaded_key);
    }

    public CategoryItemStub getUploadToAlbum(Context context) {
        String albumName = getUploadToAlbumName(context);
        long albumId = getUploadToAlbumId(context);
        return new CategoryItemStub(albumName, albumId);
    }
}
