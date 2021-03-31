package delit.piwigoclient.ui.subscription;

import androidx.lifecycle.ViewModel;

import com.android.billingclient.api.Purchase;

import java.util.ArrayList;
import java.util.List;

import delit.piwigoclient.subscription.piwigo.PiwigoClientSubscriptionManager;

public class PermittedActions extends ViewModel {

    private final List<String> actions = new ArrayList<>();
    private static final String NO_ADVERTS = "NoAds";
    private static final String LARGE_UPLOADS = "LargeUploads";
    private final static String FAVORITES = "Favorites";
    private final static String TAGS = "Tags";
    private final static String ORPHANS = "Orphans";
    private final static String DOWNLOADS = "Downloads";

    public PermittedActions(){}

    public boolean noAdverts() {
        return actions.contains(NO_ADVERTS);
    }

    public boolean hasTags() {
        return actions.contains(TAGS);
    }

    public boolean hasOrphans() {
        return actions.contains(ORPHANS);
    }

    public boolean hasFavorites() {
        return actions.contains(FAVORITES);
    }

    public boolean hasDownloads() {
        return actions.contains(DOWNLOADS);
    }

    public boolean hasLargeUploads() {
        return actions.contains(LARGE_UPLOADS);
    }

    public void populate(List<Purchase> allSubscriptions) {
        actions.clear();
        for(Purchase subscription : allSubscriptions) {
            switch(subscription.getSku()) {
                case PiwigoClientSubscriptionManager.SUB_REMOVE_ADVERTS_W01:
                case PiwigoClientSubscriptionManager.SUB_REMOVE_ADVERTS_W04:
                    actions.add(NO_ADVERTS);
                    break;
                case PiwigoClientSubscriptionManager.SUB_TAG_ORPHAN_FAVS_W01:
                case PiwigoClientSubscriptionManager.SUB_TAG_ORPHAN_FAVS_W04:
                    actions.add(TAGS);
                    actions.add(ORPHANS);
                    actions.add(FAVORITES);
                    break;
                case PiwigoClientSubscriptionManager.SUB_RES_DOWNLOAD_M06:
                    actions.add(DOWNLOADS);
                    break;
                case PiwigoClientSubscriptionManager.SUB_ALLOW_LARGE_UPLOADS_W04:
                case PiwigoClientSubscriptionManager.SUB_ALLOW_LARGE_UPLOADS_M06:
                    actions.add(LARGE_UPLOADS);
                    break;
                default:
            }
        }
    }
}
