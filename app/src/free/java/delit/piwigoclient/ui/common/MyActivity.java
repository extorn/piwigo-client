package delit.piwigoclient.ui.common;

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.ExecutorManager;
import delit.piwigoclient.R;
import delit.piwigoclient.subscription.api.ExistingPurchases;
import delit.piwigoclient.subscription.piwigo.PiwigoClientSubscriptionManager;
import delit.piwigoclient.ui.AdsManager;

/**
 * Created by gareth on 26/05/17.
 */

public abstract class MyActivity<A extends MyActivity<A, AUIH>, AUIH extends ActivityUIHelper<AUIH, A>> extends BaseMyActivity<A, AUIH> {

    private static final String TAG = "MyActivity";
    private ExecutorManager activityExecutorManager;

    public MyActivity(@LayoutRes int contentView) {
        super(contentView);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityExecutorManager = new ExecutorManager(1,1,1,1);
        activityExecutorManager.blockIfBusy(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        activityExecutorManager.submit(this::updateSettingsBasedOnSubscriptions);
    }

    private void updateSettingsBasedOnSubscriptions() {
        PiwigoClientSubscriptionManager subscriptionManager = new PiwigoClientSubscriptionManager(this);
        subscriptionManager.getProductPurchases(this);
        ExistingPurchases purchases = subscriptionManager.getExistingPurchases();
        purchases.waitForLoad(5000);
        if(!purchases.isLoaded()) {
            getUiHelper().showDetailedMsg(R.string.alert_error, getString(R.string.unable_to_validate_subscription));
            Logging.log(Log.ERROR, TAG, "Unable to validate subscription information");
        }
        subscriptionManager.closeConnection();
        if(purchases.isLoaded() && purchases.getAllSubscriptions().isEmpty()) {
            AdsManager.getInstance(this).setAdvertsDisabled(false);
        }
        AdsManager.getInstance(this).updateShowAdvertsSetting(this);
    }

    @Override
    protected void onAppPaused() {
    }

    @Override
    protected void onAppResumed() {
    }
}
