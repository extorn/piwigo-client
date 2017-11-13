package delit.piwigoclient.ui.common;

import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;

/**
 * Created by gareth on 17/10/17.
 */

public class AppCompatActivityUIHelper extends UIHelper<AppCompatActivity> {
    public AppCompatActivityUIHelper(AppCompatActivity parent, SharedPreferences prefs) {
        super(parent, prefs, parent);
    }

    @Override
    protected boolean canShowDialog() {
        return !getParent().isFinishing();
    }
}
