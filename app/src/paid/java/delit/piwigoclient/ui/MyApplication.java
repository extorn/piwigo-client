package delit.piwigoclient.ui;

import delit.piwigoclient.piwigoApi.upload.BackgroundPiwigoUploadService;
import delit.piwigoclient.ui.preferences.AutoUploadJobsConfig;

public class MyApplication extends AbstractMyApplication {

    @Override
    protected void onAppCreate() {
        if(new AutoUploadJobsConfig(getPrefs()).isBackgroundUploadEnabled(getApplicationContext())) {
            if (!BackgroundPiwigoUploadService.isStarted()) {
                BackgroundPiwigoUploadService.startService(getApplicationContext(), true);
            }
        }
    }
}
