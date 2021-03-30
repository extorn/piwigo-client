package delit.piwigoclient.subscription.api;

import android.util.Log;

import androidx.annotation.NonNull;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.Purchase;

import delit.libs.core.util.Logging;

public class ActivePurchaseRetriever {

    private static final String TAG = "PurchaseChecker";
    private final BillingClient client;
    private final PurchaseHandler purchaseHandler;

    public ActivePurchaseRetriever(BillingClient client, PurchaseHandler handler) {
        this.purchaseHandler = handler;
        this.client = client;
    }

    /**
     * @param purchaseType Either {@link BillingClient.SkuType#SUBS} or {@link BillingClient.SkuType#INAPP}
     */
    public void retrievePurchases(@NonNull String purchaseType) {
        purchaseHandler.clearAll(purchaseType);
        Purchase.PurchasesResult result = client.queryPurchases(purchaseType);
        switch (result.getResponseCode()) {
            case BillingClient.BillingResponseCode.DEVELOPER_ERROR:
                Logging.log(Log.ERROR, TAG, "Purchases Retrieval failed with developer error. Error %1$d : %2$s", result.getResponseCode(), result.getBillingResult().getDebugMessage());
                break;
            case BillingClient.BillingResponseCode.OK:
                purchaseHandler.handlePurchases(purchaseType, result.getPurchasesList());
                break;
            default:
                Logging.log(Log.WARN, TAG, "Product retrieval failed. Error %1$d : %2$s", result.getResponseCode(), result.getBillingResult().getDebugMessage());
        }
    }

    public void retrievePurchases() {
        retrievePurchases(BillingClient.SkuType.SUBS);
        retrievePurchases(BillingClient.SkuType.INAPP);
    }
}