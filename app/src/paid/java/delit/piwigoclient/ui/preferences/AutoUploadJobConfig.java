package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.BoolRes;
import androidx.annotation.IntegerRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.exoplayer2.util.MimeTypes;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import delit.libs.ui.util.ParcelUtils;
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

    public String getUploadFromFolderName(Context c) {
        DocumentFile uploadFromFolder = getLocalFolderToMonitor(c);
        return uploadFromFolder == null ? "???" : uploadFromFolder.getName();
    }

    public String getSummary(SharedPreferences appPrefs, Context c) {

        StringBuilder sb = new StringBuilder();
        sb.append("uploadFrom:\n");
        sb.append(getUploadFromFolderName(c));
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

    public DocumentFile getLocalFolderToMonitor(Context c) {
        String uriStr = getStringValue(c, R.string.preference_data_upload_automatic_job_local_folder_key, null);
        if(uriStr == null) {
            return null;
        }
        Uri localFolderUri = Uri.parse(uriStr);
        if(localFolderUri == null) {
            return null;
        }
        return DocumentFile.fromTreeUri(c, localFolderUri);
    }

    public String getUploadToAlbumName(Context c) {
        String remoteAlbumDetails = getStringValue(c, R.string.preference_data_upload_automatic_job_server_album_key);
        String uploadFolder = ServerAlbumSelectPreference.ServerAlbumDetails.fromEncodedPersistenceString(remoteAlbumDetails).getAlbumName();
        if(uploadFolder == null) {
            uploadFolder = "???";
        }
        return uploadFolder;
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

    public static class PriorUploads implements Parcelable {

        private int jobId;
        private final HashMap<Uri, String> fileUrisAndHashcodes = new HashMap<>();

        public PriorUploads(int jobId) {
            this.jobId = jobId;
        }

        protected PriorUploads(Parcel in) {
            jobId = in.readInt();
            ParcelUtils.readMap(in, fileUrisAndHashcodes, Uri.class.getClassLoader());
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(jobId);
            ParcelUtils.writeMap(dest, fileUrisAndHashcodes);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<PriorUploads> CREATOR = new Creator<PriorUploads>() {
            @Override
            public PriorUploads createFromParcel(Parcel in) {
                return new PriorUploads(in);
            }

            @Override
            public PriorUploads[] newArray(int size) {
                return new PriorUploads[size];
            }
        };

        public Map<Uri, String> getFileUrisAndHashcodes() {
            return Collections.unmodifiableMap(fileUrisAndHashcodes);
        }

        public static DocumentFile getFolder(Context c) {
            File jobsFolder = new File(c.getExternalCacheDir(), "uploadJobData");
            if(!jobsFolder.exists()) {
                if(!jobsFolder.mkdir()) {
                    throw new RuntimeException("Unable to create required app data folder");
                }
            }
            return DocumentFile.fromFile(jobsFolder);
        }

        public void saveToFile(Context c) {
            DocumentFile folder = getFolder(c);
            DocumentFile child = folder.findFile(""+jobId);
            if(child == null) {
                child = folder.createFile(MimeTypes.BASE_TYPE_APPLICATION, ""+jobId);
            }
            IOUtils.saveParcelableToDocumentFile(c, child, this);
        }

        public static PriorUploads loadFromFile(Context c, int jobId) {
            DocumentFile folder = getFolder(c);
            DocumentFile child = folder.findFile(""+jobId);
            if(child == null) {
                return new PriorUploads(jobId);
            } else {
                PriorUploads uploadedFiles = IOUtils.readParcelableFromDocumentFile(c.getContentResolver(), child, PriorUploads.class);
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
        public boolean isOutOfSyncWithFileSystem(Context context) {
            boolean outOfSync = false;
            if(fileUrisAndHashcodes.size() > 0) {
                Set<Uri> itemsToRemove = new HashSet<>();
                for(Map.Entry<Uri, String> priorUploadEntry : fileUrisAndHashcodes.entrySet()) {
                    DocumentFile docFile = DocumentFile.fromSingleUri(context, priorUploadEntry.getKey());
                    if(docFile == null || !docFile.exists()) {
                        itemsToRemove.add(priorUploadEntry.getKey());
                        outOfSync = true;
                    }
                }
                for(Uri itemToRemove : itemsToRemove) {
                    fileUrisAndHashcodes.remove(itemToRemove);
                }
            }
            return outOfSync;
        }

        public void putAll(HashMap<Uri, String> uploadedFileChecksums) {
            Collections.checkedMap(new HashMap<>(),Uri.class, String.class).putAll(uploadedFileChecksums);
        }
    }

    public PriorUploads getFilesPreviouslyUploaded(Context c) {
        PriorUploads uploads = PriorUploads.loadFromFile(c, jobId);
        if(uploads.isOutOfSyncWithFileSystem(c)) {
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
