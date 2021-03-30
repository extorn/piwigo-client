package delit.piwigoclient.subscription.api;

import android.util.Log;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;

import java.util.List;

import delit.libs.core.util.Logging;

public class MyPurchasesUpdatedListener implements PurchasesUpdatedListener {

    private static final String TAG = "MyPurchasesUpdatedListener";
    private final PurchaseHandler purchaseHandler;

        public MyPurchasesUpdatedListener(PurchaseHandler handler) {
            this.purchaseHandler = handler;
        }
        
        @Override
        public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
            switch (billingResult.getResponseCode()) {
                case BillingClient.BillingResponseCode.DEVELOPER_ERROR:
                    Logging.log(Log.ERROR, TAG, "PurchaseUpdate failed with developer error. Error %1$d : %2$s", billingResult.getResponseCode(), billingResult.getDebugMessage());
                    break;
                case BillingClient.BillingResponseCode.OK:
                    if(purchases != null) {
                        purchaseHandler.handlePurchases(purchases);
                    }
                    break;
                case BillingClient.BillingResponseCode.USER_CANCELED:
                    //TODO  Show user a message
                    break;
                case BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED:
                    //TODO Show user a message
                    break;
                default:
                    Logging.log(Log.WARN, TAG, "PurchaseUpdate failed. Error %1$d : %2$s", billingResult.getResponseCode(), billingResult.getDebugMessage());
                    //TODO Handle any other error codes.
            }
        }
    }