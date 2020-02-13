package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.BoolRes;
import androidx.annotation.IntegerRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import delit.libs.util.CollectionUtils;
import delit.libs.util.IOUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.ui.common.preference.ServerAlbumSelectPreference;

public class AutoUploadJobConfig implements Parcelable {
    private int jobId;
    private SharedPreferences jobPreferences;

    public AutoUploadJobConfig(int jobId) {
        this.jobId = jobId;
    }

    public AutoUploadJobConfig(Parcel in) {
        jobId = in.readInt();
    }

    public static final Creator<AutoUploadJobConfig> CREATOR = new Creator<AutoUploadJobConfig>() {
        @Override
        public AutoUploadJobConfig createFromParcel(Parcel in) {
            return new AutoUploadJobConfig(in);
        }

        @Override
        public AutoUploadJobConfig[] newArray(int size) {
            return new AutoUploadJobConfig[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(jobId);
    }

    public SharedPreferences getJobPreferences(Context c) {
        if(jobPreferences == null) {
            jobPreferences = c.getSharedPreferences(getSharedPreferencesName(jobId), Context.MODE_PRIVATE);
        }
        return jobPreferences;
    }

    public String getSummary(SharedPreferences appPrefs, Context c) {
        StringBuilder sb = new StringBuilder();
        sb.append("uploadFrom:\n");
        sb.append(getLocalFolderToMonitor(c) == null ? "???" : getLocalFolderToMonitor(c).getAbsolutePath());
        sb.append('\n');
        sb.append("uploadTo:\n");
        ConnectionPreferences.ProfilePreferences cp = getConnectionPrefs(c, appPrefs);
        sb.append(cp.getAbsoluteProfileKey(appPrefs, c));
        sb.append('\n');
        sb.append(getUploadToAlbumId(c));
        sb.append('\n');
        sb.append("Prefs:\n");
        sb.append("MaxUploadSize:");
        sb.append(getMaxUploadSize(c));
        sb.append('\n');
        sb.append("PrivacyLevel:");
        sb.append(getUploadedFilePrivacyLevel(c));
        sb.append('\n');
        sb.append("UploadExts:\n");
        sb.append(CollectionUtils.toCsvList(getFileExtsToUpload(c)));
        sb.append('\n');
        return sb.toString();
    }

    public void deletePreferences(Context c) {
        getJobPreferences(c).edit().clear().commit();
    }


    public boolean exists(Context c) {
        if(getJobPreferences(c).contains(c.getString(R.string.preference_data_upload_automatic_job_server_key))) {
            return true;
        }
        deletePreferences(c);
        return false;
    }

    public static String getSharedPreferencesName(int jobId) {
        return String.format(Locale.UK,"autoUploadJob[%1$d]",jobId);
    }

    public int getJobId() {
        return jobId;
    }

    private boolean getBooleanValue(Context c, @StringRes int prefKeyId, boolean defaultVal) {
        return getJobPreferences(c).getBoolean(c.getString(prefKeyId), defaultVal);
    }

    private boolean getBooleanValue(Context c, @StringRes int prefKeyId, @BoolRes int defaultValResId) {
        return getJobPreferences(c).getBoolean(c.getString(prefKeyId), c.getResources().getBoolean(defaultValResId));
    }

    private int getIntValue(Context c, @StringRes int prefKeyId, @IntegerRes int defaultVal) {
        return getJobPreferences(c).getInt(c.getString(prefKeyId), c.getResources().getInteger(defaultVal));
    }

    private String getStringValue(Context c, @StringRes int prefKeyId) {
        String value = getJobPreferences(c).getString(c.getString(prefKeyId), null);
        return value;
    }

    private String getStringValue(Context c, @StringRes int prefKeyId, @StringRes int prefDefaultKeyId) {
        String value = getJobPreferences(c).getString(c.getString(prefKeyId), c.getString(prefDefaultKeyId));
        return value;
    }

    private String getStringValue(Context c, @StringRes int prefKeyId, String defaultVal) {
        String value = getJobPreferences(c).getString(c.getString(prefKeyId), defaultVal);
        return value;
    }

    public @NonNull
    Set<String> getStringSetValue(Context c, @StringRes int prefKeyId, @StringRes int prefDefaultId) {
        TreeSet<String> defaultVal = new TreeSet<>(CollectionUtils.stringsFromCsvList(c.getString(prefDefaultId)));
        return getJobPreferences(c).getStringSet(c.getString(prefKeyId), defaultVal);
    }

    public ConnectionPreferences.ProfilePreferences getConnectionPrefs(Context c, SharedPreferences overallAppPrefs) {
        String connectionProfileName = getStringValue(c, R.string.preference_data_upload_automatic_job_server_key);
        return ConnectionPreferences.getPreferences(connectionProfileName, overallAppPrefs, c);
    }

    public File getLocalFolderToMonitor(Context c) {
        String localFolderName = getStringValue(c, R.string.preference_data_upload_automatic_job_local_folder_key, null);
        if(localFolderName == null) {
            return null;
        }
        return new File(localFolderName);
    }

    public String getUploadToAlbumName(Context c) {
        String remoteAlbumDetails = getStringValue(c, R.string.preference_data_upload_automatic_job_server_album_key);
        return ServerAlbumSelectPreference.ServerAlbumDetails.fromEncodedPersistenceString(remoteAlbumDetails).getAlbumName();
    }

    public long getUploadToAlbumId(Context c) {
        String remoteAlbumDetails = getStringValue(c, R.string.preference_data_upload_automatic_job_server_album_key);
        return ServerAlbumSelectPreference.ServerAlbumDetails.fromEncodedPersistenceString(remoteAlbumDetails).getAlbumId();
    }

    public void setJobValid(Context c, boolean isValid) {
        SharedPreferences.Editor editor = getJobPreferences(c).edit();
        editor.putBoolean(c.getString(R.string.preference_data_upload_automatic_job_is_valid_key), isValid);
        editor.apply();
    }

    public boolean isJobValid(Context c) {
        return getBooleanValue(c, R.string.preference_data_upload_automatic_job_is_valid_key, false);
    }

    public byte getUploadedFilePrivacyLevel(Context c) {
        return (byte) getIntValue(c, R.string.preference_data_upload_automatic_job_privacy_level_key, R.integer.preference_data_upload_automatic_job_privacy_level_default);
    }

    public int getMaxUploadSize(Context c) {
        return getIntValue(c, R.string.preference_data_upload_automatic_job_max_upload_size_mb_key, R.integer.preference_data_upload_automatic_job_max_upload_size_mb_default);
    }

    public boolean isJobEnabled(Context c) {
        return getBooleanValue(c, R.string.preference_data_upload_automatic_job_enabled_key, R.bool.preference_data_upload_automatic_job_enabled_default);
    }

    public boolean isDeleteFilesAfterUpload(Context c) {
        return getBooleanValue(c, R.string.preference_data_upload_automatic_job_delete_uploaded_key, R.bool.preference_data_upload_automatic_job_delete_uploaded_default);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public boolean isCompressVideosBeforeUpload(Context c) {
        return getBooleanValue(c, R.string.preference_data_upload_automatic_job_compress_videos_key, R.bool.preference_data_upload_automatic_job_compress_videos_default);
    }

    public boolean isCompressImagesBeforeUpload(Context c) {
        return getBooleanValue(c, R.string.preference_data_upload_automatic_job_compress_images_key, R.bool.preference_data_upload_automatic_job_compress_images_default);
    }

    private double getVideoCompressionQuality(Context c) {
        return ((double) getIntValue(c, R.string.preference_data_upload_automatic_job_compress_videos_quality_key, R.integer.preference_data_upload_automatic_job_compress_videos_quality_default)) / 1000;
    }

    private int getVideoCompressionAudioBitrate(Context c) {
        return getIntValue(c, R.string.preference_data_upload_automatic_job_compress_videos_audio_bitrate_key, R.integer.preference_data_upload_automatic_job_compress_videos_audio_bitrate_default);
    }

    private int getImageCompressionQuality(Context c) {
        return getIntValue(c, R.string.preference_data_upload_automatic_job_compress_images_quality_key, R.integer.preference_data_upload_automatic_job_compress_images_quality_default);
    }

    private int getImageCompressionMaxWidth(Context c) {
        return getIntValue(c, R.string.preference_data_upload_automatic_job_compress_images_max_width_key, R.integer.preference_data_upload_automatic_job_compress_images_max_width_default);
    }

    private int getImageCompressionMaxHeight(Context c) {
        return getIntValue(c, R.string.preference_data_upload_automatic_job_compress_images_max_height_key, R.integer.preference_data_upload_automatic_job_compress_images_max_height_default);
    }

    private String getImageCompressionOutputFormat(Context c) {
        return getStringValue(c, R.string.preference_data_upload_automatic_job_compress_images_quality_key, R.string.preference_data_upload_automatic_job_compress_images_output_format_default);
    }

    public UploadJob.VideoCompressionParams getVideoCompressionParams(Context c) {
        if (isCompressVideosBeforeUpload(c)) {
            UploadJob.VideoCompressionParams params = new UploadJob.VideoCompressionParams();
            params.setQuality(getVideoCompressionQuality(c));
            params.setAudioBitrate(getVideoCompressionAudioBitrate(c));
            return params;
        }
        return null;
    }

    public UploadJob.ImageCompressionParams getImageCompressionParams(Context c) {
        if (isCompressImagesBeforeUpload(c)) {
            UploadJob.ImageCompressionParams params = new UploadJob.ImageCompressionParams();
            params.setOutputFormat(getImageCompressionOutputFormat(c));
            params.setQuality(getImageCompressionQuality(c));
            params.setMaxHeight(getImageCompressionMaxHeight(c));
            params.setMaxWidth(getImageCompressionMaxWidth(c));
            return params;
        }
        return null;
    }

    public static class PriorUploads implements Serializable {

        private static final long serialVersionUID = 4250545241017682232L;
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
            File jobsFolder = new File(c.getExternalCacheDir(), "uploadJobData");
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
                if(uploadedFiles == null) {
                    return new PriorUploads(jobId);
                } else if(uploadedFiles.jobId != jobId) {
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

    public Set<String> getFileExtsToUpload(Context c) {
        return getStringSetValue(c, R.string.preference_data_upload_automatic_job_file_exts_uploaded_key, R.string.preference_data_upload_automatic_job_file_exts_uploaded_default);
    }

    public ServerAlbumSelectPreference.ServerAlbumDetails getUploadToAlbumDetails(Context context) {
        String remoteAlbumDetails = getStringValue(context, R.string.preference_data_upload_automatic_job_server_album_key);
        return ServerAlbumSelectPreference.ServerAlbumDetails.fromEncodedPersistenceString(remoteAlbumDetails);
    }

    public CategoryItemStub getUploadToAlbum(Context context) {
        if(!isJobValid(context)) {
            throw new IllegalStateException("Unable to retrieve upload album for invalid job");
        }
        return getUploadToAlbumDetails(context).toCategoryItemStub();
    }

}
