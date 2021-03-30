package delit.piwigoclient.subscription.api;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PriceChangeConfirmationListener;
import com.android.billingclient.api.PriceChangeFlowParams;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;

import java.util.List;

import delit.libs.core.util.Logging;

public abstract class SubscriptionManager implements ConnectionListener {

    private static final String TAG = "SubscriptionManager";
    private BillingClient billingClient;
    private final AvailableProducts availableProducts;
    private final ExistingPurchases existingPurchases;
    private boolean isConnected;
    private Runnable onConnectedTask;

    public SubscriptionManager(PurchaseListUpdateListener purchaseListUpdateListener) {
        availableProducts = new AvailableProducts();
        existingPurchases = new ExistingPurchases(purchaseListUpdateListener);
        purchaseListUpdateListener.withSubscriptionManager(this);
    }


    public AvailableProducts getAvailableProducts() {
        return availableProducts;
    }

    public ExistingPurchases getExistingPurchases() {
        return existingPurchases;
    }

    private BillingClient getBillingClient(@NonNull Context context) {
        if(billingClient == null) {

            PurchasesUpdatedListener purchasesUpdatedListener = new MyPurchasesUpdatedListener(existingPurchases);

            billingClient = BillingClient.newBuilder(context)
                    .setListener(purchasesUpdatedListener)
                    .enablePendingPurchases()
                    .build();
        }
        return billingClient;
    }

    public void checkForPriceChanges(@NonNull Activity currentActivity, @NonNull PurchaseDetail purchases) {
        //Enable this when we can be sure a change has occurred
        /*for(ExistingPurchase p : purchases.getList()) {
            if(p.isPriceChangeConfirmationRequired()) {
                launchPriceChangeFlow(currentActivity, p.getSkuDetail(), new MyPriceChangeConfirmationListener());
            }
        }*/
    }

    public synchronized void obtainConnection(@NonNull Context context) {
        List<String> skuList = getAvailableProductSkus();
        billingClient = getBillingClient(context);
        billingClient.startConnection(new MyBillingClientInitialiser(billingClient, skuList, availableProducts, existingPurchases, this));
    }

    public void closeConnection() {
        billingClient.endConnection();
        billingClient = null;
        synchronized (this) {
            isConnected = false;
        }
    }

    @Override
    public synchronized void onConnectionAcquired() {
        isConnected = true;
        runTaskOnConnection();
    }

    private void runTaskOnConnection() {
        if(onConnectedTask != null) {
            Runnable task = onConnectedTask;
            onConnectedTask = null;
            task.run();
        }
    }

    @Override
    public synchronized void onConnectionLost() {
        isConnected = false;
    }

    public synchronized void launchBillingFlow(@NonNull Activity currentActivity, SkuDetails skuDetails) {
        if(!isConnected) {
            onConnectedTask = ()->launchBillingFlow(currentActivity, skuDetails);
            obtainConnection(currentActivity);
            return;
        }

        BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                .setSkuDetails(skuDetails)
                .build();
        int responseCode = billingClient.launchBillingFlow(currentActivity, billingFlowParams).getResponseCode();

        if(responseCode != BillingClient.BillingResponseCode.OK) {
            Logging.log(Log.WARN, TAG, "BillingClient returned error code %1$d", responseCode);
        }
    }

    public synchronized void launchPriceChangeFlow(@NonNull Activity currentActivity, SkuDetails skuDetails, PriceChangeConfirmationListener listener) {
        if(!isConnected) {
            onConnectedTask = ()->launchPriceChangeFlow(currentActivity, skuDetails, listener);
            obtainConnection(currentActivity);
            return;
        }

        PriceChangeFlowParams params = PriceChangeFlowParams.newBuilder().setSkuDetails(skuDetails).build();
        billingClient.launchPriceChangeConfirmationFlow(currentActivity, params, listener);
    }

    protected abstract List<String> getAvailableProductSkus();

    public synchronized void getProductPurchases(@NonNull Context context) {
        if(!isConnected) {
            onConnectedTask = ()->getProductPurchases(context);
            obtainConnection(context);
            return;
        }
        ActivePurchaseRetriever purchaseRetriever = new ActivePurchaseRetriever(billingClient, existingPurchases);
        purchaseRetriever.retrievePurchases();
    }

    public void acknowledgePurchase(Purchase purchase, AckListener.Listener listener) {
        AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();
        billingClient.acknowledgePurchase(params, new AckListener(listener));
    }

    public PurchaseDetail getPurchasedProductDetail() {
        return new PurchaseDetail().fill(getAvailableProducts(), getExistingPurchases());
    }

    private static class AckListener implements AcknowledgePurchaseResponseListener {

        public AckListener(Listener listener) {
            this.listener = listener;
        }

        public interface Listener {
            void onComplete(boolean success);
        }

        private final Listener listener;

        @Override
        public void onAcknowledgePurchaseResponse(@NonNull BillingResult billingResult) {
            switch (billingResult.getResponseCode()) {
                case BillingClient.BillingResponseCode.OK:
                    listener.onComplete(true);
                    break;
                default:
                    Logging.log(Log.WARN, TAG, "Product payment acknowledgement failed. Error %1$d : %2$s", billingResult.getResponseCode(), billingResult.getDebugMessage());
                    listener.onComplete(false);
            }
        }
    }
}
