package delit.piwigoclient.subscription.api;

import android.util.Log;

import androidx.annotation.NonNull;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PriceChangeConfirmationListener;

import delit.libs.core.util.Logging;

public class MyPriceChangeConfirmationListener implements PriceChangeConfirmationListener {

    private static final String TAG = "MyPurchasesUpdatedListener";

    public MyPriceChangeConfirmationListener() {
    }

    @Override
    public void onPriceChangeConfirmationResult(@NonNull BillingResult billingResult) {
        switch (billingResult.getResponseCode()) {
            case BillingClient.BillingResponseCode.DEVELOPER_ERROR:
                Logging.log(Log.ERROR, TAG, "PriceChangeConfirm failed with developer error. Error %1$d : %2$s", billingResult.getResponseCode(), billingResult.getDebugMessage());
                break;
            case BillingClient.BillingResponseCode.OK:
                //TODO  Show user a message
                break;
            case BillingClient.BillingResponseCode.USER_CANCELED:
                //TODO  Show user a message
                break;
            default:
                Logging.log(Log.WARN, TAG, "PurchaseUpdate failed. Error %1$d : %2$s", billingResult.getResponseCode(), billingResult.getDebugMessage());
                //TODO Handle any other error codes.
        }
    }
}