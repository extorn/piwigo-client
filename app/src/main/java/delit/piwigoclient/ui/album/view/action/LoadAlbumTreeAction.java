package delit.piwigoclient.ui.album.view.action;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.fragment.app.FragmentActivity;

import delit.libs.core.util.Logging;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.StaticCategoryItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumsGetFirstAvailableAlbumResponseHandler;
import delit.piwigoclient.ui.album.view.AbstractViewAlbumFragment;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.model.PiwigoAlbumModel;

public class LoadAlbumTreeAction<FUIH extends FragmentUIHelper<FUIH,F>, F extends AbstractViewAlbumFragment<F,FUIH>> extends UIHelper.Action<FUIH, F, AlbumsGetFirstAvailableAlbumResponseHandler.PiwigoGetAlbumTreeResponse> implements Parcelable {

    public static final Creator<LoadAlbumTreeAction<?,?>> CREATOR = new Creator<LoadAlbumTreeAction<?,?>>() {
        @Override
        public LoadAlbumTreeAction<?,?> createFromParcel(Parcel in) {
            return new LoadAlbumTreeAction<>(in);
        }

        @Override
        public LoadAlbumTreeAction<?,?>[] newArray(int size) {
            return new LoadAlbumTreeAction[size];
        }
    };

    public LoadAlbumTreeAction() {
    }

    protected LoadAlbumTreeAction(Parcel in) {
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
    public boolean onSuccess(FUIH uiHelper, AlbumsGetFirstAvailableAlbumResponseHandler.PiwigoGetAlbumTreeResponse response) {
        CategoryItem currentItem = response.getAlbumTreeRoot();

        // cache the retrieved category tree into the model
        if (currentItem != null) {
            FragmentActivity activity = uiHelper.getParent().requireActivity();
            for (Long albumId : response.getAlbumPath()) {
                if (albumId.equals(StaticCategoryItem.ROOT_ALBUM.getId())) {
                    continue;
                }
                PiwigoAlbumModel albumViewModel = getActionParent(uiHelper).obtainActivityViewModel(activity, "" + currentItem.getId(), PiwigoAlbumModel.class);
                albumViewModel.getPiwigoAlbum(currentItem).getValue();
                try {
                    currentItem = currentItem.getChild(albumId);
                } catch (IllegalStateException e) {
                    // thrown if no child albums were set. This should never occur really since it was a success but the root could now theoretically be empty.
                }
                if (currentItem == null) {
                    break; // were unable to load this item.
                }
            }
        } else {
            Logging.log(Log.ERROR, AbstractViewAlbumFragment.TAG, "album tree retrieved, but albumTreeRoot is null");
        }

        // now reopen the model
        uiHelper.getParent().onReopenModelRetrieved(response.getAlbumTreeRoot(), response.getDeepestAlbumOnDesiredPath());
        return true; // to close the progress indicator
    }

    @Override
    public boolean onFailure(FUIH uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
        uiHelper.getParent().onReopenModelRetrieved(StaticCategoryItem.ROOT_ALBUM.toInstance(), StaticCategoryItem.ROOT_ALBUM.toInstance());
        return true; // to close the progress indicator
    }
}
