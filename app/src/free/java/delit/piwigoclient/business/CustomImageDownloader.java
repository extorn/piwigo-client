package delit.piwigoclient.business;

import android.content.Context;

import androidx.annotation.NonNull;

/**
 * Created by gareth on 18/05/17.
 */

public class CustomImageDownloader extends AbstractBaseCustomImageDownloader {

    private static final String TAG = "CustomImageDwnldr";

    public CustomImageDownloader(@NonNull Context context, @NonNull ConnectionPreferences.ProfilePreferences connectionPrefs) {
        super(context, connectionPrefs);
    }

    public CustomImageDownloader(@NonNull Context context) {
        super(context);
    }


}