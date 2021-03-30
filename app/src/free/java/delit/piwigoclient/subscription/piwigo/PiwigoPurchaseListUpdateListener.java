package delit.piwigoclient.subscription.piwigo;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.billingclient.api.Purchase;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.ExecutorManager;
import delit.piwigoclient.subscription.api.PurchaseListUpdateListener;
import delit.piwigoclient.subscription.api.SubscriptionManager;
import delit.piwigoclient.ui.AdsManager;

public class PiwigoPurchaseListUpdateListener implements PurchaseListUpdateListener {
    private static final String TAG = "PiwigoPurchaseListUpdateListener";
    private final PiwigoClientPurchaseVerifier purchaseVerifier;
    private ExecutorManager executorManager;
    private SubscriptionManager purchaseManager;
    private final Context context;

    public PiwigoPurchaseListUpdateListener(@NonNull Context context, @NonNull PiwigoClientPurchaseVerifier purchaseVerifier) {
        this.context = context;
        this.purchaseVerifier = purchaseVerifier;
        executorManager = new ExecutorManager(1,1,1000, 3);
        executorManager.blockIfBusy(true);
    }

    @Override
    public void withSubscriptionManager(SubscriptionManager subscriptionManager) {
        this.purchaseManager = subscriptionManager;
    }

    @Override
    public void onSubscriptionPurchaseAdded(@NonNull Purchase purchase) {

        switch(purchase.getPurchaseState()) {
            case Purchase.PurchaseState.PURCHASED:
                executorManager.submit(()->handleProductPurchaseComplete(purchase));
                break;
            case Purchase.PurchaseState.PENDING:
                executorManager.submit(()->handleProductPurchasePending(purchase));
                break;
        }
    }

    private void handleProductPurchasePending(Purchase purchase) {
        Logging.log(Log.INFO, TAG, "Notified of Pending payment for purchase : %1$s", purchase.getSku());
    }

    private void handleProductPurchaseComplete(Purchase purchase) {
        if(isPurchaseAuthentic(purchase)) {
            //TODO is it possible for it to be inauthentic at this point?
            purchaseVerifier.recordPurchase(purchase);
            switch(purchase.getSku()) {
                case PiwigoClientSubscriptionManager.SUB_REMOVE_ADVERTS_W01:
                case PiwigoClientSubscriptionManager.SUB_REMOVE_ADVERTS_W04:
                    long expires = PiwigoClientSubscriptionManager.getProductExpiry(purchase);
                    AdsManager.getInstance(context).setAdvertsDisabled(System.currentTimeMillis() < expires);
                    break;
                default:
                    Logging.log(Log.ERROR, TAG, "Unrecognised purchase");
            }
        } else {
            Logging.log(Log.ERROR, TAG, "Purchase could not be verified as authentic");
        }
    }

    private void handleProductPurchaseRemoved(Purchase purchase) {
        switch(purchase.getSku()) {
            case PiwigoClientSubscriptionManager.SUB_REMOVE_ADVERTS_W01:
            case PiwigoClientSubscriptionManager.SUB_REMOVE_ADVERTS_W04:
                AdsManager.getInstance(context).setAdvertsDisabled(false);
                break;
            default:
                Logging.log(Log.ERROR, TAG, "Unrecognised purchase");
        }
    }

    @Override
    public void onNotificationOfExternalPurchases(@NonNull List<Purchase> purchases) {
        // acknowledge the purchase
        CountDownLatch countDownLatch = new CountDownLatch(purchases.size());
        ExecutorManager executorManager = new ExecutorManager(2,2,1000,purchases.size());
        executorManager.blockIfBusy(true);
        for(Purchase p : purchases) {
            if(!p.isAcknowledged()) {
                executorManager.submit(() -> purchaseManager.acknowledgePurchase(p, (success)-> countDownLatch.countDown()));
            }
        }
        executorManager.submit(()->{
            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
                Logging.log(Log.ERROR, TAG, "Interrupted while waiting for purchases to be acknowledged");
            }
        });
    }

    @Override
    public void onProductPurchaseRemoved(@NonNull Purchase purchase) {
        throw new IllegalStateException("No product purchases are currently accepted");
    }

    @Override
    public void onSubscriptionRemoved(@NonNull Purchase purchase) {
        executorManager.submit(()->handleProductPurchaseRemoved(purchase));
    }

    private boolean isPurchaseAuthentic(@NonNull Purchase purchase) {
        return purchaseVerifier.verifyPurchase(purchase);
    }

    @Override
        public void onProductPurchaseAdded(@NonNull Purchase purchase) {
            throw new IllegalStateException("No product purchases are currently accepted");
        }
    }