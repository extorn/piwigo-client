package delit.piwigoclient.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;

import org.greenrobot.eventbus.EventBus;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;

/**
 * Created by gareth on 08/06/17.
 */

public class UploadActivityDrawerNavigationView extends BaseActivityDrawerNavigationView {

    private static final String TAG = "NavView";

    public UploadActivityDrawerNavigationView(Context context) {
        this(context, null);
    }

    public UploadActivityDrawerNavigationView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public UploadActivityDrawerNavigationView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.nav_online_mode) {
            configureNetworkAccess(true);
        } else if (itemId == R.id.nav_offline_mode) {
            configureNetworkAccess(false);
        } else {
            super.onNavigationItemSelected(item);
        }
        return true;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        EventBus.getDefault().unregister(this);
        super.onDetachedFromWindow();
    }

    @Override
    protected void setMenuVisibilityToMatchSessionState(boolean isReadOnly) {
        Menu m = getMenu();
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        m.findItem(R.id.nav_settings).setVisible(!isReadOnly);
        m.findItem(R.id.nav_offline_mode).setVisible(sessionDetails == null || !sessionDetails.isCached());
        m.findItem(R.id.nav_online_mode).setVisible(sessionDetails != null && sessionDetails.isCached());
    }

}
