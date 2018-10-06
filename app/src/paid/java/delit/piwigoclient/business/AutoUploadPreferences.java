package delit.piwigoclient.business;

import android.content.Context;
import android.content.SharedPreferences;

import delit.piwigoclient.R;

public class AutoUploadPreferences {
    public static int getPollIntervalMins(Context context, SharedPreferences prefs) {
        return prefs.getInt(context.getString(R.string.preference_data_upload_automatic_upload_polling_interval_key), context.getResources().getInteger(R.integer.preference_data_upload_automatic_upload_polling_interval_default));
    }
}
