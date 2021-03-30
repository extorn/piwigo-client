package delit.piwigoclient.subscription.api;

import android.util.Log;

import androidx.annotation.NonNull;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingResult;

import java.util.List;

import delit.libs.core.util.Logging;

public class MyBillingClientInitialiser implements BillingClientStateListener {

    private static final String TAG = "MyBillingClientInitialiser";
    private final PurchaseHandler priorPurchasesHandler;
    private final BillingClient billingClient;
    private final List<String> productSkus;
    private final AvailableProducts availableProducts;
    private final ConnectionListener connectionListener;

    public MyBillingClientInitialiser(@NonNull BillingClient billingClient, @NonNull List<String> productSkus, @NonNull AvailableProducts products, @NonNull PurchaseHandler priorPurchasesHandler, @NonNull ConnectionListener connectionListener) {
        this.billingClient = billingClient;
        this.priorPurchasesHandler = priorPurchasesHandler;
        this.availableProducts = products;
        this.productSkus = productSkus;
        this.connectionListener = connectionListener;
    }

    @Override
    public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
        switch (billingResult.getResponseCode()) {
            case BillingClient.BillingResponseCode.DEVELOPER_ERROR:
                Logging.log(Log.ERROR, TAG, "Billing initialisation failed with developer error. Error %1$d : %2$s", billingResult.getResponseCode(), billingResult.getDebugMessage());
                break;
            case BillingClient.BillingResponseCode.OK:
                onConnectionEstablished();
                break;
            default:
                Logging.log(Log.WARN, TAG, "Product retrieval failed. Error %1$d : %2$s", billingResult.getResponseCode(), billingResult.getDebugMessage());
        }
    }

    private void onConnectionEstablished() {
        AvailableProductsListener onAvailableProductsReceived = new AvailableProductsRetriever(availableProducts);
        ProductListRetriever productListRetriever = new ProductListRetriever(billingClient);
        productListRetriever.getListOfProductsAvailable(BillingClient.SkuType.SUBS, productSkus, onAvailableProductsReceived);

        ActivePurchaseRetriever purchaseChecker = new ActivePurchaseRetriever(billingClient, priorPurchasesHandler);
        purchaseChecker.retrievePurchases(BillingClient.SkuType.SUBS);
        purchaseChecker.retrievePurchases(BillingClient.SkuType.INAPP);

        connectionListener.onConnectionAcquired();
    }

    @Override
    public void onBillingServiceDisconnected() {
        connectionListener.onConnectionLost();
    }


}