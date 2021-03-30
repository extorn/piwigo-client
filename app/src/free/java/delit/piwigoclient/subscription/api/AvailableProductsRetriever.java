package delit.piwigoclient.subscription.api;

import com.android.billingclient.api.SkuDetails;

import java.util.List;

public class AvailableProductsRetriever implements AvailableProductsListener {

    private final AvailableProducts availableProducts;

    public AvailableProductsRetriever(AvailableProducts availableProducts) {
        this.availableProducts = availableProducts;
    }

    @Override
    public void withAvailableProducts(List<SkuDetails> productDetails) {
        availableProducts.addAll(productDetails);
    }
}