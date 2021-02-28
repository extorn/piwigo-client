package delit.piwigoclient.ui.album.view.action;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.appcompat.app.AlertDialog;

import delit.libs.ui.util.ParcelUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.piwigoApi.handlers.AlbumDeleteResponseHandler;
import delit.piwigoclient.ui.album.view.AbstractViewAlbumFragment;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;

public class DeleteAlbumAction<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  extends QuestionResultAdapter<FUIH,F> implements Parcelable {

    public static final Creator<DeleteAlbumAction<?,?>> CREATOR = new Creator<DeleteAlbumAction<?,?>>() {
        @Override
        public DeleteAlbumAction<?,?> createFromParcel(Parcel in) {
            return new DeleteAlbumAction<>(in);
        }

        @Override
        public DeleteAlbumAction<?,?>[] newArray(int size) {
            return new DeleteAlbumAction[size];
        }
    };
    private final CategoryItem album;
    private final boolean deleteOrphans;

    public DeleteAlbumAction(FUIH uiHelper, CategoryItem album, boolean deleteOrphans) {
        super(uiHelper);
        this.album = album;
        this.deleteOrphans = deleteOrphans;
    }

    protected DeleteAlbumAction(Parcel in) {
        super(in);
        album = in.readParcelable(CategoryItem.class.getClassLoader());
        deleteOrphans = ParcelUtils.readBool(in);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeParcelable(album, flags);
        ParcelUtils.writeBool(dest, deleteOrphans);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
        if (Boolean.TRUE == positiveAnswer) {
            getUiHelper().addActiveServiceCall(R.string.progress_delete_album, new AlbumDeleteResponseHandler(album.getId(), deleteOrphans));
        }
    }

}
