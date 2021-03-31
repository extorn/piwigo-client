package delit.piwigoclient.ui.orphans.action;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import delit.libs.core.util.Logging;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;
import delit.piwigoclient.ui.orphans.ViewOrphansFragment;

public class CreateOrphansAlbumQuestionAction<F extends ViewOrphansFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  extends QuestionResultAdapter<FUIH,F> implements Parcelable {

    public static final Creator<CreateOrphansAlbumQuestionAction<?,?>> CREATOR = new Creator<CreateOrphansAlbumQuestionAction<?,?>>() {
        @Override
        public CreateOrphansAlbumQuestionAction<?,?> createFromParcel(Parcel in) {
            return new CreateOrphansAlbumQuestionAction<>(in);
        }

        @Override
        public CreateOrphansAlbumQuestionAction<?,?>[] newArray(int size) {
            return new CreateOrphansAlbumQuestionAction[size];
        }
    };
    private static final String TAG = "CreateRescuedOrphansAlbumAction";
    private final int orphanCount;

    public CreateOrphansAlbumQuestionAction(FUIH uiHelper, int orphanCount) {
        super(uiHelper);
        this.orphanCount = orphanCount;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(orphanCount);
    }


    protected CreateOrphansAlbumQuestionAction(Parcel in) {
        super(in);
        orphanCount = in.readInt();
    }

    @Override
    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
        if(positiveAnswer) {
            getParent().userActionCreateRescuedOrphansAlbum();
        } else {
            // immediately leave this screen.
            Logging.log(Log.INFO, TAG, "Permission not granted to manage orphans.");
            getParent().showEmptyAlbumText(orphanCount);
        }
    }
}
