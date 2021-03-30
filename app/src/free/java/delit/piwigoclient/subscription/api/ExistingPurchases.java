package delit.piwigoclient.subscription.api;

import androidx.annotation.NonNull;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.Purchase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class ExistingPurchases implements PurchaseHandler {

    private final List<Purchase> allProductPurchases;
    private final List<Purchase> allSubscriptionPurchases;
    private final PurchaseListUpdateListener purchaseListUpdateListener;
    private boolean loaded;

    public ExistingPurchases(PurchaseListUpdateListener purchaseListUpdateListener) {
        allProductPurchases = new ArrayList<>();
        allSubscriptionPurchases = new ArrayList<>();
        this.purchaseListUpdateListener = purchaseListUpdateListener;
    }

    /**
     * @param purchaseType Either {@link BillingClient.SkuType#SUBS} or {@link BillingClient.SkuType#INAPP}
     */
    @Override
    public void clearAll(String purchaseType) {
        switch(purchaseType) {
            case BillingClient.SkuType.INAPP:
                synchronized (this) {
                    for (Iterator<Purchase> iterator = allProductPurchases.iterator(); iterator.hasNext(); ) {
                        Purchase purchase = iterator.next();
                        iterator.remove();
                        purchaseListUpdateListener.onProductPurchaseRemoved(purchase);
                    }
                }
                break;
            case BillingClient.SkuType.SUBS:
                synchronized (this) {
                    for (Iterator<Purchase> iterator = allSubscriptionPurchases.iterator(); iterator.hasNext(); ) {
                        Purchase purchase = iterator.next();
                        iterator.remove();
                        purchaseListUpdateListener.onSubscriptionRemoved(purchase);
                    }
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported purchase type : " + purchaseType);

        }
    }

    @Override
    public void handlePurchases(@NonNull String purchaseType, @NonNull Collection<Purchase> purchases) {
        for(Purchase purchase : purchases) {
            add(purchaseType, purchase);
        }
        synchronized (this) {
            loaded = true;
            notifyAll();
        }
    }

    @Override
    public List<Purchase> getAllProductPurchases() {
        synchronized (this) {
            return new ArrayList<>(allProductPurchases);
        }
    }

    @Override
    public List<Purchase> getAllSubscriptions() {
        synchronized (this) {
            return new ArrayList<>(allSubscriptionPurchases);
        }
    }

    @Override
    public void handlePurchases(List<Purchase> purchases) {
        //NOTE: these are NOT verified. Theoretically, they could be spoofed. Check the current list at google
        purchaseListUpdateListener.onNotificationOfExternalPurchases(purchases);
    }

    private void add(String purchaseType, Purchase purchase) {
        switch(purchaseType) {
            case BillingClient.SkuType.INAPP:
                synchronized (this) {
                    allProductPurchases.add(purchase);
                }
                purchaseListUpdateListener.onProductPurchaseAdded(purchase);
                break;
            case BillingClient.SkuType.SUBS:
                synchronized (this) {
                    allSubscriptionPurchases.add(purchase);
                }
                purchaseListUpdateListener.onSubscriptionPurchaseAdded(purchase);
                break;
            default:
                throw new IllegalArgumentException("Unsupported purchase type : " + purchaseType);

        }
    }

    public List<Purchase>  getAll() {
        List<Purchase> all = new ArrayList<>();
        all.addAll(getAllSubscriptions());
        all.addAll(getAllProductPurchases());
        return all;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public void waitForLoad(int millis) {
        synchronized (this) {
            try {
                wait(millis);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
