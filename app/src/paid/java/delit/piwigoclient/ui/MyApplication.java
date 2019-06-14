package delit.piwigoclient.ui;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;

import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.upload.BackgroundPiwigoUploadService;
import delit.piwigoclient.ui.preferences.AutoUploadJobConfig;
import delit.piwigoclient.ui.preferences.AutoUploadJobsConfig;
import delit.piwigoclient.util.CollectionUtils;

public class MyApplication extends AbstractMyApplication {

    @Override
    protected void onAppCreate() {
        if(new AutoUploadJobsConfig(getPrefs()).isBackgroundUploadEnabled(getApplicationContext())) {
            if (!BackgroundPiwigoUploadService.isStarted()) {
                BackgroundPiwigoUploadService.startService(getApplicationContext());
            }
        }
    }


    @Override
    protected void upgradeAnyPreferencesIfRequired(SharedPreferences prefs, int prefsVersion) {
        super.upgradeAnyPreferencesIfRequired(prefs, prefsVersion);

        if (prefsVersion < 216) {
            for (AutoUploadJobConfig config : new AutoUploadJobsConfig(prefs).getAutoUploadJobs(getApplicationContext())) {
                SharedPreferences jobPrefs = config.getJobPreferences(getApplicationContext());
                SharedPreferences.Editor editor = jobPrefs.edit();
                String oldVal = jobPrefs.getString(getString(R.string.preference_data_upload_automatic_job_file_exts_uploaded_key), getString(R.string.preference_data_upload_automatic_job_file_exts_uploaded_default));
                ArrayList<String> fileExts = CollectionUtils.stringsFromCsvList(oldVal);
                editor.putStringSet(getString(R.string.preference_data_upload_automatic_job_file_exts_uploaded_key), new HashSet<>(fileExts));
                editor.apply();
            }
        }
    }
}
