package delit.piwigoclient.subscription.api;

import androidx.annotation.NonNull;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.zzb;

public class ExistingPurchase {
    private final Purchase purchase;
    private final SkuDetails itemDetail;

    public ExistingPurchase(@NonNull Purchase p, @NonNull SkuDetails itemDetail) {
        this.purchase = p;
        this.itemDetail = itemDetail;
    }

    @NonNull
    public String getType() {
        return itemDetail.getType();
    }

    @NonNull
    public String getPricePaid() {
        return itemDetail.getPrice();
    }

    @NonNull
    public String getTitle() {
        return itemDetail.getTitle();
    }

    @NonNull
    public String getDescription() {
        return itemDetail.getDescription();
    }

    @NonNull
    public String getOrderId() {
        return purchase.getOrderId();
    }

    @NonNull
    @zzb
    public String getSku() {
        return purchase.getSku();
    }

    public long getPurchaseTime() {
        return purchase.getPurchaseTime();
    }

    public int getPurchaseState() {
        return purchase.getPurchaseState();
    }

    public boolean isAutoRenewing() {
        return purchase.isAutoRenewing();
    }

    public boolean isSubscription() {
        return BillingClient.SkuType.SUBS.equals(getType()) ;
    }

    public SkuDetails getSkuDetail() {
        return itemDetail;
    }

    public boolean isPriceChangeConfirmationRequired() {
        return isSubscription() && isAutoRenewing(); /*&& purchase.getPurchaseTime() < itemDetail.????*/
    }
}
