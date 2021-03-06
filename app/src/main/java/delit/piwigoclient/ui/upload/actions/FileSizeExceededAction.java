package delit.piwigoclient.ui.upload.actions;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.appcompat.app.AlertDialog;

import java.util.Set;

import delit.libs.ui.util.ParcelUtils;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;
import delit.piwigoclient.ui.upload.AbstractUploadFragment;
import delit.piwigoclient.ui.upload.list.UploadDataItem;

public class FileSizeExceededAction<F extends AbstractUploadFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  extends QuestionResultAdapter<FUIH,F> implements Parcelable {

    private final Set<UploadDataItem> filesToDelete;

    public FileSizeExceededAction(FUIH uiHelper, Set<UploadDataItem> filesForReview) {
        super(uiHelper);
        this.filesToDelete = filesForReview;
    }

    protected FileSizeExceededAction(Parcel in) {
        super(in);
        filesToDelete = ParcelUtils.readHashSet(in, Uri.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        ParcelUtils.writeSet(dest, filesToDelete);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<FileSizeExceededAction<?,?>> CREATOR = new Creator<FileSizeExceededAction<?,?>>() {
        @Override
        public FileSizeExceededAction<?,?> createFromParcel(Parcel in) {
            return new FileSizeExceededAction<>(in);
        }

        @Override
        public FileSizeExceededAction<?,?>[] newArray(int size) {
            return new FileSizeExceededAction[size];
        }
    };

    @Override
    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
        F fragment = getUiHelper().getParent();

        if (Boolean.TRUE == positiveAnswer) {
            for (UploadDataItem file : filesToDelete) {
                fragment.onUserActionDeleteFileFromUpload(fragment.getFilesForUploadViewAdapter(), file.getUri(), false);
            }
        }
        if (positiveAnswer != null) {
            fragment.buildAndSubmitNewUploadJob(true);
        }
    }
}
