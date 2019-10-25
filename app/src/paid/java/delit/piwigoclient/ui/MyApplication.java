package delit.piwigoclient.ui;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import delit.libs.util.CollectionUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.upload.BackgroundPiwigoUploadService;
import delit.piwigoclient.ui.preferences.AutoUploadJobConfig;
import delit.piwigoclient.ui.preferences.AutoUploadJobsConfig;

public class MyApplication extends AbstractMyApplication {

    @Override
    protected void onAppCreate() {
        if(new AutoUploadJobsConfig(getPrefs()).isBackgroundUploadEnabled(getApplicationContext())) {
            if (!BackgroundPiwigoUploadService.isStarted()) {
                BackgroundPiwigoUploadService.startService(getApplicationContext());
            }
        }
        // start the database
        //TODO start the previously uploaded files database here
//        PiwigoDatabase.getInstance(this);
    }


    @Override
    protected List<PreferenceMigrator> getPreferenceMigrators() {
        List<PreferenceMigrator> migrators = super.getPreferenceMigrators();
        migrators.add(new PreferenceMigrator(226) {
            @Override
            protected void upgradePreferences(Context context, SharedPreferences prefs, SharedPreferences.Editor editor) {
                try {
                    for (AutoUploadJobConfig config : new AutoUploadJobsConfig(prefs).getAutoUploadJobs(context)) {
                        SharedPreferences jobPrefs = config.getJobPreferences(context);
                        String oldVal = jobPrefs.getString(getString(R.string.preference_data_upload_automatic_job_file_exts_uploaded_key), getString(R.string.preference_data_upload_automatic_job_file_exts_uploaded_default));
                        ArrayList<String> fileExts = CollectionUtils.stringsFromCsvList(oldVal);
                        SharedPreferences.Editor jobPrefsEditor = jobPrefs.edit();
                        jobPrefsEditor.putStringSet(getString(R.string.preference_data_upload_automatic_job_file_exts_uploaded_key), new HashSet<>(fileExts));
                        jobPrefsEditor.apply();
                    }
                } catch (ClassCastException e) {
                    // preference already migrated
                }
            }
        });
        migrators.add(new PreferenceMigrator(235) {

            @Override
            protected void upgradePreferences(Context context, SharedPreferences prefs, SharedPreferences.Editor editor) {
                List<AutoUploadJobConfig> autoUploadJobs = new AutoUploadJobsConfig(prefs).getAutoUploadJobs(context);
                for (AutoUploadJobConfig cfg : autoUploadJobs) {
                    SharedPreferences jobSharedPrefs = cfg.getJobPreferences(context);
                    try {
                        cfg.getFileExtsToUpload(context);
                    } catch (ClassCastException e) {
                        SharedPreferences.Editor jobPrefsEditor = jobSharedPrefs.edit();
                        // need to migrate this preference from a csv string
                        String key = getString(R.string.preference_data_upload_automatic_job_file_exts_uploaded_key);
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
                        jobPrefsEditor.remove(key);
                        jobPrefsEditor.putStringSet(key, cleanedValues);
                        jobPrefsEditor.apply();
                    }
                }
            }
        });
        return migrators;
    }
}
