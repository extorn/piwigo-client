package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.util.Log;

import com.google.android.vending.licensing.util.Base64;
import com.google.android.vending.licensing.util.Base64DecoderException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.util.IOUtils;

public class AutoUploadJobConfig {
    private int jobId;
    private SharedPreferences jobPreferences;

    public AutoUploadJobConfig(Context c, int jobId) {
        this.jobId = jobId;
        jobPreferences = c.getSharedPreferences(getSharedPreferencesName(jobId), Context.MODE_PRIVATE);
    }

    public void deletePreferences() {
        jobPreferences.edit().clear().commit();
    }


    public boolean exists(Context c) {
        if(jobPreferences.contains(c.getString(R.string.preference_data_upload_automatic_job_server_key))) {
            return true;
        }
        deletePreferences();
        return false;
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

    private int getIntValue(Context c, @StringRes int prefKeyId, int defaultVal) {
        if(!jobPreferences.contains(c.getString(prefKeyId))) {
            return defaultVal;
        }
        return jobPreferences.getInt(c.getString(prefKeyId), defaultVal);
    }

    private @NonNull String getStringValue(Context c, @StringRes int prefKeyId) {
        String value = jobPreferences.getString(c.getString(prefKeyId), null);
        if(value == null) {
            throw new IllegalStateException("Job misconfigured");
        }
        return value;
    }

    private @NonNull String getStringValue(Context c, @StringRes int prefKeyId, String defaultVal) {
        String value = jobPreferences.getString(c.getString(prefKeyId), defaultVal);
        return value;
    }

    private @NonNull List getCsvListValue(Context c, @StringRes int prefKeyId) {
        String value = getStringValue(c, prefKeyId);
        String[] values = value.split(",");
        return Arrays.asList(values);
    }

    private @NonNull List getCsvListValue(Context c, @StringRes int prefKeyId, String prefDefault) {
        String value = getStringValue(c, prefKeyId, prefDefault);
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
        return getIntValue(c, R.string.preference_data_upload_automatic_job_max_upload_size_mb_key, R.integer.preference_data_upload_automatic_job_max_upload_size_mb_default);
    }

    public boolean isJobEnabled(Context c) {
        return getBooleanValue(c, R.string.preference_data_upload_automatic_job_enabled_key);
    }

    public boolean isDeleteFilesAfterUpload(Context c) {
        return getBooleanValue(c, R.string.preference_data_upload_automatic_job_delete_uploaded_key);
    }

    public static class PriorUploads implements Serializable {

        private int jobId;
        private final HashMap<File, String> filesToHashMap;

        public PriorUploads(int jobId) {
            this.jobId = jobId;
            filesToHashMap = new HashMap<>();
        }

        public PriorUploads(int jobId, HashMap<File, String> map) {
            this.jobId = jobId;
            filesToHashMap = map;
        }

        public HashMap<File, String> getFilesToHashMap() {
            return filesToHashMap;
        }

        public static File getFolder(Context c) {
            File jobsFolder = new File(c.getApplicationContext().getExternalCacheDir(), "uploadJobData");
            if(!jobsFolder.exists()) {
                if(!jobsFolder.mkdir()) {
                    throw new RuntimeException("Unable to create required app data folder");
                }
            }
            return jobsFolder;
        }

        public void saveToFile(Context c) {
            File f = new File(getFolder(c), ""+jobId);
            IOUtils.saveObjectToFile(f, this);
        }

        public static PriorUploads loadFromFile(Context c, int jobId) {
            File f = new File(getFolder(c), ""+jobId);
            if(!f.exists()) {
                return new PriorUploads(jobId);
            } else {
                PriorUploads uploadedFiles = IOUtils.readObjectFromFile(f);
                if(uploadedFiles.jobId != jobId) {
                    throw new RuntimeException("Saved state for uploaded files corrupted");
                }
                return uploadedFiles;
            }
        }

        /**
         * NOT idempotent!
         *
         * @return
         */
        public boolean isOutOfSyncWithFileSystem() {
            boolean outOfSync = false;
            if(filesToHashMap.size() > 0) {
                Set<File> itemsToRemove = new HashSet<>();
                for(Map.Entry<File, String> priorUploadEntry : filesToHashMap.entrySet()) {
                    if(!priorUploadEntry.getKey().exists()) {
                        outOfSync = true;
                    }
                }
                for(File itemToRemove : itemsToRemove) {
                    filesToHashMap.remove(itemToRemove);
                }
            }
            return outOfSync;
        }
    }

    public PriorUploads getFilesPreviouslyUploaded(Context c) {
        PriorUploads uploads = PriorUploads.loadFromFile(c, jobId);
        if(uploads.isOutOfSyncWithFileSystem()) {
            uploads.saveToFile(c);
        }
        return uploads;
    }

    public void saveFilesPreviouslyUploaded(Context c, PriorUploads priorUploads) {
        priorUploads.saveToFile(c);
    }

    public List<String> getFileExtsToUpload(Context c) {
        return getCsvListValue(c, R.string.preference_data_upload_automatic_job_file_exts_uploaded_key, c.getString(R.string.preference_data_upload_automatic_job_file_exts_uploaded_default));
    }

    public CategoryItemStub getUploadToAlbum(Context context) {
        String albumName = getUploadToAlbumName(context);
        long albumId = getUploadToAlbumId(context);
        return new CategoryItemStub(albumName, albumId);
    }

}
