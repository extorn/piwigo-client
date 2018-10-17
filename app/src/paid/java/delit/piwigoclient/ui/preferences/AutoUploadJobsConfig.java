package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import delit.piwigoclient.R;
import delit.piwigoclient.util.CollectionUtils;

public class AutoUploadJobsConfig {
    private SharedPreferences prefs;

    public AutoUploadJobsConfig(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public AutoUploadJobsConfig(Context c) {
        prefs = PreferenceManager.getDefaultSharedPreferences(c);
    }

    private boolean getBooleanValue(Context c, @StringRes int prefKeyId) {
        if(!prefs.contains(c.getString(prefKeyId))) {
            throw new IllegalStateException("Job misconfigured");
        }
        return prefs.getBoolean(c.getString(prefKeyId), false);
    }

    private boolean getBooleanValue(Context c, @StringRes int prefKeyId, boolean defaultVal) {
        return prefs.getBoolean(c.getString(prefKeyId), defaultVal);
    }

    private int getIntValue(Context c, @StringRes int prefKeyId) {
        if(!prefs.contains(c.getString(prefKeyId))) {
            throw new IllegalStateException("Job misconfigured");
        }
        return prefs.getInt(c.getString(prefKeyId), -1);
    }

    private String getStringValue(Context c, @StringRes int prefKeyId) {
        String value = prefs.getString(c.getString(prefKeyId), null);
        return value;
    }

    private @NonNull List getCsvListValue(Context c, @StringRes int prefKeyId) {
        String value = getStringValue(c, prefKeyId);
        if(value != null) {
            String[] values = value.split(",");
            return Arrays.asList(values);
        }
        return new ArrayList(0);
    }

    public boolean isBackgroundUploadEnabled(Context c) {
        return getBooleanValue(c, R.string.preference_data_upload_automatic_upload_enabled_key, c.getResources().getBoolean(R.bool.preference_data_upload_automatic_upload_enabled_default));
    }

    public boolean isUploadOnWirelessOnly(Context c) {
        return getBooleanValue(c, R.string.preference_data_upload_automatic_upload_wireless_only_key, c.getResources().getBoolean(R.bool.preference_data_upload_automatic_upload_wireless_only_default));
    }

    public boolean hasUploadJobs(Context c) {
        return getCsvListValue(c, R.string.preference_data_upload_automatic_upload_jobs_key).size() > 0;
    }

    public List<AutoUploadJobConfig> getAutoUploadJobs(Context c) {
        String jobIdsStr = getStringValue(c, R.string.preference_data_upload_automatic_upload_jobs_key);
        ArrayList<Integer> uploadJobIds = CollectionUtils.integersFromCsvList(jobIdsStr);
        List<AutoUploadJobConfig> jobs = new ArrayList<>(uploadJobIds.size());
        for(int jobId : uploadJobIds) {
            jobs.add(new AutoUploadJobConfig(jobId));
        }
        return jobs;
    }

    public AutoUploadJobConfig getAutoUploadJobConfig(int jobConfigId, Context context) {
        for(AutoUploadJobConfig cfg : getAutoUploadJobs(context)) {
            if(cfg.getJobId() == jobConfigId) {
                return cfg;
            }
        }
        return null;
    }
}
