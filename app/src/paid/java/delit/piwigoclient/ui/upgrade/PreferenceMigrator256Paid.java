package delit.piwigoclient.ui.upgrade;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.crashlytics.android.Crashlytics;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import delit.libs.util.CollectionUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.ui.preferences.AutoUploadJobConfig;
import delit.piwigoclient.ui.preferences.AutoUploadJobsConfig;

public class PreferenceMigrator256Paid extends PreferenceMigrator {

    private static final String TAG = "PrefMigrator";

    public PreferenceMigrator256Paid() {
        super(256);
    }

    @Override
    protected void upgradePreferences(Context context, SharedPreferences prefs, SharedPreferences.Editor editor) {
        List<AutoUploadJobConfig> autoUploadJobs = new AutoUploadJobsConfig(prefs).getAutoUploadJobs(context);
        Crashlytics.log(Log.INFO, TAG, "found auto upload jobs : " + autoUploadJobs.size());
        for (AutoUploadJobConfig cfg : autoUploadJobs) {
            try {
                Set<String> items = cfg.getFileExtsToUpload(context);
                Crashlytics.log(Log.INFO, TAG, "auto upload job set to upload exts : " + CollectionUtils.toCsvList(items));
            } catch (ClassCastException e) {
                Crashlytics.log(Log.INFO, TAG, "auto upload job preference needs upgrade");
                SharedPreferences jobSharedPrefs = cfg.getJobPreferences(context);
                // need to migrate this preference from a csv string
                String key = context.getString(R.string.preference_data_upload_automatic_job_file_exts_uploaded_key);
                String fileExtsCsvList = jobSharedPrefs.getString(key, null);
                HashSet<String> values = new HashSet<>(CollectionUtils.stringsFromCsvList(fileExtsCsvList));
                HashSet<String> cleanedValues = new HashSet<>(values.size());
                for (String value : values) {
                    int dotIdx = value.indexOf('.');
                    if (dotIdx < 0) {
                        cleanedValues.add(value.toLowerCase());
                    } else {
                        cleanedValues.add(value.substring(dotIdx + 1).toLowerCase());
                    }
                }
                Crashlytics.log(Log.INFO, TAG, "auto upload job preference extracted and ready for migration");

                SharedPreferences.Editor jobPrefsEditor = jobSharedPrefs.edit();
                jobPrefsEditor.remove(key);
                jobPrefsEditor.putStringSet(key, cleanedValues);
                jobPrefsEditor.apply();
                Crashlytics.log(Log.INFO, TAG, "auto upload job preference upgraded from string (" + fileExtsCsvList + ") to string set (" + CollectionUtils.toCsvList(cleanedValues) + ")");
            }
        }
    }
}
