package delit.piwigoclient.ui.album.view.action;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.appcompat.app.AlertDialog;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.ui.album.view.AbstractViewAlbumFragment;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;

public class DeleteWithOrphansAlbumAction<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  extends QuestionResultAdapter<FUIH,F> implements Parcelable {
    public static final Creator<DeleteWithOrphansAlbumAction<?,?>> CREATOR = new Creator<DeleteWithOrphansAlbumAction<?,?>>() {
        @Override
        public DeleteWithOrphansAlbumAction<?,?> createFromParcel(Parcel in) {
            return new DeleteWithOrphansAlbumAction<>(in);
        }

        @Override
        public DeleteWithOrphansAlbumAction<?,?>[] newArray(int size) {
            return new DeleteWithOrphansAlbumAction[size];
        }
    };
    private final CategoryItem album;

    public DeleteWithOrphansAlbumAction(FUIH uiHelper, CategoryItem album) {
        super(uiHelper);
        this.album = album;
    }

    protected DeleteWithOrphansAlbumAction(Parcel in) {
        super(in);
        album = in.readParcelable(CategoryItem.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeParcelable(album, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
        String msg = getContext().getString(R.string.alert_confirm_really_delete_album_from_server_pattern, album.getName());
        getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, R.string.button_no, R.string.button_yes, new DeleteAlbumAction<>(getUiHelper(), album, positiveAnswer));
    }
}
