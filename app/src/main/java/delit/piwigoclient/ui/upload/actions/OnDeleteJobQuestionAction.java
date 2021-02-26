package delit.piwigoclient.ui.upload.actions;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.appcompat.app.AlertDialog;

import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;
import delit.piwigoclient.ui.upload.AbstractUploadFragment;

public class OnDeleteJobQuestionAction<F extends AbstractUploadFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  extends QuestionResultAdapter<FUIH,F> implements Parcelable {

    private static final String TAG = "OnDeleteJobQAction";

    public OnDeleteJobQuestionAction(FUIH uiHelper) {
        super(uiHelper);
    }

    protected OnDeleteJobQuestionAction(Parcel in) {
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

    public static final Creator<OnDeleteJobQuestionAction<?,?>> CREATOR = new Creator<OnDeleteJobQuestionAction<?,?>>() {
        @Override
        public OnDeleteJobQuestionAction<?,?> createFromParcel(Parcel in) {
            return new OnDeleteJobQuestionAction<>(in);
        }

        @Override
        public OnDeleteJobQuestionAction<?,?>[] newArray(int size) {
            return new OnDeleteJobQuestionAction[size];
        }
    };

    @Override
    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
        F fragment = getUiHelper().getParent();
        if (Boolean.TRUE.equals(positiveAnswer)) {
            fragment.onUserActionDeleteUploadJob();
        }
    }
}
