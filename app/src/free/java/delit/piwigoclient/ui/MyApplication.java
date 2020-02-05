package delit.piwigoclient.ui;

import android.content.Context;

import delit.piwigoclient.business.AppPreferences;

public class MyApplication extends AbstractMyApplication {
    @Override
    protected String getDesiredLanguage(Context context) {
        return AppPreferences.getDesiredLanguage(getPrefs(context), context);
    }

    @Override
    protected void onAppCreate() {
    }
}
