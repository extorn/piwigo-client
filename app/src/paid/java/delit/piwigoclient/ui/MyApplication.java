package delit.piwigoclient.ui;

import android.content.Context;

import java.util.List;

import delit.piwigoclient.business.AppPreferences;
import delit.piwigoclient.piwigoApi.upload.BackgroundPiwigoUploadService;
import delit.piwigoclient.ui.preferences.AutoUploadJobsConfig;
import delit.piwigoclient.ui.upgrade.PreferenceMigrator;
import delit.piwigoclient.ui.upgrade.PreferenceMigrator226Paid;
import delit.piwigoclient.ui.upgrade.PreferenceMigrator256Paid;
import delit.piwigoclient.ui.upgrade.PreferenceMigrator362Paid;
import delit.piwigoclient.ui.upgrade.PreferenceMigrator392Paid;

public class MyApplication extends AbstractMyApplication {

    @Override
    protected void onAppCreate() {
        if(new AutoUploadJobsConfig(getPrefs()).isBackgroundUploadEnabled(getApplicationContext())) {
            if (!BackgroundPiwigoUploadService.isStarted(this)) {
                BackgroundPiwigoUploadService.startService(getApplicationContext());
            }
        }
        // start the database
        //TODO start the previously uploaded files database here
//        AppSettingsDatabase.getInstance(this);
//        PiwigoDatabase.getInstance(this);
    }

    @Override
    protected String getDesiredLanguage(Context context) {
        return AppPreferences.getDesiredLanguage(getPrefs(context), context);
    }

    @Override
    protected List<PreferenceMigrator> getPreferenceMigrators() {
        List<PreferenceMigrator> migrators = super.getPreferenceMigrators();
        migrators.add(new PreferenceMigrator226Paid());
        migrators.add(new PreferenceMigrator256Paid());
        migrators.add(new PreferenceMigrator362Paid());
        migrators.add(new PreferenceMigrator392Paid());
        return migrators;
    }

}
