package delit.piwigoclient.ui.upload.actions;

import android.os.Parcel;
import android.os.Parcelable;

import delit.piwigoclient.piwigoApi.handlers.AlbumsGetFirstAvailableAlbumResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.upload.AbstractUploadFragment;

public class LoadAlbumTreeAction<F extends AbstractUploadFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends UIHelper.Action<FUIH,F, AlbumsGetFirstAvailableAlbumResponseHandler.PiwigoGetAlbumTreeResponse> implements Parcelable {

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

    @Override
    public boolean onSuccess(FUIH uiHelper, AlbumsGetFirstAvailableAlbumResponseHandler.PiwigoGetAlbumTreeResponse response) {
        F parent = getActionParent(uiHelper);
        if(parent != null) {
            parent.setUploadToAlbum(response.getDeepestAlbumOnDesiredPath().toStub());
        }
        return true; // to close the progress indicator
    }
}
