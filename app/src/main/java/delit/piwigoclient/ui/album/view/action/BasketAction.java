package delit.piwigoclient.ui.album.view.action;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.appcompat.app.AlertDialog;

import java.util.HashSet;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.Basket;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.handlers.ImageChangeParentAlbumHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageCopyToAlbumResponseHandler;
import delit.piwigoclient.ui.album.view.AbstractViewAlbumFragment;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;

public class BasketAction<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  extends QuestionResultAdapter<FUIH,F> implements Parcelable {

    public static final Creator<BasketAction<?,?>> CREATOR = new Creator<BasketAction<?,?>>() {
        @Override
        public BasketAction<?,?> createFromParcel(Parcel in) {
            return new BasketAction<>(in);
        }

        @Override
        public BasketAction<?,?>[] newArray(int size) {
            return new BasketAction[size];
        }
    };

    public BasketAction(FUIH uiHelper) {
        super(uiHelper);
    }

    protected BasketAction(Parcel in) {
        super(in);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
        F fragment = getUiHelper().getParent();
        Basket basket = fragment.getBasket();
        final int basketAction = basket.getAction();
        final HashSet<ResourceItem> basketContent = basket.getContents();
        final CategoryItem containerDetails = fragment.getGalleryModel().getContainerDetails();

        if (Boolean.TRUE == positiveAnswer) {
            if (basketAction == Basket.ACTION_COPY) {
                HashSet<ResourceItem> itemsToCopy = basketContent;
                CategoryItem copyToAlbum = containerDetails;
                for (ResourceItem itemToCopy : itemsToCopy) {
                    getUiHelper().addActiveServiceCall(R.string.progress_copy_resources, new ImageCopyToAlbumResponseHandler<>(itemToCopy, copyToAlbum));
                }
            } else if (basketAction == Basket.ACTION_CUT) {
                HashSet<ResourceItem> itemsToMove = basketContent;
                CategoryItem moveToAlbum = containerDetails;
                for (ResourceItem itemToMove : itemsToMove) {
                    getUiHelper().addActiveServiceCall(R.string.progress_move_resources, new ImageChangeParentAlbumHandler<>(itemToMove, moveToAlbum));
                }
            }
        }
    }
}
