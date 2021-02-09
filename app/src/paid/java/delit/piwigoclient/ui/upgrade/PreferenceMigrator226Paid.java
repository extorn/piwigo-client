package delit.piwigoclient.ui.upgrade;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;

import delit.libs.util.CollectionUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.ui.preferences.AutoUploadJobConfig;
import delit.piwigoclient.ui.preferences.AutoUploadJobsConfig;

public class PreferenceMigrator226Paid extends PreferenceMigrator {
    public PreferenceMigrator226Paid() {
        super(226);
    }

    @Override
    protected void upgradePreferences(Context context, SharedPreferences prefs, SharedPreferences.Editor editor) {
        try {
            for (AutoUploadJobConfig config : new AutoUploadJobsConfig(prefs).getAutoUploadJobs(context)) {
                SharedPreferences jobPrefs = config.getJobPreferences(context);
                String oldVal = jobPrefs.getString(context.getString(R.string.preference_data_upload_automatic_job_file_exts_uploaded_key), context.getString(R.string.preference_data_upload_automatic_job_file_exts_uploaded_default));
                ArrayList<String> fileExts = CollectionUtils.stringsFromCsvList(oldVal);
                SharedPreferences.Editor jobPrefsEditor = jobPrefs.edit();
                jobPrefsEditor.putStringSet(context.getString(R.string.preference_data_upload_automatic_job_file_exts_uploaded_key), new HashSet<>(fileExts));
                jobPrefsEditor.apply();
            }
        } catch (ClassCastException e) {
            // preference already migrated
        }
    }
}
