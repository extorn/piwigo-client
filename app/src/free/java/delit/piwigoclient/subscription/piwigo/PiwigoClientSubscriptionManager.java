package delit.piwigoclient.subscription.piwigo;

import android.content.Context;

import androidx.annotation.NonNull;

import com.android.billingclient.api.Purchase;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import delit.piwigoclient.subscription.api.SubscriptionManager;

public class PiwigoClientSubscriptionManager extends SubscriptionManager {

    public static final String SUB_REMOVE_ADVERTS_W01 = "piwigoclient.adverts.remove.w01";
    public static final String SUB_REMOVE_ADVERTS_W04 = "piwigoclient.adverts.remove.w04";
    public static final String SUB_ALLOW_LARGE_UPLOADS_W04 = "piwigoclient.allow_large_uploads.w04";
    public static final String SUB_ALLOW_LARGE_UPLOADS_M06 = "piwigoclient.allow_large_uploads.m06";
    public static final String SUB_TAG_ORPHAN_FAVS_W01 = "piwigoclient.tags_orphans_favs.w01";
    public static final String SUB_TAG_ORPHAN_FAVS_W04 = "piwigoclient.tags_orphans_favs.w04";
    public static final String SUB_RES_DOWNLOAD_M06 = "piwigoclient.allow_downloads.m06";

    public PiwigoClientSubscriptionManager(@NonNull Context context) {
        super(new PiwigoPurchaseListUpdateListener(context, new PiwigoClientPurchaseVerifier()));
    }

    /**
     * @param purchase item purchased
     * @return Long.MAX_VALUE if never expires.
     */
    public static long getProductExpiry(Purchase purchase) {
        Matcher m = Pattern.compile(".*\\.([hwd])(\\d*)$").matcher(purchase.getSku());
        if(!m.matches()) {
            return Long.MAX_VALUE;
        }
        String unit = Objects.requireNonNull(m.group(1));
        int val = Integer.parseInt(Objects.requireNonNull(m.group(2)));
        int hours;
        switch (unit) {
            case "m":
                hours = val * 31 * 24; // presume every month has 31 days (lazy but safe)
            break;
            case "w":
                hours = val * 7 * 24;
            break;
            case "d":
                hours = val * 24;
            break;
            case "h":
                hours = val;
            break;
            default:
                throw new IllegalStateException("Unexpected unit " + unit);
        }
        return purchase.getPurchaseTime() + hours * 60 * 60 * 1000;
    }

    @Override
    protected List<String> getAvailableProductSkus() {
        List<String> skuList = new ArrayList<>();
        skuList.add(SUB_REMOVE_ADVERTS_W01);
        skuList.add(SUB_REMOVE_ADVERTS_W04);
        skuList.add(SUB_ALLOW_LARGE_UPLOADS_W04);
        skuList.add(SUB_ALLOW_LARGE_UPLOADS_M06);
        skuList.add(SUB_TAG_ORPHAN_FAVS_W01);
        skuList.add(SUB_TAG_ORPHAN_FAVS_W04);
        skuList.add(SUB_RES_DOWNLOAD_M06);
        return skuList;
    }


}
