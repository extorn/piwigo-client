package delit.piwigoclient.ui.upload.actions;

import android.os.Parcel;
import android.os.Parcelable;

import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetChildAlbumNamesResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.upload.AbstractUploadFragment;

public class OnGetChildAlbumNamesAction<F extends AbstractUploadFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  extends UIHelper.Action<FUIH,F, AlbumGetChildAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse> implements Parcelable {

    public OnGetChildAlbumNamesAction(){}

    protected OnGetChildAlbumNamesAction(Parcel in) {
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

    public static final Creator<OnGetChildAlbumNamesAction<?,?>> CREATOR = new Creator<OnGetChildAlbumNamesAction<?,?>>() {
        @Override
        public OnGetChildAlbumNamesAction<?,?> createFromParcel(Parcel in) {
            return new OnGetChildAlbumNamesAction<>(in);
        }

        @Override
        public OnGetChildAlbumNamesAction<?,?>[] newArray(int size) {
            return new OnGetChildAlbumNamesAction<?,?>[size];
        }
    };

    @Override
    public boolean onSuccess(FUIH uiHelper, AlbumGetChildAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse response) {
        if (response.getAlbumNames().size() > 0) {
            F fragment = uiHelper.getParent();
            CategoryItemStub uploadToAlbum = fragment.getUploadToAlbum();
            if (uploadToAlbum.getId() == response.getAlbumNames().get(0).getId()) {
                uploadToAlbum = response.getAlbumNames().get(0);
                fragment.setUploadToAlbum(uploadToAlbum);

            } else if (uploadToAlbum.isParentRoot()) {
                fragment.setUploadToAlbum(CategoryItemStub.ROOT_GALLERY);
            }
        }
        return super.onSuccess(uiHelper, response);
    }
}
