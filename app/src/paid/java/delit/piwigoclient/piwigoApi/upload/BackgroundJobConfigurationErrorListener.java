package delit.piwigoclient.piwigoApi.upload;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import delit.libs.core.util.Logging;

public class BackgroundJobConfigurationErrorListener {

    private static final String TAG = "BGJConfigErrorListener";
    private final Context context;

    public BackgroundJobConfigurationErrorListener(@NonNull Context context) {
        this.context = context;
    }

    public void postError(int backgroundJobId, String error) {
        //FIXME Link this to something the user sees somehow.
        Logging.log(Log.WARN,TAG,error);
        Bundle b = new Bundle();
        b.putString("message", error);
        Logging.logAnalyticEvent(context, "BGJobError",b);
    }
}
