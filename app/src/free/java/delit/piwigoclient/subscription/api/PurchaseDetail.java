package delit.piwigoclient.subscription.api;

import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.SkuDetails;

import java.util.ArrayList;
import java.util.List;

public class PurchaseDetail {
    private List<ExistingPurchase> purchases;

    public PurchaseDetail fill(AvailableProducts products, ExistingPurchases purchasedItems) {
        List<Purchase> purchasedItemList = purchasedItems.getAll();
        purchases = new ArrayList<>(purchasedItemList.size());
        for(Purchase p : purchasedItemList) {
            SkuDetails itemDetail = products.getForSku(p.getSku());
            purchases.add(new ExistingPurchase(p, itemDetail));
        }
        return this;
    }

    public List<ExistingPurchase> getList() {
        return purchases;
    }

    public boolean isEmpty() {
        return purchases.isEmpty();
    }
}
