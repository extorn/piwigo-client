package delit.piwigoclient.ui.album.view;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.appcompat.app.AlertDialog;

import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;

class AddingAlbumPermissionsAction<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends QuestionResultAdapter<FUIH, F> implements Parcelable {

    public static final Creator<AddingAlbumPermissionsAction<?,?>> CREATOR = new Creator<AddingAlbumPermissionsAction<?,?>>() {
        @Override
        public AddingAlbumPermissionsAction<?,?> createFromParcel(Parcel in) {
            return new AddingAlbumPermissionsAction<>(in);
        }

        @Override
        public AddingAlbumPermissionsAction<?,?>[] newArray(int size) {
            return new AddingAlbumPermissionsAction[size];
        }
    };

    AddingAlbumPermissionsAction(FUIH uiHelper) {
        super(uiHelper);
    }

    protected AddingAlbumPermissionsAction(Parcel in) {
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
        if (Boolean.TRUE == positiveAnswer) {
            F fragment = getUiHelper().getParent();
            fragment.addingAlbumPermissions();
        }
    }
}
