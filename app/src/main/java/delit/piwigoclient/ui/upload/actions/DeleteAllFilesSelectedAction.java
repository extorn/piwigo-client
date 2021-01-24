package delit.piwigoclient.ui.upload.actions;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.appcompat.app.AlertDialog;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.upload.AbstractUploadFragment;

public class DeleteAllFilesSelectedAction<F extends AbstractUploadFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  extends UIHelper.QuestionResultAdapter<FUIH,F> implements Parcelable {

    public DeleteAllFilesSelectedAction(FUIH uiHelper) {
        super(uiHelper);
    }

    protected DeleteAllFilesSelectedAction(Parcel in) {
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

    public static final Creator<DeleteAllFilesSelectedAction<?,?>> CREATOR = new Creator<DeleteAllFilesSelectedAction<?,?>>() {
        @Override
        public DeleteAllFilesSelectedAction<?,?> createFromParcel(Parcel in) {
            return new DeleteAllFilesSelectedAction<>(in);
        }

        @Override
        public DeleteAllFilesSelectedAction<?,?>[] newArray(int size) {
            return new DeleteAllFilesSelectedAction[size];
        }
    };

    @Override
    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
        if (Boolean.TRUE == positiveAnswer) {
            getParent().showOverallUploadProgressIndicator(R.string.removing_files_from_job, 0);
            F fragment = getUiHelper().getParent();
            fragment.removeAllFilesFromUploadImmediately();
        }
    }
}
