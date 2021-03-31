package delit.piwigoclient.ui;

import android.content.ActivityNotFoundException;
import android.os.Bundle;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import delit.libs.core.util.Logging;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.ui.common.ActivityUIHelper;
import delit.piwigoclient.ui.events.NavigationItemSelectEvent;
import delit.piwigoclient.ui.events.ViewTagEvent;
import delit.piwigoclient.ui.events.trackable.TagSelectionNeededEvent;
import delit.piwigoclient.ui.subscription.PermittedActions;

/**
 * Created by gareth on 07/04/18.
 */

public class MainActivity<A extends MainActivity<A, AUIH>, AUIH extends ActivityUIHelper<AUIH, A>> extends AbstractMainActivity<A, AUIH> {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onNavigationItemSelected(NavigationItemSelectEvent event, int itemId) {
        int id = event.navigationitemSelected;
        if (id == R.id.nav_app_purchases) {
            showAppPurchases();
        }
    }

    private void showAppPurchases() {
        try {
            startActivity(AppPurchasesActivity.buildIntent(this));
        } catch(ActivityNotFoundException e) {
            Logging.recordException(e);
        }
    }

    protected void showFavorites() {
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        if (!obtainViewModel(PermittedActions.class).hasFavorites()) {
            if (sessionDetails.getPiwigoClientPluginVersion() == null) {
                getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_paid_or_subscription_feature_only_piwigo_client_plugin), R.string.button_close);
            } else {
                getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_paid_or_subscription_feature_only), R.string.button_close);
            }
            return;
        }
        super.showFavorites();
    }

    protected void showOrphans() {
        if (!obtainViewModel(PermittedActions.class).hasOrphans()) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_paid_or_subscription_feature_only), R.string.button_close);
            return;
        }
        super.showOrphans();
    }

    protected void showTags() {
        if (!obtainViewModel(PermittedActions.class).hasTags()) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_paid_or_subscription_feature_only), R.string.button_close);
            return;
        }
        super.showTags();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ViewTagEvent event) {
        if (!obtainViewModel(PermittedActions.class).hasTags()) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_paid_or_subscription_feature_only), R.string.button_close);
            return;
        }
        super.onEvent(event);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(TagSelectionNeededEvent event) {
        if (!obtainViewModel(PermittedActions.class).hasTags()) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_paid_or_subscription_feature_only), R.string.button_close);
            return;
        }
        super.onEvent(event);
    }
}