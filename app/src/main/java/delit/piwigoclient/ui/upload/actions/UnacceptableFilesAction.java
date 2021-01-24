package delit.piwigoclient.ui.upload.actions;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import delit.libs.ui.util.ParcelUtils;
import delit.libs.util.IOUtils;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.upload.AbstractUploadFragment;

public class UnacceptableFilesAction<F extends AbstractUploadFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  extends UIHelper.QuestionResultAdapter<FUIH, F> implements Parcelable {
    private final Set<String> unacceptableFileExts;

    public UnacceptableFilesAction(FUIH uiHelper, Set<String> unacceptableFileExts) {
        super(uiHelper);
        this.unacceptableFileExts = unacceptableFileExts;
    }

    protected UnacceptableFilesAction(Parcel in) {
        super(in);
        unacceptableFileExts = ParcelUtils.readStringSet(in);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        ParcelUtils.writeStringSet(dest, unacceptableFileExts);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<UnacceptableFilesAction<?,?>> CREATOR = new Creator<UnacceptableFilesAction<?,?>>() {
        @Override
        public UnacceptableFilesAction<?,?> createFromParcel(Parcel in) {
            return new UnacceptableFilesAction<>(in);
        }

        @Override
        public UnacceptableFilesAction<?,?>[] newArray(int size) {
            return new UnacceptableFilesAction[size];
        }
    };

    @Override
    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
        F fragment = getUiHelper().getParent();

        if (Boolean.TRUE == positiveAnswer) {

            List<Uri> unaccceptableFiles = new ArrayList<Uri>(fragment.getFilesForUploadViewAdapter().getFilesAndSizes().keySet());
            Iterator<Uri> iter = unaccceptableFiles.iterator();
            while (iter.hasNext()) {
                if (!unacceptableFileExts.contains(IOUtils.getFileExt(getContext(), iter.next()).toLowerCase())) {
                    iter.remove();
                }
            }
            for (Uri file : unaccceptableFiles) {
                fragment.onRemove(fragment.getFilesForUploadViewAdapter(), file, false);
            }

            fragment.buildAndSubmitNewUploadJob(false);
        }
    }
}
