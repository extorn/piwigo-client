package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import delit.libs.util.CollectionUtils;
import delit.piwigoclient.R;

public class AutoUploadJobsConfig {
    private final SharedPreferences prefs;

    public AutoUploadJobsConfig(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public AutoUploadJobsConfig(@NonNull Context c) {
        prefs = PreferenceManager.getDefaultSharedPreferences(c);
    }

    private boolean getBooleanValue(@NonNull Context c, @StringRes int prefKeyId) {
        if(!prefs.contains(c.getString(prefKeyId))) {
            throw new IllegalStateException("Job misconfigured");
        }
        return prefs.getBoolean(c.getString(prefKeyId), false);
    }

    private boolean getBooleanValue(@NonNull Context c, @StringRes int prefKeyId, boolean defaultVal) {
        return prefs.getBoolean(c.getString(prefKeyId), defaultVal);
    }

    private int getIntValue(@NonNull Context c, @StringRes int prefKeyId) {
        if(!prefs.contains(c.getString(prefKeyId))) {
            throw new IllegalStateException("Job misconfigured");
        }
        return prefs.getInt(c.getString(prefKeyId), -1);
    }

    private String getStringValue(@NonNull Context c, @StringRes int prefKeyId) {
        String value = prefs.getString(c.getString(prefKeyId), null);
        return value;
    }

    private @NonNull List getCsvListValue(@NonNull Context c, @StringRes int prefKeyId) {
        String value = getStringValue(c, prefKeyId);
        if(value != null) {
            String[] values = value.split(",");
            return Arrays.asList(values);
        }
        return new ArrayList(0);
    }

    public boolean isBackgroundUploadEnabled(@NonNull Context c) {
        return getBooleanValue(c, R.string.preference_data_upload_automatic_upload_enabled_key, c.getResources().getBoolean(R.bool.preference_data_upload_automatic_upload_enabled_default));
    }

    public boolean isUploadOnUnMeteredNetworkOnly(@NonNull Context c) {
        return getBooleanValue(c, R.string.preference_data_upload_automatic_upload_wireless_only_key, c.getResources().getBoolean(R.bool.preference_data_upload_automatic_upload_wireless_only_default));
    }

    public boolean hasUploadJobs(@NonNull Context c) {
        return getCsvListValue(c, R.string.preference_data_upload_automatic_upload_jobs_key).size() > 0;
    }

    public int countEnabledUploadJobs(@NonNull Context c) {
        List<AutoUploadJobConfig> uploadJobs = getAutoUploadJobs(c);
        int count = 0;
        if (uploadJobs != null) {
            for (AutoUploadJobConfig config : uploadJobs) {
                if (config.isJobValid(c) && config.isJobEnabled(c)) {
                    count++;
                }
            }
        }
        return count;
    }

    public List<AutoUploadJobConfig> getAutoUploadJobs(@NonNull Context c) {
        String jobIdsStr = getStringValue(c, R.string.preference_data_upload_automatic_upload_jobs_key);
        ArrayList<Integer> uploadJobIds = CollectionUtils.integersFromCsvList(jobIdsStr);
        List<AutoUploadJobConfig> jobs = new ArrayList<>(uploadJobIds.size());
        for(int jobId : uploadJobIds) {
            jobs.add(new AutoUploadJobConfig(jobId));
        }
        return jobs;
    }

    public AutoUploadJobConfig getAutoUploadJobConfig(int jobConfigId, @NonNull Context context) {
        for(AutoUploadJobConfig cfg : getAutoUploadJobs(context)) {
            if(cfg.getJobId() == jobConfigId) {
                return cfg;
            }
        }
        return null;
    }

    public boolean isExistsABackgroundJobRequiringExternalPower(@NonNull Context context) {
        for(AutoUploadJobConfig job : getAutoUploadJobs(context)) {
            if(job.isUploadWithExternalPowerOnly(context)) {
                return true;
            }
        }
        return false;
    }
}
