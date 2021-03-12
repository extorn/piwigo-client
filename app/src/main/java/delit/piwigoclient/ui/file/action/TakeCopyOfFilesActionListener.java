package delit.piwigoclient.ui.file.action;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.appcompat.app.AlertDialog;

import java.util.List;

import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;
import delit.piwigoclient.ui.file.FolderItem;
import delit.piwigoclient.ui.file.FolderItemViewAdapterPreferences;
import delit.piwigoclient.ui.file.RecyclerViewDocumentFileFolderItemSelectFragment;

public class TakeCopyOfFilesActionListener<F extends RecyclerViewDocumentFileFolderItemSelectFragment<F,FUIH,P>, FUIH extends FragmentUIHelper<FUIH,F>,P extends FolderItemViewAdapterPreferences<P>> extends QuestionResultAdapter<FUIH, F> implements Parcelable {
    private final List<FolderItem> itemsShared;

    public TakeCopyOfFilesActionListener(FUIH uiHelper, List<FolderItem> itemsShared) {
        super(uiHelper);
        this.itemsShared = itemsShared;
    }

    protected TakeCopyOfFilesActionListener(Parcel in) {
        super(in);
        itemsShared = null;// it isn't sensible to retain these items if there isn't persistent permissions
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<TakeCopyOfFilesActionListener<?,?,?>> CREATOR = new Creator<TakeCopyOfFilesActionListener<?,?,?>>() {
        @Override
        public TakeCopyOfFilesActionListener<?,?,?> createFromParcel(Parcel in) {
            return new TakeCopyOfFilesActionListener<>(in);
        }

        @Override
        public TakeCopyOfFilesActionListener<?,?,?>[] newArray(int size) {
            return new TakeCopyOfFilesActionListener[size];
        }
    };

    @Override
    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
        if (Boolean.TRUE == positiveAnswer) {
            getUiHelper().getParent().processOpenDocumentsWithoutPermissions(itemsShared);
        }
    }

}
