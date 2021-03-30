package delit.piwigoclient.ui.subscription.list;

import com.android.billingclient.api.SkuDetails;

public interface ProductManagementListener {
    void manageProductPurchase(SkuDetails itemDetail);
}
