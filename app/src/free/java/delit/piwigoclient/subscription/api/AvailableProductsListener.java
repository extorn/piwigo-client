package delit.piwigoclient.subscription.api;

import com.android.billingclient.api.SkuDetails;

import java.util.List;

public interface AvailableProductsListener {
    void withAvailableProducts(List<SkuDetails> productDetails);
}