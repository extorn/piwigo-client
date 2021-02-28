package delit.piwigoclient.ui.slideshow.item.action;

import android.util.Log;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;

import delit.libs.core.util.Logging;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.handlers.FavoritesAddImageResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.FavoritesRemoveImageResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.slideshow.item.SlideshowItemFragment;

public class FavoriteCheckedListener<F extends SlideshowItemFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH,F>, T extends ResourceItem> implements CompoundButton.OnCheckedChangeListener {

    private static final String TAG = "FavoriteCheckedListener";
    private final @NonNull
    UIHelper<FUIH,F> helper;
    private final @NonNull
    ResourceItem item;

    public FavoriteCheckedListener(@NonNull FUIH helper, @NonNull T item) {
        this.helper = helper;
        this.item = item;
        if (item == null) { // has still happened at runtime.
            Logging.log(Log.ERROR, TAG, "Model item is null");
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if ("noListener".equals(buttonView.getTag())) {
            buttonView.setTag(null);
            return;
        }
        if (!buttonView.isEnabled()) {
            Logging.log(Log.ERROR, TAG, "tag/untag favorite image called but ignored as in progress");
            return; // should never occur
        }
        buttonView.setEnabled(false);
        if (item.hasFavoriteInfo()) {
            if (!item.isFavorite()) {
                helper.invokeActiveServiceCall(R.string.adding_favorite, new FavoritesAddImageResponseHandler(item), new FavoriteAddAction<>());
            } else {
                helper.invokeActiveServiceCall(R.string.removing_favorite, new FavoritesRemoveImageResponseHandler(item), new FavoriteRemoveAction<>());
            }
        }
    }
}
