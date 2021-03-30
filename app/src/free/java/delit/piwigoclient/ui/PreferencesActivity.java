package delit.piwigoclient.ui;

import android.content.Context;
import android.content.Intent;

import delit.piwigoclient.ui.common.ActivityUIHelper;

public class PreferencesActivity<A extends PreferencesActivity<A,AUIH>, AUIH extends ActivityUIHelper<AUIH, A>>  extends AbstractPreferencesActivity<A,AUIH> {

    public static Intent buildIntent(Context context) {
        Intent i = new Intent(context.getApplicationContext(), PreferencesActivity.class);
        return i;
    }

}
