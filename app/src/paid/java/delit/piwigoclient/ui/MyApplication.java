package delit.piwigoclient.ui;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

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
                        editor.putStringSet(getString(R.string.preference_data_upload_automatic_job_file_exts_uploaded_key), new HashSet<>(fileExts));
                        editor.apply();
                    }
                } catch (ClassCastException e) {
                    // preference already migrated
                }
            }
        });
        return migrators;
    }
}
