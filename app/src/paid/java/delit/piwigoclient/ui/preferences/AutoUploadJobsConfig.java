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

    private @NonNull String getStringValue(Context c, @StringRes int prefKeyId) {
        String value = prefs.getString(c.getString(prefKeyId), null);
        if(value == null) {
            throw new IllegalStateException("Job misconfigured");
        }
        return value;
    }

    private @NonNull List getCsvListValue(Context c, @StringRes int prefKeyId) {
        String value = getStringValue(c, prefKeyId);
        String[] values = value.split(",");
        return Arrays.asList(values);
    }

    public boolean isBackgroundUploadEnabled(Context c) {
        return getBooleanValue(c, R.string.preference_data_upload_automatic_upload_enable_key, false);
    }

    public boolean isUploadOnWirelessOnly(Context c) {
        return getBooleanValue(c, R.string.preference_data_upload_automatic_upload_wireless_only_key, c.getResources().getBoolean(R.bool.preference_data_upload_automatic_upload_wireless_only_default));
    }

    public int getUploadJobsCount(Context c) {
        try {
            return getIntValue(c, R.string.preference_data_upload_automatic_upload_jobs_key);
        } catch(IllegalStateException e) {
            return 0;
        }
    }

    public List<AutoUploadJobConfig> getAutoUploadJobs(Context c) {
        int uploadJobCount = getUploadJobsCount(c);
        List<AutoUploadJobConfig> jobs = new ArrayList<>(uploadJobCount);
        for(int i = 0; i < uploadJobCount; i++) {
            jobs.add(new AutoUploadJobConfig(c, i));
        }
        return jobs;
    }
}
