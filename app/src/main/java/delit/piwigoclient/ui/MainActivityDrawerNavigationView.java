package delit.piwigoclient.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.ui.events.RewardUpdateEvent;

/**
 * Created by gareth on 08/06/17.
 */

public class MainActivityDrawerNavigationView extends BaseActivityDrawerNavigationView {

    private static final String TAG = "MainNavView";

    public MainActivityDrawerNavigationView(Context context) {
        this(context, null);
    }

    public MainActivityDrawerNavigationView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MainActivityDrawerNavigationView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(RewardUpdateEvent event) {
        MenuItem item = getMenu().findItem(R.id.nav_buy_time);
        long millis = event.getRewardTimeRemaining();
        if (millis > 0) {
            long mins = millis / (60000);
            long secs = (millis - (mins * 60000)) / 1000;
            item.setTitle(getContext().getString(R.string.bought_time_menu_title_pattern, mins, secs));
            if (mins > 30) {
                item.setEnabled(false);
            }
        } else {
            item.setEnabled(true);
            item.setTitle(getContext().getString(R.string.buy_time_menu_title));
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.nav_lock:
                showLockDialog();
                break;
            case R.id.nav_unlock:
                showUnlockDialog();
                break;
            case R.id.nav_online_mode:
                configureNetworkAccess(true);
                break;
            case R.id.nav_offline_mode:
                configureNetworkAccess(false);
                break;
            default:
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
        boolean isAdminUser = sessionDetails != null && sessionDetails.isAdminUser();
        boolean hasCommunityPlugin = sessionDetails != null && sessionDetails.isUseCommunityPlugin();
//            m.findItem(R.id.nav_gallery).setVisible(PiwigoSessionDetails.isLoggedInAndHaveSessionAndUserDetails());
        m.findItem(R.id.nav_upload).setVisible((isAdminUser || hasCommunityPlugin) && !isReadOnly);
        m.findItem(R.id.nav_groups).setVisible(isAdminUser && !isReadOnly);
        m.findItem(R.id.nav_users).setVisible(isAdminUser && !isReadOnly);

        m.findItem(R.id.nav_settings).setVisible(!isReadOnly);
        // only allow locking of the app if we've got an active login to PIWIGO.
        m.findItem(R.id.nav_lock).setVisible(!isReadOnly && sessionDetails != null && sessionDetails.isFullyLoggedIn() && !sessionDetails.isGuest());
        m.findItem(R.id.nav_unlock).setVisible(isReadOnly);

        m.findItem(R.id.nav_buy_time).setVisible(!BuildConfig.PAID_VERSION);

        m.findItem(R.id.nav_offline_mode).setVisible(sessionDetails == null || !sessionDetails.isCached());
        m.findItem(R.id.nav_online_mode).setVisible(sessionDetails != null && sessionDetails.isCached());
    }

}
