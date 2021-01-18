package delit.piwigoclient.picasso;

import com.squareup.picasso.Picasso;

public interface EnhancedPicassoListener extends Picasso.Listener {
    boolean isLikelyStillNeeded();

    String getListenerPurpose();
}
