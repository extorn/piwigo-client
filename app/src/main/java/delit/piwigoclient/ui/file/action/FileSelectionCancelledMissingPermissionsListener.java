package delit.piwigoclient.ui.file.action;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.appcompat.app.AlertDialog;

import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;
import delit.piwigoclient.ui.file.FolderItem;
import delit.piwigoclient.ui.file.FolderItemRecyclerViewAdapter;
import delit.piwigoclient.ui.file.FolderItemViewAdapterPreferences;
import delit.piwigoclient.ui.file.RecyclerViewDocumentFileFolderItemSelectFragment;

public class FileSelectionCancelledMissingPermissionsListener<F extends RecyclerViewDocumentFileFolderItemSelectFragment<F,FUIH,P>,FUIH extends FragmentUIHelper<FUIH,F>,LVA extends FolderItemRecyclerViewAdapter<LVA, P, FolderItem,?,?>, P extends FolderItemViewAdapterPreferences<P>> extends QuestionResultAdapter<FUIH,F> implements Parcelable {

    public FileSelectionCancelledMissingPermissionsListener(FUIH uiHelper) {
        super(uiHelper);
    }

    protected FileSelectionCancelledMissingPermissionsListener(Parcel in) {
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
        getParent().onClickCancelFileSelectionButton();
    }

    public static final Creator<FileSelectionCancelledMissingPermissionsListener<?,?,?,?>> CREATOR = new Creator<FileSelectionCancelledMissingPermissionsListener<?,?,?,?>>() {
        @Override
        public FileSelectionCancelledMissingPermissionsListener<?,?,?,?> createFromParcel(Parcel in) {
            return new FileSelectionCancelledMissingPermissionsListener<>(in);
        }

        @Override
        public FileSelectionCancelledMissingPermissionsListener<?,?,?,?>[] newArray(int size) {
            return new FileSelectionCancelledMissingPermissionsListener[size];
        }
    };
}
