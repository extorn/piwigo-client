package delit.piwigoclient.ui.upload.actions;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import delit.libs.core.util.Logging;
import delit.libs.util.IOUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.handlers.AlbumDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.upload.ForegroundPiwigoUploadService;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.upload.AbstractUploadFragment;

public class OnDeleteJobQuestionAction<F extends AbstractUploadFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  extends UIHelper.QuestionResultAdapter<FUIH,F> implements Parcelable {

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
        Long currentJobId = fragment.getUploadJobId();
        if (currentJobId == null) {
            Logging.log(Log.WARN, TAG, "User attempted to delete job that was no longer exists");
            return;
        }
        UploadJob job = ForegroundPiwigoUploadService.getActiveForegroundJob(getContext(), currentJobId);
        if (positiveAnswer != null && positiveAnswer && job != null) {
            if (job.getTemporaryUploadAlbum() > 0) {
                AlbumDeleteResponseHandler albumDelHandler = new AlbumDeleteResponseHandler(job.getTemporaryUploadAlbum(), false);
                getUiHelper().addNonBlockingActiveServiceCall(getContext().getString(R.string.alert_deleting_temporary_upload_album), albumDelHandler.invokeAsync(getContext(), job.getConnectionPrefs()), albumDelHandler.getTag());
            }
            IOUtils.deleteAllFilesSharedWithThisApp(getContext());
            ForegroundPiwigoUploadService.removeJob(job);
            ForegroundPiwigoUploadService.deleteStateFromDisk(getContext(), job, true);
            fragment.allowUserUploadConfiguration(null);
        }
    }
}
