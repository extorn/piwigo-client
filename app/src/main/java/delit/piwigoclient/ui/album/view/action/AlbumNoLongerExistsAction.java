package delit.piwigoclient.ui.album.view.action;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import delit.libs.core.util.Logging;
import delit.piwigoclient.ui.album.view.AbstractViewAlbumFragment;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;

public class AlbumNoLongerExistsAction<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  extends QuestionResultAdapter<FUIH, F> implements Parcelable {

    public static final Creator<AlbumNoLongerExistsAction<?,?>> CREATOR = new Creator<AlbumNoLongerExistsAction<?,?>>() {
        @Override
        public AlbumNoLongerExistsAction<?,?> createFromParcel(Parcel in) {
            return new AlbumNoLongerExistsAction<>(in);
        }

        @Override
        public AlbumNoLongerExistsAction<?,?>[] newArray(int size) {
            return new AlbumNoLongerExistsAction[size];
        }
    };

    public AlbumNoLongerExistsAction(FUIH uiHelper) {
        super(uiHelper);
    }

    protected AlbumNoLongerExistsAction(Parcel in) {
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
    public void onDismiss(AlertDialog dialog) {
        super.onDismiss(dialog);
        try {
            Logging.log(Log.INFO, AbstractViewAlbumFragment.TAG, "removing from activity on dialog dismiss");
            getUiHelper().getParent().getParentFragmentManager().popBackStack();
        } catch(IllegalStateException e) {
            Logging.log(Log.ERROR, AbstractViewAlbumFragment.TAG, "Unable to pop fragment as it is no longer visible!");
        }
    }
}
