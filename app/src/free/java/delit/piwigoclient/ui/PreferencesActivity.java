package delit.piwigoclient.ui;

import android.content.Context;
import android.content.Intent;

public class PreferencesActivity extends AbstractPreferencesActivity {

    public static Intent buildIntent(Context context) {
        Intent i = new Intent(context.getApplicationContext(), PreferencesActivity.class);
        return i;
    }

}
