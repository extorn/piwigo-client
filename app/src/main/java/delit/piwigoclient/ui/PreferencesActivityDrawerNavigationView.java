package delit.piwigoclient.ui;

import android.content.Context;
import android.util.AttributeSet;

/**
 * Created by gareth on 08/06/17.
 */

public class PreferencesActivityDrawerNavigationView extends BaseActivityDrawerNavigationView {

    private static final String TAG = "PrefsNavView";

    public PreferencesActivityDrawerNavigationView(Context context) {
        this(context, null);
    }

    public PreferencesActivityDrawerNavigationView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PreferencesActivityDrawerNavigationView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

}
