package delit.piwigoclient.subscription.api;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.Purchase;

import java.util.Collection;
import java.util.List;

public interface PurchaseHandler {
    /**
     *
     * @param purchaseType Either {@link BillingClient.SkuType#SUBS} or {@link BillingClient.SkuType#INAPP}
     * @param purchases list of purchases to add
     */
    void handlePurchases(String purchaseType, Collection<Purchase> purchases);

    List<Purchase> getAllProductPurchases();

    List<Purchase> getAllSubscriptions();

    void handlePurchases(List<Purchase> purchases);

    /**
     * @param purchaseType Either {@link BillingClient.SkuType#SUBS} or {@link BillingClient.SkuType#INAPP}
     */
    void clearAll(String purchaseType);
}