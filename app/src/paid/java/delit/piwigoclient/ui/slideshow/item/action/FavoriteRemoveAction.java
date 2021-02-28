package delit.piwigoclient.ui.slideshow.item.action;

import android.os.Parcel;
import android.os.Parcelable;

import org.greenrobot.eventbus.EventBus;

import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.handlers.FavoritesRemoveImageResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.events.FavoritesUpdatedEvent;
import delit.piwigoclient.ui.slideshow.item.SlideshowItemFragment;

public class FavoriteRemoveAction<F extends SlideshowItemFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH,F>, T extends ResourceItem> extends SlideshowFavoriteAction<F,FUIH,T, FavoritesRemoveImageResponseHandler.PiwigoRemoveFavoriteResponse> implements Parcelable {

    public FavoriteRemoveAction(){}

    protected FavoriteRemoveAction(Parcel in) {
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<FavoriteRemoveAction<?,?,?>> CREATOR = new Creator<FavoriteRemoveAction<?,?,?>>() {
        @Override
        public FavoriteRemoveAction<?,?,?> createFromParcel(Parcel in) {
            return new FavoriteRemoveAction<>(in);
        }

        @Override
        public FavoriteRemoveAction<?,?,?>[] newArray(int size) {
            return new FavoriteRemoveAction[size];
        }
    };

    @Override
    protected boolean getValueOnSucess() {
        return false;
    }

    @Override
    public boolean onSuccess(FUIH uiHelper, FavoritesRemoveImageResponseHandler.PiwigoRemoveFavoriteResponse response) {
        if(EventBus.getDefault().getStickyEvent(FavoritesUpdatedEvent.class) == null) {
            EventBus.getDefault().postSticky(new FavoritesUpdatedEvent());
        }
        return super.onSuccess(uiHelper, response);
    }
}
