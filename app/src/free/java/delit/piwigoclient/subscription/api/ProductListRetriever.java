package delit.piwigoclient.subscription.api;

import android.util.Log;

import androidx.annotation.NonNull;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.SkuDetailsParams;

import java.util.List;

import delit.libs.core.util.Logging;

public class ProductListRetriever {
    private static final String TAG = "ProductListRetriever";
    private final BillingClient client;

        public ProductListRetriever(@NonNull BillingClient client) {
            this.client = client;
        }

    /**
     *
     * @param productType one of {@link BillingClient.SkuType#SUBS} OR {@link BillingClient.SkuType#INAPP}
     * @param productSkus
     * @param callbackOnReceived
     */
        public void getListOfProductsAvailable(@NonNull String productType, @NonNull List<String> productSkus, @NonNull AvailableProductsListener callbackOnReceived) {

            SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
            params.setSkusList(productSkus).setType(productType);
            client.querySkuDetailsAsync(params.build(),
                    (billingResult, skuDetailsList) -> {
                        Logging.log(Log.INFO, TAG, "Products list received");
                                callbackOnReceived.withAvailableProducts(skuDetailsList);
                    });
        }
    }