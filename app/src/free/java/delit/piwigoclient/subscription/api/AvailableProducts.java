package delit.piwigoclient.subscription.api;

import androidx.annotation.NonNull;

import com.android.billingclient.api.SkuDetails;
import com.drew.lang.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AvailableProducts {
    private final HashMap<String,SkuDetails> availableProducts;
    private boolean loaded;

    public AvailableProducts() {
        this.availableProducts = new HashMap<>();
    }

    public void addAll(List<SkuDetails> products) {
        synchronized (this) {
            for(SkuDetails newProduct : products) {
                availableProducts.remove(newProduct.getSku());
                availableProducts.put(newProduct.getSku(), newProduct);
            }
            loaded = true;
            notifyAll();
        }
    }

    public List<SkuDetails> getAvailableProducts() {
        synchronized (this) {
            return new ArrayList<>(availableProducts.values());
        }
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

    public @Nullable SkuDetails getForSku(@NonNull String sku) {
        return availableProducts.get(sku);
    }
}
