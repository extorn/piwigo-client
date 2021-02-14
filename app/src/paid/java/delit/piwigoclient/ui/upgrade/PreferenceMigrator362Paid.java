package delit.piwigoclient.ui.upgrade;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.util.CollectionUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.ui.preferences.AutoUploadJobConfig;
import delit.piwigoclient.ui.preferences.AutoUploadJobsConfig;

public class PreferenceMigrator362Paid extends PreferenceMigrator {

    public PreferenceMigrator362Paid() {
        super(362);
    }

    @Override
    protected void upgradePreferences(Context context, SharedPreferences prefs, SharedPreferences.Editor editor) {
        List<AutoUploadJobConfig> autoUploadJobsConfig = new AutoUploadJobsConfig(context).getAutoUploadJobs(context);
        boolean fixedUploadPref = false;
        for (AutoUploadJobConfig config : autoUploadJobsConfig) {
            SharedPreferences jobPrefs = config.getJobPreferences(context);
            try {
                Set<String> currentVal = jobPrefs.getStringSet(context.getString(R.string.preference_data_upload_automatic_job_file_exts_uploaded_key), null);
            } catch(ClassCastException e){
                fixedUploadPref = true;
                String oldVal = jobPrefs.getString(context.getString(R.string.preference_data_upload_automatic_job_file_exts_uploaded_default), context.getString(R.string.preference_data_upload_automatic_job_file_exts_uploaded_default));
                ArrayList<String> fileExts = CollectionUtils.stringsFromCsvList(oldVal);
                SharedPreferences.Editor jobPrefsEditor = jobPrefs.edit();
                jobPrefsEditor.putStringSet(context.getString(R.string.preference_data_upload_automatic_job_file_exts_uploaded_key), new HashSet<>(fileExts));
                jobPrefsEditor.apply();
            }
        }
        if(fixedUploadPref) {
            Logging.logAnalyticEvent(context, "Upgraded AutoUploadPref FileExts");
        }
    }


}
