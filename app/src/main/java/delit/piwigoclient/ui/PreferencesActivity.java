package delit.piwigoclient.ui;

import android.os.Bundle;

import org.greenrobot.eventbus.EventBus;

import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.ui.common.MyActivity;
import delit.piwigoclient.ui.common.util.BundleUtils;
import delit.piwigoclient.ui.preferences.PreferencesFragment;

public class PreferencesActivity extends MyActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_preferences);
        EventBus.getDefault().register(this);
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, new PreferencesFragment()).commit();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if(BuildConfig.DEBUG) {
            BundleUtils.logSize("Current Preferences Activity", outState);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }
}
