package delit.piwigoclient.ui;

import android.content.ActivityNotFoundException;
import android.os.Bundle;

import delit.libs.core.util.Logging;
import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.ActivityUIHelper;
import delit.piwigoclient.ui.events.NavigationItemSelectEvent;

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

    @Override
    protected void showFavorites() {
        getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_paid_feature_only), R.string.button_close);
    }

    @Override
    protected void showOrphans() {
        getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_paid_feature_only), R.string.button_close);
    }

    @Override
    protected void showTags() {
        getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_paid_feature_only), R.string.button_close);
    }
}