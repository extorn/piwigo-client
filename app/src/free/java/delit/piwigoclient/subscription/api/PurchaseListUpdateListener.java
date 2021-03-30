package delit.piwigoclient.subscription.api;

import androidx.annotation.NonNull;

import com.android.billingclient.api.Purchase;

import java.util.List;

public interface PurchaseListUpdateListener {
    void onProductPurchaseAdded(@NonNull Purchase purchase);
    void onSubscriptionPurchaseAdded(@NonNull Purchase purchase);

    void onNotificationOfExternalPurchases(@NonNull List<Purchase> purchases);

    void onProductPurchaseRemoved(@NonNull Purchase purchase);

    void onSubscriptionRemoved(@NonNull Purchase purchase);

    void withSubscriptionManager(SubscriptionManager subscriptionManager);
}
