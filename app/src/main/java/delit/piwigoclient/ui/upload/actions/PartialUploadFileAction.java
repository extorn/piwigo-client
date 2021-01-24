package delit.piwigoclient.ui.upload.actions;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import delit.libs.core.util.Logging;
import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.upload.AbstractUploadFragment;

public class PartialUploadFileAction<F extends AbstractUploadFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  extends UIHelper.QuestionResultAdapter<FUIH,F> implements Parcelable {
    private static final String TAG = "PartialUploadFileAction";
    private final Uri itemToRemove;

    public PartialUploadFileAction(FUIH uiHelper, Uri itemToRemove) {
        super(uiHelper);
        this.itemToRemove = itemToRemove;
    }

    protected PartialUploadFileAction(Parcel in) {
        super(in);
        itemToRemove = in.readParcelable(Uri.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeParcelable(itemToRemove, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<PartialUploadFileAction<?,?>> CREATOR = new Creator<PartialUploadFileAction<?,?>>() {
        @Override
        public PartialUploadFileAction<?,?> createFromParcel(Parcel in) {
            return new PartialUploadFileAction<>(in);
        }

        @Override
        public PartialUploadFileAction<?,?>[] newArray(int size) {
            return new PartialUploadFileAction[size];
        }
    };

    @Override
    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
        if (Boolean.TRUE == positiveAnswer) {
            F fragment = getUiHelper().getParent();
            UploadJob activeJob = fragment.getActiveJob(getContext());
            if (activeJob != null) {
                activeJob.cancelFileUpload(itemToRemove);
                fragment.getFilesForUploadViewAdapter().remove(itemToRemove);
                int countFilesNeedingServerAction = activeJob.getFilesAwaitingUpload().size();
                if (countFilesNeedingServerAction == 0) {
                    // no files left to upload. Lets switch the button from upload to finish
                    fragment.getUploadFilesNowButton().setText(R.string.upload_files_finish_job_button_title);
                }
            } else {
                Logging.log(Log.ERROR, TAG, "Attempt to alter upload job but it was null");
            }
        }
    }
}
