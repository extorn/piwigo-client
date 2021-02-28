package delit.piwigoclient.ui.slideshow.item.action;

import android.os.Parcel;
import android.os.Parcelable;

import org.greenrobot.eventbus.EventBus;

import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.handlers.FavoritesAddImageResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.events.FavoritesUpdatedEvent;
import delit.piwigoclient.ui.slideshow.item.SlideshowItemFragment;

public class FavoriteAddAction<F extends SlideshowItemFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH,F>, T extends ResourceItem> extends SlideshowFavoriteAction<F,FUIH,T, FavoritesAddImageResponseHandler.PiwigoAddFavoriteResponse> implements Parcelable {

    public FavoriteAddAction(){}

    protected FavoriteAddAction(Parcel in) {
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<FavoriteAddAction<?,?,?>> CREATOR = new Creator<FavoriteAddAction<?,?,?>>() {
        @Override
        public FavoriteAddAction<?,?,?> createFromParcel(Parcel in) {
            return new FavoriteAddAction<>(in);
        }

        @Override
        public FavoriteAddAction<?,?,?>[] newArray(int size) {
            return new FavoriteAddAction[size];
        }
    };

    @Override
    protected boolean getValueOnSucess() {
        return true;
    }

    @Override
    public boolean onSuccess(FUIH uiHelper, FavoritesAddImageResponseHandler.PiwigoAddFavoriteResponse response) {
        if(EventBus.getDefault().getStickyEvent(FavoritesUpdatedEvent.class) == null) {
            EventBus.getDefault().postSticky(new FavoritesUpdatedEvent());
        }
        return super.onSuccess(uiHelper, response);
    }
}
